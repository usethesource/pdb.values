/*******************************************************************************
 * Copyright (c) 2013-2014 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *
 *   * Michael Steindorfer - Michael.Steindorfer@cwi.nl - CWI  
 *******************************************************************************/
package org.eclipse.imp.pdb.facts.util;

import static org.eclipse.imp.pdb.facts.util.AbstractSpecialisedImmutableMap.entryOf;

import java.text.DecimalFormat;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("rawtypes")
public class TrieMap_5Bits<K, V> implements ImmutableMap<K, V> {

	@SuppressWarnings("unchecked")
	private static final TrieMap_5Bits EMPTY_MAP = new TrieMap_5Bits(CompactMapNode.EMPTY_NODE, 0,
					0);

	private static final boolean DEBUG = false;

	private final AbstractMapNode<K, V> rootNode;
	private final int hashCode;
	private final int cachedSize;

	TrieMap_5Bits(AbstractMapNode<K, V> rootNode, int hashCode, int cachedSize) {
		this.rootNode = rootNode;
		this.hashCode = hashCode;
		this.cachedSize = cachedSize;
		if (DEBUG) {
			assert checkHashCodeAndSize(hashCode, cachedSize);
		}
	}

	@SuppressWarnings("unchecked")
	public static final <K, V> ImmutableMap<K, V> of() {
		return TrieMap_5Bits.EMPTY_MAP;
	}

	@SuppressWarnings("unchecked")
	public static final <K, V> ImmutableMap<K, V> of(Object... keyValuePairs) {
		if (keyValuePairs.length % 2 != 0) {
			throw new IllegalArgumentException(
							"Length of argument list is uneven: no key/value pairs.");
		}

		ImmutableMap<K, V> result = TrieMap_5Bits.EMPTY_MAP;

		for (int i = 0; i < keyValuePairs.length; i += 2) {
			final K key = (K) keyValuePairs[i];
			final V val = (V) keyValuePairs[i + 1];

			result = result.__put(key, val);
		}

		return result;
	}

	@SuppressWarnings("unchecked")
	public static final <K, V> TransientMap<K, V> transientOf() {
		return TrieMap_5Bits.EMPTY_MAP.asTransient();
	}

	@SuppressWarnings("unchecked")
	public static final <K, V> TransientMap<K, V> transientOf(Object... keyValuePairs) {
		if (keyValuePairs.length % 2 != 0) {
			throw new IllegalArgumentException(
							"Length of argument list is uneven: no key/value pairs.");
		}

		final TransientMap<K, V> result = TrieMap_5Bits.EMPTY_MAP.asTransient();

		for (int i = 0; i < keyValuePairs.length; i += 2) {
			final K key = (K) keyValuePairs[i];
			final V val = (V) keyValuePairs[i + 1];

			result.__put(key, val);
		}

		return result;
	}

	private boolean checkHashCodeAndSize(final int targetHash, final int targetSize) {
		int hash = 0;
		int size = 0;

		for (Iterator<Map.Entry<K, V>> it = entryIterator(); it.hasNext();) {
			final Map.Entry<K, V> entry = it.next();
			final K key = entry.getKey();
			final V val = entry.getValue();

			hash += key.hashCode() ^ val.hashCode();
			size += 1;
		}

		return hash == targetHash && size == targetSize;
	}

	private static int improve(final int hash) {
		int h = hash + ~(hash << 9);
		h = h ^ (h >>> 14);
		h = h + (h << 4);
		return h ^ (h >>> 10);
	}

	@Override
	public TrieMap_5Bits<K, V> __put(final K key, final V val) {
		final int keyHash = key.hashCode();
		final Result<K, V> details = Result.unchanged();

		final CompactMapNode<K, V> newRootNode = rootNode.updated(null, key, val, improve(keyHash),
						0, details);

		if (details.isModified()) {

			if (details.hasReplacedValue()) {
				final int valHashOld = details.getReplacedValue().hashCode();
				final int valHashNew = val.hashCode();

				return new TrieMap_5Bits<K, V>(newRootNode, hashCode + (keyHash ^ valHashNew)
								- (keyHash ^ valHashOld), cachedSize);
			}

			final int valHash = val.hashCode();
			return new TrieMap_5Bits<K, V>(newRootNode, hashCode + (keyHash ^ valHash),
							cachedSize + 1);

		}

		return this;
	}

	@Override
	public TrieMap_5Bits<K, V> __putEquivalent(final K key, final V val,
					final Comparator<Object> cmp) {
		final int keyHash = key.hashCode();
		final Result<K, V> details = Result.unchanged();

		final CompactMapNode<K, V> newRootNode = rootNode.updated(null, key, val, improve(keyHash),
						0, details, cmp);

		if (details.isModified()) {

			if (details.hasReplacedValue()) {
				final int valHashOld = details.getReplacedValue().hashCode();
				final int valHashNew = val.hashCode();

				return new TrieMap_5Bits<K, V>(newRootNode, hashCode + (keyHash ^ valHashNew)
								- (keyHash ^ valHashOld), cachedSize);
			}

			final int valHash = val.hashCode();
			return new TrieMap_5Bits<K, V>(newRootNode, hashCode + (keyHash ^ valHash),
							cachedSize + 1);

		}

		return this;
	}

	@Override
	public ImmutableMap<K, V> __remove(final K key) {
		final int keyHash = key.hashCode();
		final Result<K, V> details = Result.unchanged();

		final CompactMapNode<K, V> newRootNode = rootNode.removed(null, key, improve(keyHash), 0,
						details);

		if (details.isModified()) {

			assert details.hasReplacedValue();
			final int valHash = details.getReplacedValue().hashCode();

			return new TrieMap_5Bits<K, V>(newRootNode, hashCode - (keyHash ^ valHash),
							cachedSize - 1);

		}

		return this;
	}

	@Override
	public ImmutableMap<K, V> __removeEquivalent(final K key, final Comparator<Object> cmp) {
		final int keyHash = key.hashCode();
		final Result<K, V> details = Result.unchanged();

		final CompactMapNode<K, V> newRootNode = rootNode.removed(null, key, improve(keyHash), 0,
						details, cmp);

		if (details.isModified()) {

			assert details.hasReplacedValue();
			final int valHash = details.getReplacedValue().hashCode();

			return new TrieMap_5Bits<K, V>(newRootNode, hashCode - (keyHash ^ valHash),
							cachedSize - 1);

		}

		return this;
	}

	@Override
	public boolean containsKey(final java.lang.Object o) {
		try {
			@SuppressWarnings("unchecked")
			final K key = (K) o;
			return rootNode.containsKey(key, improve(key.hashCode()), 0);
		} catch (ClassCastException unused) {
			return false;
		}
	}

	@Override
	public boolean containsKeyEquivalent(final java.lang.Object o, final Comparator<Object> cmp) {
		try {
			@SuppressWarnings("unchecked")
			final K key = (K) o;
			return rootNode.containsKey(key, improve(key.hashCode()), 0, cmp);
		} catch (ClassCastException unused) {
			return false;
		}
	}

