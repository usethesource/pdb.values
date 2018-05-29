/** 
 * Copyright (c) 2017, Davy Landman, SWAT.engineering
 * All rights reserved. 
 *  
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met: 
 *  
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer. 
 *  
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution. 
 *  
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */ 
package io.usethesource.vallang.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;

/**
 * <p>
 *     A cache that stores the either the key or the value (or both) as a weak reference. If either one of the references is cleared (in case of a weak reference), the entry is dropped from the cache.
 *     The cache is thread safe, meaning the {@link #get} method can be called without any locking requirements.
 *     With the following guarantees: 
 * <p>
 * <ul>
 *    <li> As long as there are strong references to both the key and the value, all calls to {@link #get} will return the value reference for something that {@link Object#equals} key (not just reference equality, as Caffeine and WeakHashMap do)
 *    <li> There will only be one entry for a given key, there is no point in time where you can get two different values for the same key (as the entry wasn't cleared because there was no strong reference to the key or the value between two calls)
 *    <li> If the key is in the cache, retrieving the value will not block the code.
 *    <li> If the key is *not* in the cache, the function to generate a value will be called once per concurrent {@link #get} call, and only one of those results will be the reference stored in the cache, and returned to all these concurrent calls.
 *    <li> If the key is *not* in the cache, the {@link #get} rarely blocks, only in case of a resize (which happens at 80% fill rate, and grows exponentially)
 * </ul>
 * 
 * <p>
 *     Warning: only use this class if you want to clear the entry after either the value or the key has been cleared, if you do not want this behavior, use a regular {@link Map}.
 *     Also check the {@link WeakHashMap} that keeps a strong reference to the Value, however it uses reference equality on the key.
 * </p>
 * @author Davy Landman
 *
 * @param <K>
 * @param <V>
 */
public class WeakReferenceCache<K,V> {

	/**
	 * <p>
	 * 		Buckets of the Hash Table, every bucket is the start of a linked list of hash collision nodes.
	 * </p>
	 * 
	 * <p>
	 *		New entries are only added to the head of the bucket, so by comparing the reference at the head of the bucket, you know the chain has not been extended (cleanup can remove intermediate nodes, but that is not a problem for lookup or insertion) 
	 * </p>
	 */
	private volatile AtomicReferenceArray<Entry<K,V>> table = new AtomicReferenceArray<>(MINIMAL_CAPACITY);
	/**
	 * Read write lock around the reference to the table. Read lock when you want to insert stuff into the table, write lock when you want to change the reference of the table (only happens during resize).
	 */
	private final StampedLock lock = new StampedLock();

	/**
	 * Keeps track of how many items are in the table, can temporarily be out of sync with the table.
	 */
	private volatile int count = 0;
	/**
	 * The GC notifies us about references that are cleared via this queue. Polling is cheap (fast and lock-free) if it is empty.
	 * We use Object here so that we can share the queue between key and value.
	 */
	private final ReferenceQueue<Object> cleared = new ReferenceQueue<>();

	private static final int MINIMAL_CAPACITY = 1 << 4;
	private static final int MAX_CAPACITY = 1 << 30;

	/**
	 * Constructor to store the reference to the key
	 */
	private final EntryChildConstructor<Entry<K,V>> keyBuilder;

	/**
	 * Constructor to store the reference to the value
	 */
	private final EntryChildConstructor<Entry<K,V>> valueBuilder;

	public WeakReferenceCache() {
		this(true, true);
	}
	/**
	 * Construct a new WeakReference cache. 
	 * <strong>Passing false for both keys and values turns this into a (memory) expensive hashmap</strong>
	 * @param weakKeys if the keys should be stored as weak references
	 * @param weakValues if the values should be stored as weak references
	 */
	public WeakReferenceCache(boolean weakKeys, boolean weakValues) {
		keyBuilder = weakKeys ? WeakChild::new : StrongChild::new;
		valueBuilder = weakValues ? WeakChild::new : StrongChild::new;
	}
	
	/**
	 * Lookup key in the cache.
	 * @param key lookup something that is equal to this key in the cache.
	 * @param generateValue function that will generate a value when the key is not in the cache. Warning, there is no guarantee that when this function is called, the result of the get method will be the same.
	 * @return the value for this key
	 */
	public V get(K key, Function<K, V> generateValue) {
		if (key == null || generateValue == null) {
			throw new IllegalArgumentException("No null arguments allowed");
		}
		int hash = key.hashCode();
		AtomicReferenceArray<Entry<K,V>> table = this.table; // read the table once, to assure that the next operations all work on the same table
		int bucket = bucket(hash, table.length());
		Entry<K, V> bucketHead = table.get(bucket); 
		V found = lookup(key, hash, bucketHead, null);
		if (found != null) {
			return found;
		}

		// not found, so before trying to insert it, lets cleanup and resize if needed
		cleanup();
		resize();
		return insert(key, hash, bucketHead, generateValue.apply(key));
	}

