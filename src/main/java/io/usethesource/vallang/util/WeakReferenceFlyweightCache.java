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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;


public class WeakReferenceFlyweightCache<K,V> {

	private volatile AtomicReferenceArray<Entry<K,V>> table;
	private volatile int count;
	private final ReferenceQueue<Object> cleared;
	private final StampedLock lock;

	private static final int INITIAL_CAPACITY = 32;
	private static final int MAX_CAPACITY = 1 << 30;

	public WeakReferenceFlyweightCache() {
		table = new AtomicReferenceArray<>(INITIAL_CAPACITY);
		count = 0;
		cleared = new ReferenceQueue<>();
		lock = new StampedLock();
	}
	
	private int bucket(int tableLength,int hash) {
		return (hash ^ (hash >> 16)) & (tableLength - 1);
	}
	
	public V getFlyweight(K key, Function<K, V> generateValue) {
		if (key == null) {
			throw new IllegalArgumentException();
		}
		cleanup();
		int hash = key.hashCode();
		AtomicReferenceArray<Entry<K,V>> table = this.table;
		int bucket = bucket(table.length(), hash);
		Entry<K, V> bucketHead = table.get(bucket); // just so we k
		V found = lookup(key, hash, bucketHead);
		if (found != null) {
			return found;
		}

		// not found
		resize();
		return insertIfPossible(key, hash, bucketHead, generateValue.apply(key));
	}

	private V insertIfPossible(final K key, final int hash, Entry<K, V> notFoundIn, final V result) {
		final Entry<K, V> toInsert = new Entry<>(key, result, hash, cleared);
		while (true) {
			final AtomicReferenceArray<Entry<K, V>> table = this.table;
			int bucket = bucket(table.length(), hash);
			Entry<K, V> currentBucketHead = table.get(bucket);
			if (currentBucketHead != notFoundIn) {
				// the head of the chain has changed, so it might be that now the key is there, so we have to lookup again
				V otherResult = lookup(key, hash, currentBucketHead);
				if (otherResult != null) {
					return otherResult;
				}
				notFoundIn = currentBucketHead;
			}
			toInsert.next.set(currentBucketHead);
			
			long stamp = lock.readLock(); // we get a read lock on the table, so we can put something in it, to protect against a table resize in process
			try {
                if (table == this.table && table.compareAndSet(bucket, currentBucketHead, toInsert)) {
                	count++;
                    return result;
                }
			}
			finally {
				lock.unlockRead(stamp);
			}
		}
	}

	private V lookup(K key, int hash, Entry<K, V> bucketEntry) {
		while (bucketEntry != null) {
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
		WeakWrap<Entry<K,V>> clearedReference = (WeakWrap<Entry<K,V>>) cleared.poll();
		if (clearedReference != null) {
			int totalCleared = 0;
			long stamp = lock.readLock(); // we get a read lock on the table, so we can remove some stuff, to protect against a table resize in process
			try {
				// quickly consumed the whole cleared pool: to avoid too many threads trying to do a cleanup 
				List<WeakWrap<Entry<K,V>>> toClear = new ArrayList<>();
				while (clearedReference != null) {
					toClear.add(clearedReference);
					clearedReference = (WeakWrap<Entry<K,V>>) cleared.poll();
				}

				final AtomicReferenceArray<Entry<K, V>> table = this.table;
				final int currentLength = table.length();
				for (WeakWrap<Entry<K,V>> e: toClear) {
					Entry<K, V> mapNode = e.parent;
					if (mapNode != null) {
                        // avoid multiple threads clearing the same entry (for example the key and value both got cleared)
						synchronized (mapNode) {
							mapNode = e.parent; // make sure it wasn't cleared since we got the lock
							if (mapNode != null) {

								int bucket = bucket(currentLength, mapNode.hash);
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
								e.parent = null;
								mapNode.key.clear();
								mapNode.value.clear();
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

	private void resize() {
		final AtomicReferenceArray<Entry<K, V>> table = this.table;
		int newSize = calculateNewSize(table);

		if (newSize != table.length()) {
			// We have to grow, so we have to lock the table against new inserts.
			long stamp = lock.writeLock();
			try {
				final AtomicReferenceArray<Entry<K, V>> oldTable = this.table;
				final int oldLength = oldTable.length();
				if (oldTable != table) {
					// someone else already changed the table
					newSize = calculateNewSize(oldTable);
					if (newSize == oldLength) {
						return;
					}
				}
				final AtomicReferenceArray<Entry<K,V>> newTable = new AtomicReferenceArray<>(newSize);
				for (int i = 0; i < oldLength; i++) {
					Entry<K,V> current = oldTable.get(i);
					while (current != null) {
						int newBucket = bucket(newSize, current.hash);
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
		else if (newSize != INITIAL_CAPACITY && newCount < (newSize >> 2)) {
			// shrank quite a bit, so it makes sens to resize
			// find the smallest next power for the 
			newSize = Integer.highestOneBit(newCount - 1) << 1;
		}

		if (newSize < 0 || newSize > MAX_CAPACITY) {
			newSize = MAX_CAPACITY;
		}
		else if (newSize == 0) {
			newSize = INITIAL_CAPACITY;
		}
		return newSize;
	}

	private static final class WeakWrap<P> extends WeakReference<Object> {
		private volatile P parent;

		public WeakWrap(Object referent, P parent, ReferenceQueue<? super Object> q) {
			super(referent, q);
			this.parent = parent;
		}
	}

	private static final class Entry<K, V> {
		private final int hash;

		private final AtomicReference<Entry<K,V>> next;

		private final WeakWrap<Entry<K,V>> key;
		private final WeakWrap<Entry<K,V>> value;

		public Entry(K key, V value, int hash, ReferenceQueue<? super Object> q) {
			this.hash = hash;
			this.key = new WeakWrap<>(key, this, q);
			this.value = key == value ? this.key : new WeakWrap<>(value, this, q); // safe a reference in case of identity cache
			this.next = new AtomicReference<>(null);
		}

		public Entry(WeakWrap<Entry<K,V>> key, WeakWrap<Entry<K,V>> value, int hash, Entry<K,V> next) {
			this.hash = hash;
			this.key = key;
			this.value = value;
			this.next = new AtomicReference<>(next);
			key.parent = this;
			value.parent = this;
		}
	}
}