	@Override
	public boolean containsValue(final java.lang.Object o) {
		for (Iterator<V> iterator = valueIterator(); iterator.hasNext();) {
			if (iterator.next().equals(o)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean containsValueEquivalent(final java.lang.Object o, final Comparator<Object> cmp) {
		for (Iterator<V> iterator = valueIterator(); iterator.hasNext();) {
			if (iterator.next().equals(o)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public V get(final java.lang.Object o) {
		try {
			@SuppressWarnings("unchecked")
			final K key = (K) o;
			final Optional<V> result = rootNode.findByKey(key, improve(key.hashCode()), 0);

			if (result.isPresent()) {
				return result.get();
			} else {
				return null;
			}
		} catch (ClassCastException unused) {
			return null;
		}
	}

	@Override
	public V getEquivalent(final java.lang.Object o, final Comparator<Object> cmp) {
		try {
			@SuppressWarnings("unchecked")
			final K key = (K) o;
			final Optional<V> result = rootNode.findByKey(key, improve(key.hashCode()), 0, cmp);

			if (result.isPresent()) {
				return result.get();
			} else {
				return null;
			}
		} catch (ClassCastException unused) {
			return null;
		}
	}

	@Override
	public ImmutableMap<K, V> __putAll(final Map<? extends K, ? extends V> map) {
		TransientMap<K, V> tmp = asTransient();
		tmp.__putAll(map);
		return tmp.freeze();
	}

	@Override
	public ImmutableMap<K, V> __putAllEquivalent(final Map<? extends K, ? extends V> map,
					final Comparator<Object> cmp) {
		TransientMap<K, V> tmp = asTransient();
		tmp.__putAllEquivalent(map, cmp);
		return tmp.freeze();
	}

	@Override
	public V put(final K key, final V val) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public V remove(final java.lang.Object key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putAll(final Map<? extends K, ? extends V> m) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int size() {
		return cachedSize;
	}

	@Override
	public boolean isEmpty() {
		return cachedSize == 0;
	}

	@Override
	public SupplierIterator<K, V> keyIterator() {
		return new MapKeyIterator<>(rootNode);
	}

	@Override
	public Iterator<V> valueIterator() {
		return new MapValueIterator<>(rootNode);
	}

	@Override
	public Iterator<Map.Entry<K, V>> entryIterator() {
		return new MapEntryIterator<>(rootNode);
	}

	@Override
	public Set<K> keySet() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<V> values() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		Set<java.util.Map.Entry<K, V>> entrySet = null;

		if (entrySet == null) {
			entrySet = new AbstractSet<java.util.Map.Entry<K, V>>() {
				@Override
				public Iterator<java.util.Map.Entry<K, V>> iterator() {
					return new Iterator<Entry<K, V>>() {
						private final Iterator<Entry<K, V>> i = entryIterator();

						@Override
						public boolean hasNext() {
							return i.hasNext();
						}

						@Override
						public Entry<K, V> next() {
							return i.next();
						}

						@Override
						public void remove() {
							i.remove();
						}
					};
				}

				@Override
				public int size() {
					return TrieMap_5Bits.this.size();
				}

				@Override
				public boolean isEmpty() {
					return TrieMap_5Bits.this.isEmpty();
				}

				@Override
				public void clear() {
					TrieMap_5Bits.this.clear();
				}

				@Override
				public boolean contains(Object k) {
					return TrieMap_5Bits.this.containsKey(k);
				}
			};
		}
		return entrySet;
	}

	@Override
	public boolean isTransientSupported() {
		return true;
	}

	@Override
	public TransientMap<K, V> asTransient() {
		return new TransientTrieMap_5Bits<K, V>(this);
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	@Override
	public boolean equals(Object other) {
		if (other == this) {
			return true;
		}
		if (other == null) {
			return false;
		}

		if (other instanceof TrieMap_5Bits) {
			TrieMap_5Bits<?, ?> that = (TrieMap_5Bits<?, ?>) other;

			if (this.size() != that.size()) {
				return false;
			}

			return rootNode.equals(that.rootNode);
		}

		return super.equals(other);
	}

	/*
	 * For analysis purposes only.
	 */
	protected AbstractMapNode<K, V> getRootNode() {
		return rootNode;
	}

	/*
	 * For analysis purposes only.
	 */
	protected Iterator<AbstractMapNode<K, V>> nodeIterator() {
		return new TrieMap_5BitsNodeIterator<>(rootNode);
	}

	/*
	 * For analysis purposes only.
	 */
	protected int getNodeCount() {
		final Iterator<AbstractMapNode<K, V>> it = nodeIterator();
		int sumNodes = 0;

		for (; it.hasNext(); it.next()) {
			sumNodes += 1;
		}

		return sumNodes;
	}

	/*
	 * For analysis purposes only. Payload X Node
	 */
	protected int[][] arityCombinationsHistogram() {
		final Iterator<AbstractMapNode<K, V>> it = nodeIterator();
		final int[][] sumArityCombinations = new int[33][33];

		while (it.hasNext()) {
			final AbstractMapNode<K, V> node = it.next();
			sumArityCombinations[node.payloadArity()][node.nodeArity()] += 1;
		}

		return sumArityCombinations;
	}

	/*
	 * For analysis purposes only.
	 */
	protected int[] arityHistogram() {
		final int[][] sumArityCombinations = arityCombinationsHistogram();
		final int[] sumArity = new int[33];

		final int maxArity = 32; // TODO: factor out constant

		for (int j = 0; j <= maxArity; j++) {
			for (int maxRestArity = maxArity - j, k = 0; k <= maxRestArity - j; k++) {
				sumArity[j + k] += sumArityCombinations[j][k];
			}
		}

		return sumArity;
	}

	/*
	 * For analysis purposes only.
	 */
	public void printStatistics() {
		final int[][] sumArityCombinations = arityCombinationsHistogram();
		final int[] sumArity = arityHistogram();
		final int sumNodes = getNodeCount();

		final int[] cumsumArity = new int[33];
		for (int cumsum = 0, i = 0; i < 33; i++) {
			cumsum += sumArity[i];
			cumsumArity[i] = cumsum;
		}

		final float threshhold = 0.01f; // for printing results
		for (int i = 0; i < 33; i++) {
			float arityPercentage = (float) (sumArity[i]) / sumNodes;
			float cumsumArityPercentage = (float) (cumsumArity[i]) / sumNodes;

			if (arityPercentage != 0 && arityPercentage >= threshhold) {
				// details per level
				StringBuilder bldr = new StringBuilder();
				int max = i;
				for (int j = 0; j <= max; j++) {
					for (int k = max - j; k <= max - j; k++) {
						float arityCombinationsPercentage = (float) (sumArityCombinations[j][k])
										/ sumNodes;

						if (arityCombinationsPercentage != 0
										&& arityCombinationsPercentage >= threshhold) {
							bldr.append(String.format("%d/%d: %s, ", j, k, new DecimalFormat(
											"0.00%").format(arityCombinationsPercentage)));
						}
					}
				}
				final String detailPercentages = bldr.toString();

				// overview
				System.out.println(String.format("%2d: %s\t[cumsum = %s]\t%s", i,
								new DecimalFormat("0.00%").format(arityPercentage),
								new DecimalFormat("0.00%").format(cumsumArityPercentage),
								detailPercentages));
			}
		}
	}

	abstract static class Optional<T> {
		private static final Optional EMPTY = new Optional() {
			@Override
			boolean isPresent() {
				return false;
			}

			@Override
			Object get() {
				return null;
			}
		};

		@SuppressWarnings("unchecked")
		static <T> Optional<T> empty() {
			return EMPTY;
		}

		static <T> Optional<T> of(T value) {
			return new Value<T>(value);
		}

		abstract boolean isPresent();

		abstract T get();

		private static final class Value<T> extends Optional<T> {
			private final T value;

			private Value(T value) {
				this.value = value;
			}

			@Override
			boolean isPresent() {
				return true;
			}

			@Override
			T get() {
				return value;
			}
		}
	}

	static final class Result<K, V> {
		private V replacedValue;
		private boolean isModified;
		private boolean isReplaced;

		// update: inserted/removed single element, element count changed
		public void modified() {
			this.isModified = true;
		}

		public void updated(V replacedValue) {
			this.replacedValue = replacedValue;
			this.isModified = true;
			this.isReplaced = true;
		}

		// update: neither element, nor element count changed
		public static <K, V> Result<K, V> unchanged() {
			return new Result<>();
		}

		private Result() {
		}

		public boolean isModified() {
			return isModified;
		}

		public boolean hasReplacedValue() {
			return isReplaced;
		}

		public V getReplacedValue() {
			return replacedValue;
		}
	}

	protected static interface INode<K, V> {
	}

	protected static abstract class AbstractMapNode<K, V> implements INode<K, V> {

		static final int TUPLE_LENGTH = 2;

		abstract boolean containsKey(final K key, final int keyHash, final int shift);

		abstract boolean containsKey(final K key, final int keyHash, final int shift,
						final Comparator<Object> cmp);

		abstract Optional<V> findByKey(final K key, final int keyHash, final int shift);

		abstract Optional<V> findByKey(final K key, final int keyHash, final int shift,
						final Comparator<Object> cmp);

		abstract CompactMapNode<K, V> updated(final AtomicReference<Thread> mutator, final K key,
						final V val, final int keyHash, final int shift, final Result<K, V> details);

		abstract CompactMapNode<K, V> updated(final AtomicReference<Thread> mutator, final K key,
						final V val, final int keyHash, final int shift,
						final Result<K, V> details, final Comparator<Object> cmp);

		abstract CompactMapNode<K, V> removed(final AtomicReference<Thread> mutator, final K key,
						final int keyHash, final int shift, final Result<K, V> details);

		abstract CompactMapNode<K, V> removed(final AtomicReference<Thread> mutator, final K key,
						final int keyHash, final int shift, final Result<K, V> details,
						final Comparator<Object> cmp);

		static final boolean isAllowedToEdit(AtomicReference<Thread> x, AtomicReference<Thread> y) {
			return x != null && y != null && (x == y || x.get() == y.get());
		}

		abstract AbstractMapNode<K, V> getNode(final int index);

		abstract boolean hasNodes();

		abstract int nodeArity();

		@Deprecated
		Iterator<? extends AbstractMapNode<K, V>> nodeIterator() {
			return new Iterator<AbstractMapNode<K, V>>() {

				int nextIndex = 0;

				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}

				@Override
				public AbstractMapNode<K, V> next() {
					if (!hasNext())
						throw new NoSuchElementException();
					return AbstractMapNode.this.getNode(nextIndex++);
				}

				@Override
				public boolean hasNext() {
					return nextIndex < AbstractMapNode.this.nodeArity();
				}
			};
		}

		abstract K getKey(final int index);

		abstract V getValue(final int index);

		abstract java.util.Map.Entry<K, V> getKeyValueEntry(final int index);

		abstract boolean hasPayload();

		abstract int payloadArity();

		@Deprecated
		/**
		 * The arity of this trie node (i.e. number of values and nodes stored
		 * on this level).
		 * 
		 * @return sum of nodes and values stored within
		 */
		int arity() {
			return payloadArity() + nodeArity();
		}

		int size() {
			final SupplierIterator<K, V> it = new MapKeyIterator<>(this);

			int size = 0;
			while (it.hasNext()) {
				size += 1;
				it.next();
			}

			return size;
		}

	}

	private static abstract class CompactMapNode<K, V> extends AbstractMapNode<K, V> {

		static final int BIT_PARTITION_SIZE = 5;
		static final int BIT_PARTITION_MASK = 0b11111;

		static final int mask(final int keyHash, final int shift) {
			if (shift == 30) {
				return keyHash & BIT_PARTITION_MASK;
			} else {
				return (keyHash >>> (27 - shift)) & BIT_PARTITION_MASK;
			}
		}

		static final int bitpos(final int mask) {
			return 1 << mask;
		}

		abstract int nodeMap();

		abstract int dataMap();

		static final byte SIZE_EMPTY = 0b00;
		static final byte SIZE_ONE = 0b01;
		static final byte SIZE_MORE_THAN_ONE = 0b10;

		/**
		 * Abstract predicate over a node's size. Value can be either
		 * {@value #SIZE_EMPTY}, {@value #SIZE_ONE}, or
		 * {@value #SIZE_MORE_THAN_ONE}.
		 * 
		 * @return size predicate
		 */
		abstract byte sizePredicate();

		@Override
		abstract CompactMapNode<K, V> getNode(final int index);

		boolean nodeInvariant() {
			boolean inv1 = (size() - payloadArity() >= 2 * (arity() - payloadArity()));
			boolean inv2 = (this.arity() == 0) ? sizePredicate() == SIZE_EMPTY : true;
			boolean inv3 = (this.arity() == 1 && payloadArity() == 1) ? sizePredicate() == SIZE_ONE
							: true;
			boolean inv4 = (this.arity() >= 2) ? sizePredicate() == SIZE_MORE_THAN_ONE : true;

			boolean inv5 = (this.nodeArity() >= 0) && (this.payloadArity() >= 0)
							&& ((this.payloadArity() + this.nodeArity()) == this.arity());

			return inv1 && inv2 && inv3 && inv4 && inv5;
		}

		abstract CompactMapNode<K, V> copyAndSetValue(AtomicReference<Thread> mutator,
						final int bitpos, final V val);

		abstract CompactMapNode<K, V> copyAndInsertValue(AtomicReference<Thread> mutator,
						final int bitpos, final K key, final V val);

		abstract CompactMapNode<K, V> copyAndRemoveValue(AtomicReference<Thread> mutator,
						final int bitpos);

		abstract CompactMapNode<K, V> copyAndSetNode(AtomicReference<Thread> mutator,
						final int bitpos, CompactMapNode<K, V> node);

		abstract CompactMapNode<K, V> copyAndMigrateFromInlineToNode(
						final AtomicReference<Thread> mutator, final int bitpos,
						final CompactMapNode<K, V> node);

		abstract CompactMapNode<K, V> copyAndMigrateFromNodeToInline(
						final AtomicReference<Thread> mutator, final int bitpos,
						final CompactMapNode<K, V> node);

		/*
		 * TODO: specialize removed(..) to remove this method from this
		 * interface
		 */

		@SuppressWarnings("unchecked")
		static final <K, V> CompactMapNode<K, V> mergeTwoKeyValPairs(final K key0, final V val0,
						final int keyHash0, final K key1, final V val1, final int keyHash1,
						final int shift) {
			assert !(key0.equals(key1));

			if (keyHash0 == keyHash1) {
				return new HashCollisionMapNode_5Bits<>(keyHash0,
								(K[]) new Object[] { key0, key1 },
								(V[]) new Object[] { val0, val1 });
			}

			final int mask0 = mask(keyHash0, shift);
			final int mask1 = mask(keyHash1, shift);

			if (mask0 != mask1) {
				// both nodes fit on same level
				final int dataMap = (int) (bitpos(mask0) | bitpos(mask1));

				if (mask0 < mask1) {
					return nodeOf(null, (int) (0), dataMap, new Object[] { key0, val0, key1, val1 });
				} else {
					return nodeOf(null, (int) (0), dataMap, new Object[] { key1, val1, key0, val0 });
				}
			} else {
				final CompactMapNode<K, V> node = mergeTwoKeyValPairs(key0, val0, keyHash0, key1,
								val1, keyHash1, shift + BIT_PARTITION_SIZE);
				// values fit on next level

				final int nodeMap = bitpos(mask0);
				return nodeOf(null, nodeMap, (int) (0), new Object[] { node });
			}
		}

		static final <K, V> CompactMapNode<K, V> mergeNodeAndKeyValPair(
						final CompactMapNode<K, V> node0, final int keyHash0, final K key1,
						final V val1, final int keyHash1, final int shift) {
			final int mask0 = mask(keyHash0, shift);
			final int mask1 = mask(keyHash1, shift);

			if (mask0 != mask1) {
				// both nodes fit on same level
				final int nodeMap = bitpos(mask0);
				final int dataMap = bitpos(mask1);

				// store values before node
				return nodeOf(null, nodeMap, dataMap, new Object[] { key1, val1, node0 });
			} else {
				// values fit on next level
				final CompactMapNode<K, V> node = mergeNodeAndKeyValPair(node0, keyHash0, key1,
								val1, keyHash1, shift + BIT_PARTITION_SIZE);

				final int nodeMap = bitpos(mask0);
				return nodeOf(null, nodeMap, (int) (0), new Object[] { node });
			}
		}

		static final CompactMapNode EMPTY_NODE;

		static {

			EMPTY_NODE = new BitmapIndexedMapNode<>(null, (int) (0), (int) (0), new Object[] {});

		};

		static final <K, V> CompactMapNode<K, V> nodeOf(final AtomicReference<Thread> mutator,
						final int nodeMap, final int dataMap, final java.lang.Object[] nodes) {
			return new BitmapIndexedMapNode<>(mutator, nodeMap, dataMap, nodes);
		}

		@SuppressWarnings("unchecked")
		static final <K, V> CompactMapNode<K, V> nodeOf(AtomicReference<Thread> mutator) {
			return EMPTY_NODE;
		}

		static final <K, V> CompactMapNode<K, V> nodeOf(AtomicReference<Thread> mutator,
						final int nodeMap, final int dataMap, final K key, final V val) {
			assert nodeMap == 0;
			return nodeOf(mutator, (int) (0), dataMap, new Object[] { key, val });
		}

		int dataIndex(final int bitpos) {
			return java.lang.Integer.bitCount(dataMap() & (bitpos - 1));
		}

		int nodeIndex(final int bitpos) {
			return java.lang.Integer.bitCount(nodeMap() & (bitpos - 1));
		}

		K keyAt(final int bitpos) {
			return getKey(dataIndex(bitpos));
		}

		V valAt(final int bitpos) {
			return getValue(dataIndex(bitpos));
		}

		CompactMapNode<K, V> nodeAt(final int bitpos) {
			return getNode(nodeIndex(bitpos));
		}

		@Override
		boolean containsKey(final K key, final int keyHash, final int shift) {
			final int mask = mask(keyHash, shift);
			final int bitpos = bitpos(mask);

			if ((dataMap() & bitpos) != 0) {
				return keyAt(bitpos).equals(key);
			}

			if ((nodeMap() & bitpos) != 0) {
				return nodeAt(bitpos).containsKey(key, keyHash, shift + BIT_PARTITION_SIZE);
			}

			return false;
		}

		@Override
		boolean containsKey(final K key, final int keyHash, final int shift,
						final Comparator<Object> cmp) {
			final int mask = mask(keyHash, shift);
			final int bitpos = bitpos(mask);

			if ((dataMap() & bitpos) != 0) {
				return cmp.compare(keyAt(bitpos), key) == 0;
			}

			if ((nodeMap() & bitpos) != 0) {
				return nodeAt(bitpos).containsKey(key, keyHash, shift + BIT_PARTITION_SIZE, cmp);
			}

			return false;
		}

		@Override
		Optional<V> findByKey(final K key, final int keyHash, final int shift) {
			final int mask = mask(keyHash, shift);
			final int bitpos = bitpos(mask);

			if ((dataMap() & bitpos) != 0) { // inplace value
				if (keyAt(bitpos).equals(key)) {
					final V _val = valAt(bitpos);

					return Optional.of(_val);
				}

				return Optional.empty();
			}

			if ((nodeMap() & bitpos) != 0) { // node (not value)
				final AbstractMapNode<K, V> subNode = nodeAt(bitpos);

				return subNode.findByKey(key, keyHash, shift + BIT_PARTITION_SIZE);
			}

			return Optional.empty();
		}

		@Override
		Optional<V> findByKey(final K key, final int keyHash, final int shift,
						final Comparator<Object> cmp) {
			final int mask = mask(keyHash, shift);
			final int bitpos = bitpos(mask);

			if ((dataMap() & bitpos) != 0) { // inplace value
				if (cmp.compare(keyAt(bitpos), key) == 0) {
					final V _val = valAt(bitpos);

					return Optional.of(_val);
				}

				return Optional.empty();
			}

			if ((nodeMap() & bitpos) != 0) { // node (not value)
				final AbstractMapNode<K, V> subNode = nodeAt(bitpos);

				return subNode.findByKey(key, keyHash, shift + BIT_PARTITION_SIZE, cmp);
			}

			return Optional.empty();
		}

		@Override
		CompactMapNode<K, V> updated(final AtomicReference<Thread> mutator, final K key,
						final V val, final int keyHash, final int shift, final Result<K, V> details) {
			final int mask = mask(keyHash, shift);
			final int bitpos = bitpos(mask);

			if ((dataMap() & bitpos) != 0) { // inplace value
				final int dataIndex = dataIndex(bitpos);
				final K currentKey = getKey(dataIndex);

				if (currentKey.equals(key)) {
					final V currentVal = getValue(dataIndex);

					if (currentVal.equals(val)) {
						return this;
					} else {
						// update mapping
						details.updated(currentVal);
						return copyAndSetValue(mutator, bitpos, val);
					}
				} else {
					final V currentVal = getValue(dataIndex);
					final CompactMapNode<K, V> subNodeNew = mergeTwoKeyValPairs(currentKey,
									currentVal, improve(currentKey.hashCode()), key, val, keyHash,
									shift + BIT_PARTITION_SIZE);

					details.modified();
					return copyAndMigrateFromInlineToNode(mutator, bitpos, subNodeNew);

				}
			} else if ((nodeMap() & bitpos) != 0) { // node (not value)
				final CompactMapNode<K, V> subNode = nodeAt(bitpos);
				final CompactMapNode<K, V> subNodeNew = subNode.updated(mutator, key, val, keyHash,
								shift + BIT_PARTITION_SIZE, details);

				if (details.isModified()) {
					return copyAndSetNode(mutator, bitpos, subNodeNew);
				} else {
					return this;
				}
			} else {
				// no value
				details.modified();
				return copyAndInsertValue(mutator, bitpos, key, val);
			}
		}

		@Override
		CompactMapNode<K, V> updated(final AtomicReference<Thread> mutator, final K key,
						final V val, final int keyHash, final int shift,
						final Result<K, V> details, final Comparator<Object> cmp) {
			final int mask = mask(keyHash, shift);
			final int bitpos = bitpos(mask);

			if ((dataMap() & bitpos) != 0) { // inplace value
				final int dataIndex = dataIndex(bitpos);
				final K currentKey = getKey(dataIndex);

				if (cmp.compare(currentKey, key) == 0) {
					final V currentVal = getValue(dataIndex);

					if (cmp.compare(currentVal, val) == 0) {
						return this;
					} else {
						// update mapping
						details.updated(currentVal);
						return copyAndSetValue(mutator, bitpos, val);
					}
				} else {
					final V currentVal = getValue(dataIndex);
					final CompactMapNode<K, V> subNodeNew = mergeTwoKeyValPairs(currentKey,
									currentVal, improve(currentKey.hashCode()), key, val, keyHash,
									shift + BIT_PARTITION_SIZE);

					details.modified();
					return copyAndMigrateFromInlineToNode(mutator, bitpos, subNodeNew);

				}
			} else if ((nodeMap() & bitpos) != 0) { // node (not value)
				final CompactMapNode<K, V> subNode = nodeAt(bitpos);
				final CompactMapNode<K, V> subNodeNew = subNode.updated(mutator, key, val, keyHash,
								shift + BIT_PARTITION_SIZE, details, cmp);

				if (details.isModified()) {
					return copyAndSetNode(mutator, bitpos, subNodeNew);
				} else {
					return this;
				}
			} else {
				// no value
				details.modified();
				return copyAndInsertValue(mutator, bitpos, key, val);
			}
		}

		@Override
		CompactMapNode<K, V> removed(final AtomicReference<Thread> mutator, final K key,
						final int keyHash, final int shift, final Result<K, V> details) {
			final int mask = mask(keyHash, shift);
			final int bitpos = bitpos(mask);

			if ((dataMap() & bitpos) != 0) { // inplace value
				final int dataIndex = dataIndex(bitpos);

				if (getKey(dataIndex).equals(key)) {
					final V currentVal = getValue(dataIndex);
					details.updated(currentVal);

					if (this.payloadArity() == 2 && this.nodeArity() == 0) {
						/*
						 * Create new node with remaining pair. The new node
						 * will a) either become the new root returned, or b)
						 * unwrapped and inlined during returning.
						 */
						final int newDataMap = (shift == 0) ? (int) (dataMap() ^ bitpos)
										: bitpos(mask(keyHash, 0));

						if (dataIndex == 0) {
							return CompactMapNode.<K, V> nodeOf(mutator, (int) 0, newDataMap,
											getKey(1), getValue(1));
						} else {
							return CompactMapNode.<K, V> nodeOf(mutator, (int) 0, newDataMap,
											getKey(0), getValue(0));
						}
					} else {
						return copyAndRemoveValue(mutator, bitpos);
					}
				} else {
					return this;
				}
			} else if ((nodeMap() & bitpos) != 0) { // node (not value)
				final CompactMapNode<K, V> subNode = nodeAt(bitpos);
				final CompactMapNode<K, V> subNodeNew = subNode.removed(mutator, key, keyHash,
								shift + BIT_PARTITION_SIZE, details);

				if (!details.isModified()) {
					return this;
				}

				switch (subNodeNew.sizePredicate()) {
				case 0: {
					throw new IllegalStateException("Sub-node must have at least one element.");
				}
				case 1: {
					if (this.payloadArity() == 0 && this.nodeArity() == 1) {
						// escalate (singleton or empty) result
						return subNodeNew;
					} else {
						// inline value (move to front)
						return copyAndMigrateFromNodeToInline(mutator, bitpos, subNodeNew);
					}
				}
				default: {
					// modify current node (set replacement node)
					return copyAndSetNode(mutator, bitpos, subNodeNew);
				}
				}
			}

			return this;
		}

		@Override
		CompactMapNode<K, V> removed(final AtomicReference<Thread> mutator, final K key,
						final int keyHash, final int shift, final Result<K, V> details,
						final Comparator<Object> cmp) {
			final int mask = mask(keyHash, shift);
			final int bitpos = bitpos(mask);

			if ((dataMap() & bitpos) != 0) { // inplace value
				final int dataIndex = dataIndex(bitpos);

				if (cmp.compare(getKey(dataIndex), key) == 0) {
					final V currentVal = getValue(dataIndex);
					details.updated(currentVal);

					if (this.payloadArity() == 2 && this.nodeArity() == 0) {
						/*
						 * Create new node with remaining pair. The new node
						 * will a) either become the new root returned, or b)
						 * unwrapped and inlined during returning.
						 */
						final int newDataMap = (shift == 0) ? (int) (dataMap() ^ bitpos)
										: bitpos(mask(keyHash, 0));

						if (dataIndex == 0) {
							return CompactMapNode.<K, V> nodeOf(mutator, (int) 0, newDataMap,
											getKey(1), getValue(1));
						} else {
							return CompactMapNode.<K, V> nodeOf(mutator, (int) 0, newDataMap,
											getKey(0), getValue(0));
						}
					} else {
						return copyAndRemoveValue(mutator, bitpos);
					}
				} else {
					return this;
				}
			} else if ((nodeMap() & bitpos) != 0) { // node (not value)
				final CompactMapNode<K, V> subNode = nodeAt(bitpos);
				final CompactMapNode<K, V> subNodeNew = subNode.removed(mutator, key, keyHash,
								shift + BIT_PARTITION_SIZE, details, cmp);

				if (!details.isModified()) {
					return this;
				}

				switch (subNodeNew.sizePredicate()) {
				case 0: {
					throw new IllegalStateException("Sub-node must have at least one element.");
				}
				case 1: {
					if (this.payloadArity() == 0 && this.nodeArity() == 1) {
						// escalate (singleton or empty) result
						return subNodeNew;
					} else {
						// inline value (move to front)
						return copyAndMigrateFromNodeToInline(mutator, bitpos, subNodeNew);
					}
				}
				default: {
					// modify current node (set replacement node)
					return copyAndSetNode(mutator, bitpos, subNodeNew);
				}
				}
			}

			return this;
		}

		/**
		 * @return 0 <= mask <= 2^BIT_PARTITION_SIZE - 1
		 */
		static byte recoverMask(int map, byte i_th) {
			assert 1 <= i_th && i_th <= 32;

			byte cnt1 = 0;
			byte mask = 0;

			while (mask < 32) {
				if ((map & 0x01) == 0x01) {
					cnt1 += 1;

					if (cnt1 == i_th) {
						return mask;
					}
				}

				map = (int) (map >> 1);
				mask += 1;
			}

			assert cnt1 != i_th;
			throw new RuntimeException("Called with invalid arguments.");
		}

		@Override
		public String toString() {
			final StringBuilder bldr = new StringBuilder();
			bldr.append('[');

			for (byte i = 0; i < payloadArity(); i++) {
				final byte pos = recoverMask(dataMap(), (byte) (i + 1));
				bldr.append(String.format("@%d: %s", pos, getKey(i), getValue(i)));

				if (!((i + 1) == payloadArity())) {
					bldr.append(", ");
				}
			}

			if (payloadArity() > 0 && nodeArity() > 0) {
				bldr.append(", ");
			}

			for (byte i = 0; i < nodeArity(); i++) {
				final byte pos = recoverMask(nodeMap(), (byte) (i + 1));
				bldr.append(String.format("@%d: %s", pos, getNode(i)));

				if (!((i + 1) == nodeArity())) {
					bldr.append(", ");
				}
			}

			bldr.append(']');
			return bldr.toString();
		}

	}

	private static abstract class CompactMixedMapNode<K, V> extends CompactMapNode<K, V> {

		private final int nodeMap;
		private final int dataMap;

		CompactMixedMapNode(final AtomicReference<Thread> mutator, final int nodeMap,
						final int dataMap) {
			this.nodeMap = nodeMap;
			this.dataMap = dataMap;
		}

		@Override
		public int nodeMap() {
			return nodeMap;
		}

		@Override
		public int dataMap() {
			return dataMap;
		}

	}

	private static final class BitmapIndexedMapNode<K, V> extends CompactMixedMapNode<K, V> {

		final AtomicReference<Thread> mutator;
		final java.lang.Object[] nodes;

		private BitmapIndexedMapNode(final AtomicReference<Thread> mutator, final int nodeMap,
						final int dataMap, final java.lang.Object[] nodes) {
			super(mutator, nodeMap, dataMap);

			this.mutator = mutator;
			this.nodes = nodes;

			// if (DEBUG) {
			//
			// assert (TUPLE_LENGTH * java.lang.Integer.bitCount(dataMap)
			// + java.lang.Integer.bitCount(nodeMap) == nodes.length);
			//
			// for (int i = 0; i < TUPLE_LENGTH * payloadArity(); i++) {
			// assert ((nodes[i] instanceof CompactMapNode) == false);
			// }
			// for (int i = TUPLE_LENGTH * payloadArity(); i < nodes.length;
			// i++) {
			// assert ((nodes[i] instanceof CompactMapNode) == true);
			// }
			// }
			//
			// assert nodeInvariant();
		}

		@Override
		boolean containsKey(final K key, final int keyHash, final int shift) {
			final int bitpos = bitpos(mask(keyHash, shift));

			if ((dataMap() & bitpos) != 0) {
				return nodes[TUPLE_LENGTH * dataIndex(bitpos)].equals(key);
			}

			if ((nodeMap() & bitpos) != 0) {
				return getNode(nodeIndex(bitpos)).containsKey(key, keyHash,
								shift + BIT_PARTITION_SIZE);
			}

			return false;
		}

		@SuppressWarnings("unchecked")
		@Override
		K getKey(final int index) {
			return (K) nodes[TUPLE_LENGTH * index];
		}

		@SuppressWarnings("unchecked")
		@Override
		V getValue(final int index) {
			return (V) nodes[TUPLE_LENGTH * index + 1];
		}

		@SuppressWarnings("unchecked")
		@Override
		java.util.Map.Entry<K, V> getKeyValueEntry(final int index) {
			return entryOf((K) nodes[TUPLE_LENGTH * index], (V) nodes[TUPLE_LENGTH * index + 1]);
		}

		@SuppressWarnings("unchecked")
		@Override
		CompactMapNode<K, V> getNode(final int index) {
			return (CompactMapNode<K, V>) nodes[nodes.length - 1 - index];
		}

		@Override
		boolean hasPayload() {
			return dataMap() != 0;
		}

		@Override
		int payloadArity() {
			return java.lang.Integer.bitCount(dataMap());
		}

		@Override
		boolean hasNodes() {
			return nodeMap() != 0;
		}

		@Override
		int nodeArity() {
			return java.lang.Integer.bitCount(nodeMap());
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 0;
			result = prime * result + ((int) dataMap());
			result = prime * result + ((int) dataMap());
			result = prime * result + Arrays.hashCode(nodes);
			return result;
		}

		@Override
		public boolean equals(final java.lang.Object other) {
			if (null == other) {
				return false;
			}
			if (this == other) {
				return true;
			}
			if (getClass() != other.getClass()) {
				return false;
			}
			BitmapIndexedMapNode<?, ?> that = (BitmapIndexedMapNode<?, ?>) other;
			if (nodeMap() != that.nodeMap()) {
				return false;
			}
			if (dataMap() != that.dataMap()) {
				return false;
			}
			if (!Arrays.equals(nodes, that.nodes)) {
				return false;
			}
			return true;
		}

		@Override
		byte sizePredicate() {
			if (this.nodeArity() == 0 && this.payloadArity() == 0) {
				return SIZE_EMPTY;
			} else if (this.nodeArity() == 0 && this.payloadArity() == 1) {
				return SIZE_ONE;
			} else {
				return SIZE_MORE_THAN_ONE;
			}
		}

		@Override
		CompactMapNode<K, V> copyAndSetValue(final AtomicReference<Thread> mutator,
						final int bitpos, final V val) {
			final int idx = TUPLE_LENGTH * dataIndex(bitpos) + 1;

			if (isAllowedToEdit(this.mutator, mutator)) {
				// no copying if already editable
				this.nodes[idx] = val;
				return this;
			} else {
				final java.lang.Object[] src = this.nodes;
				final java.lang.Object[] dst = (java.lang.Object[]) new Object[src.length];

				// copy 'src' and set 1 element(s) at position 'idx'
				System.arraycopy(src, 0, dst, 0, src.length);
				dst[idx + 0] = val;

				return nodeOf(mutator, nodeMap(), dataMap(), dst);
			}
		}

		@Override
		CompactMapNode<K, V> copyAndSetNode(final AtomicReference<Thread> mutator,
						final int bitpos, final CompactMapNode<K, V> node) {

			final int idx = this.nodes.length - 1 - nodeIndex(bitpos);

			if (isAllowedToEdit(this.mutator, mutator)) {
				// no copying if already editable
				this.nodes[idx] = node;
				return this;
			} else {
				final java.lang.Object[] src = this.nodes;
				final java.lang.Object[] dst = (java.lang.Object[]) new Object[src.length];

				// copy 'src' and set 1 element(s) at position 'idx'
				System.arraycopy(src, 0, dst, 0, src.length);
				dst[idx + 0] = node;

				return nodeOf(mutator, nodeMap(), dataMap(), dst);
			}
		}

		@Override
		CompactMapNode<K, V> copyAndInsertValue(final AtomicReference<Thread> mutator,
						final int bitpos, final K key, final V val) {
			final int idx = TUPLE_LENGTH * dataIndex(bitpos);

			final java.lang.Object[] src = this.nodes;
			final java.lang.Object[] dst = (java.lang.Object[]) new Object[src.length + 2];

			// copy 'src' and insert 2 element(s) at position 'idx'
			System.arraycopy(src, 0, dst, 0, idx);
			dst[idx + 0] = key;
			dst[idx + 1] = val;
			System.arraycopy(src, idx, dst, idx + 2, src.length - idx);

			return nodeOf(mutator, nodeMap(), (int) (dataMap() | bitpos), dst);
		}

		@Override
		CompactMapNode<K, V> copyAndRemoveValue(final AtomicReference<Thread> mutator,
						final int bitpos) {
			final int idx = TUPLE_LENGTH * dataIndex(bitpos);

			final java.lang.Object[] src = this.nodes;
			final java.lang.Object[] dst = (java.lang.Object[]) new Object[src.length - 2];

			// copy 'src' and remove 2 element(s) at position 'idx'
			System.arraycopy(src, 0, dst, 0, idx);
			System.arraycopy(src, idx + 2, dst, idx, src.length - idx - 2);

			return nodeOf(mutator, nodeMap(), (int) (dataMap() ^ bitpos), dst);
		}

		@Override
		CompactMapNode<K, V> copyAndMigrateFromInlineToNode(final AtomicReference<Thread> mutator,
						final int bitpos, final CompactMapNode<K, V> node) {

			final int idxOld = TUPLE_LENGTH * dataIndex(bitpos);
			final int idxNew = this.nodes.length - TUPLE_LENGTH - nodeIndex(bitpos);

			final java.lang.Object[] src = this.nodes;
			final java.lang.Object[] dst = new Object[src.length - 2 + 1];

			// copy 'src' and remove 2 element(s) at position 'idxOld' and
			// insert 1 element(s) at position 'idxNew' (TODO: carefully test)
			assert idxOld <= idxNew;
			System.arraycopy(src, 0, dst, 0, idxOld);
			System.arraycopy(src, idxOld + 2, dst, idxOld, idxNew - idxOld);
			dst[idxNew + 0] = node;
			System.arraycopy(src, idxNew + 2, dst, idxNew + 1, src.length - idxNew - 2);

			return nodeOf(mutator, (int) (nodeMap() | bitpos), (int) (dataMap() ^ bitpos), dst);
		}

		@Override
		CompactMapNode<K, V> copyAndMigrateFromNodeToInline(final AtomicReference<Thread> mutator,
						final int bitpos, final CompactMapNode<K, V> node) {

			final int idxOld = this.nodes.length - 1 - nodeIndex(bitpos);
			final int idxNew = dataIndex(bitpos);

			final java.lang.Object[] src = this.nodes;
			final java.lang.Object[] dst = new Object[src.length - 1 + 2];

			// copy 'src' and remove 1 element(s) at position 'idxOld' and
			// insert 2 element(s) at position 'idxNew' (TODO: carefully test)
			assert idxOld >= idxNew;
			System.arraycopy(src, 0, dst, 0, idxNew);
			dst[idxNew + 0] = node.getKey(0);
			dst[idxNew + 1] = node.getValue(0);
			System.arraycopy(src, idxNew, dst, idxNew + 2, idxOld - idxNew);
			System.arraycopy(src, idxOld + 1, dst, idxOld + 2, src.length - idxOld - 1);

			return nodeOf(mutator, (int) (nodeMap() ^ bitpos), (int) (dataMap() | bitpos), dst);
		}

	}

	private static final class HashCollisionMapNode_5Bits<K, V> extends CompactMapNode<K, V> {
		private final K[] keys;
		private final V[] vals;
		private final int hash;

		HashCollisionMapNode_5Bits(final int hash, final K[] keys, final V[] vals) {
			this.keys = keys;
			this.vals = vals;
			this.hash = hash;

			assert payloadArity() >= 2;
		}

		@Override
		boolean containsKey(final K key, final int keyHash, final int shift) {

			if (this.hash == keyHash) {
				for (K k : keys) {
					if (k.equals(key)) {
						return true;
					}
				}
			}
			return false;

		}

		@Override
		boolean containsKey(final K key, final int keyHash, final int shift,
						final Comparator<Object> cmp) {

			if (this.hash == keyHash) {
				for (K k : keys) {
					if (cmp.compare(k, key) == 0) {
						return true;
					}
				}
			}
			return false;

		}

		@Override
		Optional<V> findByKey(final K key, final int keyHash, final int shift) {

			for (int i = 0; i < keys.length; i++) {
				final K _key = keys[i];
				if (key.equals(_key)) {
					final V _val = vals[i];
					return Optional.of(_val);
				}
			}
			return Optional.empty();

		}

		@Override
		Optional<V> findByKey(final K key, final int keyHash, final int shift,
						final Comparator<Object> cmp) {

			for (int i = 0; i < keys.length; i++) {
				final K _key = keys[i];
				if (cmp.compare(key, _key) == 0) {
					final V _val = vals[i];
					return Optional.of(_val);
				}
			}
			return Optional.empty();

		}

		@Override
		CompactMapNode<K, V> updated(final AtomicReference<Thread> mutator, final K key,
						final V val, final int keyHash, final int shift, final Result<K, V> details) {
			if (this.hash != keyHash) {
				details.modified();
				return mergeNodeAndKeyValPair(this, this.hash, key, val, keyHash, shift);
			}

			for (int idx = 0; idx < keys.length; idx++) {
				if (keys[idx].equals(key)) {

					final V currentVal = vals[idx];

					if (currentVal.equals(val)) {
						return this;
					}

					final V[] src = this.vals;
					@SuppressWarnings("unchecked")
					final V[] dst = (V[]) new Object[src.length];

					// copy 'src' and set 1 element(s) at position 'idx'
					System.arraycopy(src, 0, dst, 0, src.length);
					dst[idx + 0] = val;

					final CompactMapNode<K, V> thisNew = new HashCollisionMapNode_5Bits<>(
									this.hash, this.keys, dst);

					details.updated(currentVal);
					return thisNew;

				}
			}

			@SuppressWarnings("unchecked")
			final K[] keysNew = (K[]) new Object[this.keys.length + 1];

			// copy 'this.keys' and insert 1 element(s) at position
			// 'keys.length'
			System.arraycopy(this.keys, 0, keysNew, 0, keys.length);
			keysNew[keys.length + 0] = key;
			System.arraycopy(this.keys, keys.length, keysNew, keys.length + 1, this.keys.length
							- keys.length);

			@SuppressWarnings("unchecked")
			final V[] valsNew = (V[]) new Object[this.vals.length + 1];

			// copy 'this.vals' and insert 1 element(s) at position
			// 'vals.length'
			System.arraycopy(this.vals, 0, valsNew, 0, vals.length);
			valsNew[vals.length + 0] = val;
			System.arraycopy(this.vals, vals.length, valsNew, vals.length + 1, this.vals.length
							- vals.length);

			details.modified();
			return new HashCollisionMapNode_5Bits<>(keyHash, keysNew, valsNew);
		}

		@Override
		CompactMapNode<K, V> updated(final AtomicReference<Thread> mutator, final K key,
						final V val, final int keyHash, final int shift,
						final Result<K, V> details, final Comparator<Object> cmp) {
			if (this.hash != keyHash) {
				details.modified();
				return mergeNodeAndKeyValPair(this, this.hash, key, val, keyHash, shift);
			}

			for (int idx = 0; idx < keys.length; idx++) {
				if (cmp.compare(keys[idx], key) == 0) {

					final V currentVal = vals[idx];

					if (cmp.compare(currentVal, val) == 0) {
						return this;
					}

					final V[] src = this.vals;
					@SuppressWarnings("unchecked")
					final V[] dst = (V[]) new Object[src.length];

					// copy 'src' and set 1 element(s) at position 'idx'
					System.arraycopy(src, 0, dst, 0, src.length);
					dst[idx + 0] = val;

					final CompactMapNode<K, V> thisNew = new HashCollisionMapNode_5Bits<>(
									this.hash, this.keys, dst);

					details.updated(currentVal);
					return thisNew;

				}
			}

			@SuppressWarnings("unchecked")
			final K[] keysNew = (K[]) new Object[this.keys.length + 1];

			// copy 'this.keys' and insert 1 element(s) at position
			// 'keys.length'
			System.arraycopy(this.keys, 0, keysNew, 0, keys.length);
			keysNew[keys.length + 0] = key;
			System.arraycopy(this.keys, keys.length, keysNew, keys.length + 1, this.keys.length
							- keys.length);

			@SuppressWarnings("unchecked")
			final V[] valsNew = (V[]) new Object[this.vals.length + 1];

			// copy 'this.vals' and insert 1 element(s) at position
			// 'vals.length'
			System.arraycopy(this.vals, 0, valsNew, 0, vals.length);
			valsNew[vals.length + 0] = val;
			System.arraycopy(this.vals, vals.length, valsNew, vals.length + 1, this.vals.length
							- vals.length);

			details.modified();
			return new HashCollisionMapNode_5Bits<>(keyHash, keysNew, valsNew);
		}

		@Override
		CompactMapNode<K, V> removed(final AtomicReference<Thread> mutator, final K key,
						final int keyHash, final int shift, final Result<K, V> details) {

			for (int idx = 0; idx < keys.length; idx++) {
				if (keys[idx].equals(key)) {
					final V currentVal = vals[idx];
					details.updated(currentVal);

					if (this.arity() == 1) {
						return nodeOf(mutator);
					} else if (this.arity() == 2) {
						/*
						 * Create root node with singleton element. This node
						 * will be a) either be the new root returned, or b)
						 * unwrapped and inlined.
						 */
						final K theOtherKey = (idx == 0) ? keys[1] : keys[0];
						final V theOtherVal = (idx == 0) ? vals[1] : vals[0];
						return CompactMapNode.<K, V> nodeOf(mutator).updated(mutator, theOtherKey,
										theOtherVal, keyHash, 0, details);
					} else {
						@SuppressWarnings("unchecked")
						final K[] keysNew = (K[]) new Object[this.keys.length - 1];

						// copy 'this.keys' and remove 1 element(s) at position
						// 'idx'
						System.arraycopy(this.keys, 0, keysNew, 0, idx);
						System.arraycopy(this.keys, idx + 1, keysNew, idx, this.keys.length - idx
										- 1);

						@SuppressWarnings("unchecked")
						final V[] valsNew = (V[]) new Object[this.vals.length - 1];

						// copy 'this.vals' and remove 1 element(s) at position
						// 'idx'
						System.arraycopy(this.vals, 0, valsNew, 0, idx);
						System.arraycopy(this.vals, idx + 1, valsNew, idx, this.vals.length - idx
										- 1);

						return new HashCollisionMapNode_5Bits<>(keyHash, keysNew, valsNew);
					}
				}
			}
			return this;

		}

		@Override
		CompactMapNode<K, V> removed(final AtomicReference<Thread> mutator, final K key,
						final int keyHash, final int shift, final Result<K, V> details,
						final Comparator<Object> cmp) {

			for (int idx = 0; idx < keys.length; idx++) {
				if (cmp.compare(keys[idx], key) == 0) {
					final V currentVal = vals[idx];
					details.updated(currentVal);

					if (this.arity() == 1) {
						return nodeOf(mutator);
					} else if (this.arity() == 2) {
						/*
						 * Create root node with singleton element. This node
						 * will be a) either be the new root returned, or b)
						 * unwrapped and inlined.
						 */
						final K theOtherKey = (idx == 0) ? keys[1] : keys[0];
						final V theOtherVal = (idx == 0) ? vals[1] : vals[0];
						return CompactMapNode.<K, V> nodeOf(mutator).updated(mutator, theOtherKey,
										theOtherVal, keyHash, 0, details, cmp);
					} else {
						@SuppressWarnings("unchecked")
						final K[] keysNew = (K[]) new Object[this.keys.length - 1];

						// copy 'this.keys' and remove 1 element(s) at position
						// 'idx'
						System.arraycopy(this.keys, 0, keysNew, 0, idx);
						System.arraycopy(this.keys, idx + 1, keysNew, idx, this.keys.length - idx
										- 1);

						@SuppressWarnings("unchecked")
						final V[] valsNew = (V[]) new Object[this.vals.length - 1];

						// copy 'this.vals' and remove 1 element(s) at position
						// 'idx'
						System.arraycopy(this.vals, 0, valsNew, 0, idx);
						System.arraycopy(this.vals, idx + 1, valsNew, idx, this.vals.length - idx
										- 1);

						return new HashCollisionMapNode_5Bits<>(keyHash, keysNew, valsNew);
					}
				}
			}
			return this;

		}

		@Override
		boolean hasPayload() {
			return true;
		}

		@Override
		int payloadArity() {
			return keys.length;
		}

		@Override
		boolean hasNodes() {
			return false;
		}

		@Override
		int nodeArity() {
			return 0;
		}

		@Override
		int arity() {
			return payloadArity();
		}

		@Override
		byte sizePredicate() {
			return SIZE_MORE_THAN_ONE;
		}

		@Override
		K getKey(int index) {
			return keys[index];
		}

		@Override
		V getValue(int index) {
			return vals[index];
		}

		@Override
		Map.Entry<K, V> getKeyValueEntry(int index) {
			return entryOf(keys[index], vals[index]);
		}

		@Override
		public CompactMapNode<K, V> getNode(int index) {
			throw new IllegalStateException("Is leaf node.");
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 0;
			result = prime * result + hash;
			result = prime * result + Arrays.hashCode(keys);
			result = prime * result + Arrays.hashCode(vals);
			return result;
		}

		@Override
		public boolean equals(Object other) {
			if (null == other) {
				return false;
			}
			if (this == other) {
				return true;
			}
			if (getClass() != other.getClass()) {
				return false;
			}

			HashCollisionMapNode_5Bits<?, ?> that = (HashCollisionMapNode_5Bits<?, ?>) other;

			if (hash != that.hash) {
				return false;
			}

			if (arity() != that.arity()) {
				return false;
			}

			/*
			 * Linear scan for each key, because of arbitrary element order.
			 */
			outerLoop: for (int i = 0; i < that.payloadArity(); i++) {
				final java.lang.Object otherKey = that.getKey(i);
				final java.lang.Object otherVal = that.getValue(i);

				for (int j = 0; j < keys.length; j++) {
					final K key = keys[j];
					final V val = vals[j];

					if (key.equals(otherKey) && val.equals(otherVal)) {
						continue outerLoop;
					}
				}
				return false;
			}

			return true;
		}

		@Override
		CompactMapNode<K, V> copyAndSetValue(AtomicReference<Thread> mutator, final int bitpos,
						final V val) {
			throw new UnsupportedOperationException();
		}

		@Override
		CompactMapNode<K, V> copyAndInsertValue(AtomicReference<Thread> mutator, final int bitpos,
						final K key, final V val) {
			throw new UnsupportedOperationException();
		}

		@Override
		CompactMapNode<K, V> copyAndRemoveValue(AtomicReference<Thread> mutator, final int bitpos) {
			throw new UnsupportedOperationException();
		}

		@Override
		CompactMapNode<K, V> copyAndSetNode(AtomicReference<Thread> mutator, final int bitpos,
						CompactMapNode<K, V> node) {
			throw new UnsupportedOperationException();
		}

		@Override
		CompactMapNode<K, V> copyAndMigrateFromInlineToNode(final AtomicReference<Thread> mutator,
						final int bitpos, final CompactMapNode<K, V> node) {
			throw new UnsupportedOperationException();
		}

		@Override
		CompactMapNode<K, V> copyAndMigrateFromNodeToInline(final AtomicReference<Thread> mutator,
						final int bitpos, final CompactMapNode<K, V> node) {
			throw new UnsupportedOperationException();
		}

		@Override
		int nodeMap() {
			throw new UnsupportedOperationException();
		}

		@Override
		int dataMap() {
			throw new UnsupportedOperationException();
		}

	}

	/**
	 * Iterator skeleton that uses a fixed stack in depth.
	 */
	private static abstract class AbstractMapIterator<K, V> {

		// TODO: verify maximum deepness
		private static final int MAX_DEPTH = 8;

		protected int currentValueCursor;
		protected int currentValueLength;
		protected AbstractMapNode<K, V> currentValueNode;

		private int currentStackLevel;
		private final int[] nodeCursorsAndLengths = new int[MAX_DEPTH * 2];

		@SuppressWarnings("unchecked")
		AbstractMapNode<K, V>[] nodes = new AbstractMapNode[MAX_DEPTH];

		AbstractMapIterator(AbstractMapNode<K, V> rootNode) {
			currentStackLevel = 0;

			currentValueNode = rootNode;
			currentValueCursor = 0;
			currentValueLength = rootNode.payloadArity();

			nodes[0] = rootNode;
			nodeCursorsAndLengths[0] = 0;
			nodeCursorsAndLengths[1] = rootNode.nodeArity();
		}

		public boolean hasNext() {
			if (currentValueCursor < currentValueLength) {
				return true;
			} else {
				/*
				 * search for next node that contains values
				 */
				while (currentStackLevel >= 0) {
					final int currentCursorIndex = currentStackLevel * 2;
					final int currentLengthIndex = currentCursorIndex + 1;

					final int nodeCursor = nodeCursorsAndLengths[currentCursorIndex];
					final int nodeLength = nodeCursorsAndLengths[currentLengthIndex];

					if (nodeCursor < nodeLength) {
						final AbstractMapNode<K, V> nextNode = nodes[currentStackLevel]
										.getNode(nodeCursor);
						nodeCursorsAndLengths[currentCursorIndex]++;

						if (nextNode.hasNodes()) {
							/*
							 * put node on next stack level for depth-first
							 * traversal
							 */
							final int nextStackLevel = ++currentStackLevel;
							final int nextCursorIndex = nextStackLevel * 2;
							final int nextLengthIndex = nextCursorIndex + 1;

							nodes[nextStackLevel] = nextNode;
							nodeCursorsAndLengths[nextCursorIndex] = 0;
							nodeCursorsAndLengths[nextLengthIndex] = nextNode.nodeArity();
						}

						if (nextNode.hasPayload()) {
							/*
							 * found next node that contains values
							 */
							currentValueNode = nextNode;
							currentValueCursor = 0;
							currentValueLength = nextNode.payloadArity();
							return true;
						}
					} else {
						currentStackLevel--;
					}
				}
			}

			return false;
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	private static final class MapKeyIterator<K, V> extends AbstractMapIterator<K, V> implements
					SupplierIterator<K, V> {

		MapKeyIterator(AbstractMapNode<K, V> rootNode) {
			super(rootNode);
		}

		@Override
		public K next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			} else {
				return currentValueNode.getKey(currentValueCursor++);
			}
		}

		@Override
		public V get() {
			throw new UnsupportedOperationException();
		}
	}

	private static final class MapValueIterator<K, V> extends AbstractMapIterator<K, V> implements
					SupplierIterator<V, K> {

		MapValueIterator(AbstractMapNode<K, V> rootNode) {
			super(rootNode);
		}

		@Override
		public V next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			} else {
				return currentValueNode.getValue(currentValueCursor++);
			}
		}

		@Override
		public K get() {
			throw new UnsupportedOperationException();
		}
	}

	private static final class MapEntryIterator<K, V> extends AbstractMapIterator<K, V> implements
					SupplierIterator<Map.Entry<K, V>, K> {

		MapEntryIterator(AbstractMapNode<K, V> rootNode) {
			super(rootNode);
		}

		@Override
		public Map.Entry<K, V> next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			} else {
				return currentValueNode.getKeyValueEntry(currentValueCursor++);
			}
		}

		@Override
		public K get() {
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * Iterator that first iterates over inlined-values and then continues depth
	 * first recursively.
	 */
	private static class TrieMap_5BitsNodeIterator<K, V> implements Iterator<AbstractMapNode<K, V>> {

		final Deque<Iterator<? extends AbstractMapNode<K, V>>> nodeIteratorStack;

		TrieMap_5BitsNodeIterator(AbstractMapNode<K, V> rootNode) {
			nodeIteratorStack = new ArrayDeque<>();
			nodeIteratorStack.push(Collections.singleton(rootNode).iterator());
		}

		@Override
		public boolean hasNext() {
			while (true) {
				if (nodeIteratorStack.isEmpty()) {
					return false;
				} else {
					if (nodeIteratorStack.peek().hasNext()) {
						return true;
					} else {
						nodeIteratorStack.pop();
						continue;
					}
				}
			}
		}

		@Override
		public AbstractMapNode<K, V> next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			AbstractMapNode<K, V> innerNode = nodeIteratorStack.peek().next();

			if (innerNode.hasNodes()) {
				nodeIteratorStack.push(innerNode.nodeIterator());
			}

			return innerNode;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	static final class TransientTrieMap_5Bits<K, V> extends AbstractMap<K, V> implements
					TransientMap<K, V> {
		final private AtomicReference<Thread> mutator;
		private AbstractMapNode<K, V> rootNode;
		private int hashCode;
		private int cachedSize;

		TransientTrieMap_5Bits(TrieMap_5Bits<K, V> trieMap_5Bits) {
			this.mutator = new AtomicReference<Thread>(Thread.currentThread());
			this.rootNode = trieMap_5Bits.rootNode;
			this.hashCode = trieMap_5Bits.hashCode;
			this.cachedSize = trieMap_5Bits.cachedSize;
			if (DEBUG) {
				assert checkHashCodeAndSize(hashCode, cachedSize);
			}
		}

		private boolean checkHashCodeAndSize(final int targetHash, final int targetSize) {
			int hash = 0;
			int size = 0;

			for (Iterator<Map.Entry<K, V>> it = entryIterator(); it.hasNext();) {
				final Map.Entry<K, V> entry = it.next();
				final K key = entry.getKey();
				final V val = entry.getValue();

				hash += key.hashCode() ^ val.hashCode();
				size += 1;
			}

			return hash == targetHash && size == targetSize;
		}

		@Override
		public boolean containsKey(Object o) {
			try {
				@SuppressWarnings("unchecked")
				final K key = (K) o;
				return rootNode.containsKey(key, key.hashCode(), 0);
			} catch (ClassCastException unused) {
				return false;
			}
		}

		@Override
		public boolean containsKeyEquivalent(Object o, Comparator<Object> cmp) {
			try {
				@SuppressWarnings("unchecked")
				final K key = (K) o;
				return rootNode.containsKey(key, key.hashCode(), 0, cmp);
			} catch (ClassCastException unused) {
				return false;
			}
		}

		@Override
		public V get(Object o) {
			try {
				@SuppressWarnings("unchecked")
				final K key = (K) o;
				final Optional<V> result = rootNode.findByKey(key, key.hashCode(), 0);

				if (result.isPresent()) {
					return result.get();
				} else {
					return null;
				}
			} catch (ClassCastException unused) {
				return null;
			}
		}

		@Override
		public V getEquivalent(Object o, Comparator<Object> cmp) {
			try {
				@SuppressWarnings("unchecked")
				final K key = (K) o;
				final Optional<V> result = rootNode.findByKey(key, key.hashCode(), 0, cmp);

				if (result.isPresent()) {
					return result.get();
				} else {
					return null;
				}
			} catch (ClassCastException unused) {
				return null;
			}
		}

		@Override
		public V __put(final K key, final V val) {
			if (mutator.get() == null) {
				throw new IllegalStateException("Transient already frozen.");
			}

			final int keyHash = key.hashCode();
			final Result<K, V> details = Result.unchanged();

			final CompactMapNode<K, V> newRootNode = rootNode.updated(mutator, key, val, keyHash,
							0, details);

			if (details.isModified()) {
				rootNode = newRootNode;

				if (details.hasReplacedValue()) {
					final V old = details.getReplacedValue();

					final int valHashOld = old.hashCode();
					final int valHashNew = val.hashCode();

					hashCode += keyHash ^ valHashNew;
					hashCode -= keyHash ^ valHashOld;
					// cachedSize remains same

					if (DEBUG) {
						assert checkHashCodeAndSize(hashCode, cachedSize);
					}
					return old;
				} else {
					final int valHashNew = val.hashCode();

					hashCode += keyHash ^ valHashNew;
					cachedSize += 1;

					if (DEBUG) {
						assert checkHashCodeAndSize(hashCode, cachedSize);
					}
					return null;
				}
			}

			if (DEBUG) {
				assert checkHashCodeAndSize(hashCode, cachedSize);
			}
			return null;
		}

		@Override
		public V __putEquivalent(final K key, final V val, final Comparator<Object> cmp) {
			if (mutator.get() == null) {
				throw new IllegalStateException("Transient already frozen.");
			}

			final int keyHash = key.hashCode();
			final Result<K, V> details = Result.unchanged();

			final CompactMapNode<K, V> newRootNode = rootNode.updated(mutator, key, val, keyHash,
							0, details, cmp);

			if (details.isModified()) {
				rootNode = newRootNode;

				if (details.hasReplacedValue()) {
					final V old = details.getReplacedValue();

					final int valHashOld = old.hashCode();
					final int valHashNew = val.hashCode();

					hashCode += keyHash ^ valHashNew;
					hashCode -= keyHash ^ valHashOld;
					// cachedSize remains same

					if (DEBUG) {
						assert checkHashCodeAndSize(hashCode, cachedSize);
					}
					return old;
				} else {
					final int valHashNew = val.hashCode();

					hashCode += keyHash ^ valHashNew;
					cachedSize += 1;

					if (DEBUG) {
						assert checkHashCodeAndSize(hashCode, cachedSize);
					}
					return null;
				}
			}

			if (DEBUG) {
				assert checkHashCodeAndSize(hashCode, cachedSize);
			}
			return null;
		}

		@Override
		public boolean __putAll(final Map<? extends K, ? extends V> map) {
			boolean modified = false;

			for (Entry<? extends K, ? extends V> entry : map.entrySet()) {
				final boolean isPresent = containsKey(entry.getKey());
				final V replaced = __put(entry.getKey(), entry.getValue());

				if (!isPresent || replaced != null) {
					modified = true;
				}
			}

			return modified;
		}

		@Override
		public boolean __putAllEquivalent(final Map<? extends K, ? extends V> map,
						final Comparator<Object> cmp) {
			boolean modified = false;

			for (Entry<? extends K, ? extends V> entry : map.entrySet()) {
				final boolean isPresent = containsKeyEquivalent(entry.getKey(), cmp);
				final V replaced = __putEquivalent(entry.getKey(), entry.getValue(), cmp);

				if (!isPresent || replaced != null) {
					modified = true;
				}
			}

			return modified;
		}

		@Override
		public boolean __remove(final K key) {
			if (mutator.get() == null) {
				throw new IllegalStateException("Transient already frozen.");

			}

			final int keyHash = key.hashCode();
			final Result<K, V> details = Result.unchanged();

			final CompactMapNode<K, V> newRootNode = rootNode.removed(mutator, key, keyHash, 0,
							details);

			if (details.isModified()) {

				assert details.hasReplacedValue();
				final int valHash = details.getReplacedValue().hashCode();

				rootNode = newRootNode;
				hashCode -= keyHash ^ valHash;
				cachedSize -= 1;

				if (DEBUG) {
					assert checkHashCodeAndSize(hashCode, cachedSize);
				}
				return true;

			}

			if (DEBUG) {
				assert checkHashCodeAndSize(hashCode, cachedSize);
			}
			return false;
		}

		@Override
		public boolean __removeEquivalent(final K key, Comparator<Object> cmp) {
			if (mutator.get() == null) {
				throw new IllegalStateException("Transient already frozen.");
			}

			final int keyHash = key.hashCode();
			final Result<K, V> details = Result.unchanged();

			final CompactMapNode<K, V> newRootNode = rootNode.removed(mutator, key, keyHash, 0,
							details, cmp);

			if (details.isModified()) {

				assert details.hasReplacedValue();
				final int valHash = details.getReplacedValue().hashCode();

				rootNode = newRootNode;
				hashCode -= keyHash ^ valHash;
				cachedSize -= 1;

				if (DEBUG) {
					assert checkHashCodeAndSize(hashCode, cachedSize);
				}
				return true;

			}

			if (DEBUG) {
				assert checkHashCodeAndSize(hashCode, cachedSize);
			}
			return false;
		}

		@Override
		public Set<java.util.Map.Entry<K, V>> entrySet() {
			Set<java.util.Map.Entry<K, V>> entrySet = null;

			if (entrySet == null) {
				entrySet = new AbstractSet<java.util.Map.Entry<K, V>>() {
					@Override
					public Iterator<java.util.Map.Entry<K, V>> iterator() {
						return new Iterator<Entry<K, V>>() {
							private final Iterator<Entry<K, V>> i = entryIterator();

							@Override
							public boolean hasNext() {
								return i.hasNext();
							}

							@Override
							public Entry<K, V> next() {
								return i.next();
							}

							@Override
							public void remove() {
								i.remove();
							}
						};
					}

					@Override
					public int size() {
						return TransientTrieMap_5Bits.this.size();
					}

					@Override
					public boolean isEmpty() {
						return TransientTrieMap_5Bits.this.isEmpty();
					}

					@Override
					public void clear() {
						TransientTrieMap_5Bits.this.clear();
					}

					@Override
					public boolean contains(Object k) {
						return TransientTrieMap_5Bits.this.containsKey(k);
					}
				};
			}
			return entrySet;
		}

		@Override
		public int size() {
			return cachedSize;
		}

		@Override
		public SupplierIterator<K, V> keyIterator() {
			return new TransientMapKeyIterator<>(this);
		}

		@Override
		public Iterator<V> valueIterator() {
			// return new TrieMapValueIterator<>(keyIterator());
			return new MapValueIterator<>(rootNode); // TODO: iterator does not
														// support removal
		}

		@Override
		public Iterator<Map.Entry<K, V>> entryIterator() {
			// return new TrieMapEntryIterator<>(keyIterator());
			return new MapEntryIterator<>(rootNode); // TODO: iterator does not
														// support removal
		}

		/**
		 * Iterator that first iterates over inlined-values and then continues
		 * depth first recursively.
		 */
		private static class TransientMapKeyIterator<K, V> extends AbstractMapIterator<K, V>
						implements SupplierIterator<K, V> {

			final TransientTrieMap_5Bits<K, V> transientTrieMap_5Bits;
			K lastKey;

			TransientMapKeyIterator(TransientTrieMap_5Bits<K, V> transientTrieMap_5Bits) {
				super(transientTrieMap_5Bits.rootNode);
				this.transientTrieMap_5Bits = transientTrieMap_5Bits;
			}

			@Override
			public K next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				} else {
					lastKey = currentValueNode.getKey(currentValueCursor++);
					return lastKey;
				}
			}

			@Override
			public V get() {
				throw new UnsupportedOperationException();
			}

			/*
			 * TODO: test removal with iteration rigorously
			 */
			@Override
			public void remove() {
				boolean success = transientTrieMap_5Bits.__remove(lastKey);

				if (!success) {
					throw new IllegalStateException("Key from iteration couldn't be deleted.");
				}
			}
		}

		@Override
		public boolean equals(Object other) {
			if (other == this) {
				return true;
			}
			if (other == null) {
				return false;
			}

			if (other instanceof TransientTrieMap_5Bits) {
				TransientTrieMap_5Bits<?, ?> that = (TransientTrieMap_5Bits<?, ?>) other;

				if (this.size() != that.size()) {
					return false;
				}

				return rootNode.equals(that.rootNode);
			}

			return super.equals(other);
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public ImmutableMap<K, V> freeze() {
			if (mutator.get() == null) {
				throw new IllegalStateException("Transient already frozen.");
			}

			mutator.set(null);
			return new TrieMap_5Bits<K, V>(rootNode, hashCode, cachedSize);
		}
	}

}