	private int bucket(int hash, int tableLength) {
		// since we are only using the last bits, take the msb and add them to the mix
		return (hash ^ (hash >> 16)) % tableLength;
	}

	/**
	 * Insert the value if still possible (can be a race between another insert for the same key). The entry that won the race (to insert itself in the hash collision chain) will be returned.
	 * @param key the key entry
	 * @param hash hash of the key
	 * @param notFoundIn head of the hash collision chain where it should be inserted at, used to avoid a double lookup
	 * @param value new value to insert
	 * @return value that is now in the hash table
	 */
	private V insert(final K key, final int hash, Entry<K, V> notFoundIn, final V value) {
		if (value == null) {
			throw new IllegalArgumentException("The new value to insert cannot be null");
		}
		final Entry<K, V> toInsert = new Entry<>(key, value, hash, keyBuilder, valueBuilder, cleared);
		while (true) {
			final AtomicReferenceArray<Entry<K, V>> table = this.table; // we copy the table field once per iteration, the read lock at the end checks if it has changed since then
			int bucket = bucket(hash, table.length());
			Entry<K, V> currentBucketHead = table.get(bucket);
			if (currentBucketHead != notFoundIn) {
				// the head of the chain has changed, so it might be that now the key is there, so we have to lookup again, but we stop when we find the old head again (or null)
				V otherResult = lookup(key, hash, currentBucketHead, notFoundIn);
				if (otherResult != null) {
					// we lost the race
					return otherResult;
				}
				notFoundIn = currentBucketHead;
			}
			toInsert.next.set(currentBucketHead); // we prepare our entry to be at the head of the chain
			
			long stamp = lock.readLock(); // we get a read lock on the table, so we can put something in it, to protect against a table resize in process
			try {
                if (table == this.table && table.compareAndSet(bucket, currentBucketHead, toInsert)) {
                	// if the table didn't change since we found our bucket and our chain
                	// and if the head of the collision chain was still was we expected, we've managed to insert ourselfs in the chain
                	// so we've won any possible races
                	count++;
                    return value;
                }
			}
			finally {
				lock.unlockRead(stamp);
			}
		}
	}

	private V lookup(K key, int hash, Entry<K, V> bucketEntry, Entry<K,V> stopAfter) {
		while (bucketEntry != null && bucketEntry != stopAfter) {
			if (bucketEntry.hash == hash) {
				Object other = bucketEntry.key.get();
				if (other != null && key.equals(other)) {
					@SuppressWarnings("unchecked")
					V result = (V) bucketEntry.value.get();
					if (result != null) {
						return result;
					}
				}
			}
			bucketEntry = bucketEntry.next.get();
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	private void cleanup() {
		WeakChild<Entry<K,V>> clearedReference = (WeakChild<Entry<K, V>>) cleared.poll();
		if (clearedReference != null) {
			int totalCleared = 0;
			long stamp = lock.readLock(); // we get a read lock on the table, so we can remove some stuff, to protect against a table resize in process
			try {
				// quickly consumed the whole cleared pool: to avoid too many threads trying to do a cleanup 
				List<WeakChild<Entry<K,V>>> toClear = new ArrayList<>();
				while (clearedReference != null) {
					toClear.add(clearedReference);
					clearedReference = (WeakChild<Entry<K,V>>) cleared.poll();
				}

				final AtomicReferenceArray<Entry<K, V>> table = this.table;
				final int currentLength = table.length();
				for (WeakChild<Entry<K,V>> e: toClear) {
					Entry<K, V> mapNode = e.getParent();
					if (mapNode != null) {
                        // avoid multiple threads clearing the same entry (for example the key and value both got cleared)
						synchronized (mapNode) {
							mapNode = e.getParent(); // make sure it wasn't cleared since we got the lock
							if (mapNode != null) {

								int bucket = bucket(mapNode.hash, currentLength);
								while (true) {
                                    Entry<K,V> prev = null;
                                    Entry<K,V> cur = table.get(bucket);
                                    while (cur != mapNode) {
                                        prev = cur;
                                        cur = cur.next.get();
                                        assert cur != null; // we have to find entry in this bucket
                                    }
                                    if (prev == null) {
                                        // at the head, so we can just replace the head
                                        if (table.compareAndSet(bucket, mapNode, mapNode.next.get())) {
                                        	break; // we replaced the head, so continue
                                        }
                                    }
                                    else {
                                    	if (prev.next.compareAndSet(mapNode, mapNode.next.get())) {
                                    		break; // managed to replace the next pointer in the chain 
                                    	}
                                    }
								}
								count--;
								totalCleared++;
								// keep the next pointer intact, in case someone is following this chain.
								// we do clear the rest
								mapNode.key.clear();
								mapNode.key.setParent(null); // marks the fact that the node has been cleared already
								mapNode.value.clear();
								mapNode.value.setParent(null);
							}

						}

					}
				}
			}
			finally {
				lock.unlockRead(stamp);
			}
			if (totalCleared > 1024) {
				// let's check for a resize
				resize();
			}
		}
	}

	/**
	 * Try to see if there is enough space in the table, or if it should be grown/shrunk.
	 * Only lock when we need to resize.
	 */
	private void resize() {
		final AtomicReferenceArray<Entry<K, V>> table = this.table;
		int newSize = calculateNewSize(table);

		if (newSize != table.length()) {
			// We have to grow, so we have to get an exclusive lock on the table, so nobody is inserting/cleaning.
			long stamp = lock.writeLock();
			try {
				// now it could be that another thread also triggered a resize, so we have to make sure we are not too late
				final AtomicReferenceArray<Entry<K, V>> oldTable = this.table;
				final int oldLength = oldTable.length();
				if (oldTable != table) {
					// someone else already changed the table, so recalculate the size, see if we still need to resize
					newSize = calculateNewSize(oldTable);
					if (newSize == oldLength) {
						return;
					}
				}
				final AtomicReferenceArray<Entry<K,V>> newTable = new AtomicReferenceArray<>(newSize);
				for (int i = 0; i < oldLength; i++) {
					Entry<K,V> current = oldTable.get(i);
					while (current != null) {
						int newBucket = bucket(current.hash, newSize);
						// we cannot change the old entry, as the lookups are still happening on them (as intended)
						// so we build a new entry, that replaces the old entry for the aspect of the reference queue.
						newTable.set(newBucket, new Entry<>(current.key, current.value, current.hash, newTable.get(newBucket)));
						current = current.next.get();
					}
				}
				this.table = newTable;
			}
			finally {
				lock.unlockWrite(stamp);
			}
		}
	}

	private int calculateNewSize(final AtomicReferenceArray<Entry<K, V>> table) {
		int newSize = table.length();
		int newCount = this.count + 1;
		if (newCount > newSize * 0.8) {
			newSize <<= 1;
		}
		else if (newSize != MINIMAL_CAPACITY && newCount < (newSize >> 2)) {
			// shrank quite a bit, so it makes sens to resize
			// find the smallest next power for the 
			newSize = Integer.highestOneBit(newCount - 1) << 1;
		}

		if (newSize < 0 || newSize > MAX_CAPACITY) {
			newSize = MAX_CAPACITY;
		}
		else if (newSize < MINIMAL_CAPACITY) {
			newSize = MINIMAL_CAPACITY;
		}
		return newSize;
	}

	
	private static interface EntryChild<P> {
		Object get();
		void clear();
		P getParent();
		void setParent(P parent);
	}

	
	@FunctionalInterface
	interface EntryChildConstructor<P> {
		EntryChild<P> construct(Object reference, P parent, ReferenceQueue<? super Object> clearQue);
	}

	
	private static final class WeakChild<P> extends WeakReference<Object> implements EntryChild<P>  {
		private volatile P parent;

		public WeakChild(Object referent, P parent, ReferenceQueue<? super Object> q) {
			super(referent, q);
			this.parent = parent;
		}

		@Override
		public P getParent() {
			return parent;
		}

		@Override
		public void setParent(P parent) {
			this.parent = parent;
		}
	}

	private static final class StrongChild<P> implements EntryChild<P> {
		private volatile Object ref;

		public StrongChild(Object referent, P parent, ReferenceQueue<? super Object> q) {
			this.ref = referent;
		}
		
		@Override
		public Object get() {
			return ref;
		}
		
		@Override
		public void clear() {
			ref = null;
		}
		@Override
		public P getParent() {
			throw new RuntimeException("Should never be called");
		}
		@Override
		public void setParent(P parent) {
			// noop to make the code simpeler
		}
	}
	

	private static final class Entry<K, V> {
		private final int hash;

		private final AtomicReference<Entry<K,V>> next;

		private final EntryChild<Entry<K,V>> key;
		private final EntryChild<Entry<K,V>> value;

		public Entry(K key, V value, int hash, EntryChildConstructor<Entry<K,V>> keyBuilder, EntryChildConstructor<Entry<K,V>> valueBuilder, ReferenceQueue<? super Object> q) {
			this.hash = hash;
			this.key = keyBuilder.construct(key, this, q);
			this.value = (key == value) && (keyBuilder == valueBuilder) ? this.key : valueBuilder.construct(value, this, q); // safe a reference in case of identity cache
			this.next = new AtomicReference<>(null);
		}

		public Entry(EntryChild<Entry<K,V>> key, EntryChild<Entry<K,V>> value, int hash, Entry<K,V> next) {
			this.hash = hash;
			this.key = key;
			this.value = value;
			this.next = new AtomicReference<>(next);
			key.setParent(this);
			value.setParent(this);
		}
	}
}