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
package org.eclipse.imp.pdb.facts.impl.persistent;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;

import org.eclipse.imp.pdb.facts.ISet;
import org.eclipse.imp.pdb.facts.IValue;
import org.eclipse.imp.pdb.facts.IValueFactory;
import org.eclipse.imp.pdb.facts.impl.AbstractSet;
import org.eclipse.imp.pdb.facts.type.Type;
import org.eclipse.imp.pdb.facts.util.EqualityUtils;
import org.eclipse.imp.pdb.facts.util.ImmutableSet;
import org.eclipse.imp.pdb.facts.util.TrieSet;

public final class PDBPersistentHashSet extends AbstractSet {
	
	@SuppressWarnings("unchecked")
	private static final Comparator<Object> equalityComparator = EqualityUtils.getDefaultEqualityComparator();
	
	@SuppressWarnings("unchecked")
	private static final Comparator<Object> equivalenceComparator = EqualityUtils.getEquivalenceComparator();
	
	private Type cachedElementType;
	private final ImmutableSet<IValue> content;

	public PDBPersistentHashSet() {
		this.cachedElementType = null;
		this.content = TrieSet.of();
	}

	public PDBPersistentHashSet(ImmutableSet<IValue> content) {
		Objects.requireNonNull(content);
		this.content = content;
	}

	@Override
	protected IValueFactory getValueFactory() {
		return ValueFactory1.getInstance();
	}

	@Override
	public Type getType() {
		// calculate dynamic element type
		if (cachedElementType == null) {
			cachedElementType = getTypeFactory().voidType();

			for (IValue element : content) {
				cachedElementType = cachedElementType.lub(element.getType());
			}
		}

		final Type inferredElementType = cachedElementType;
		final Type inferredCollectionType;

		// consists collection out of tuples?
		if (inferredElementType.isFixedWidth()) {
			inferredCollectionType = getTypeFactory().relTypeFromTuple(cachedElementType);
		} else {
			inferredCollectionType = getTypeFactory().setType(cachedElementType);
		}

		return inferredCollectionType;
	}

	@Override
	public boolean isEmpty() {
		return content.isEmpty();
	}

	@Override
	public ISet insert(IValue value) {
		final ImmutableSet<IValue> contentNew = 
				content.__insertEquivalent(value, equivalenceComparator);

		if (content == contentNew)
			return this;

		return new PDBPersistentHashSet(contentNew);
	}

	@Override
	public ISet delete(IValue value) {
		final ImmutableSet<IValue> contentNew = 
				content.__removeEquivalent(value, equivalenceComparator);

		if (content == contentNew)
			return this;

		return new PDBPersistentHashSet(contentNew);
	}

	@Override
	public int size() {
		return content.size();
	}

	@Override
	public boolean contains(IValue value) {
		return content.containsEquivalent(value, equivalenceComparator);
	}

	@Override
	public Iterator<IValue> iterator() {
		return content.iterator();
	}

	@Override
	public int hashCode() {
		return content.hashCode();
	}
	
	@Override
	public boolean equals(Object other) {
		if (other == this)
			return true;
		if (other == null)
			return false;
		
		if (other instanceof PDBPersistentHashSet) {
			PDBPersistentHashSet that = (PDBPersistentHashSet) other;

			if (this.size() != that.size())
				return false;

			return content.equals(that.content);
		}
		
		if (other instanceof ISet) {
			ISet that = (ISet) other;

			// not necessary because of tightly calculated dynamic types
//			if (this.getType() != that.getType())
//				return false;
			
			if (this.size() != that.size())
				return false;
			
	        // TODO: API is missing a containsAll() equivalent
			for (IValue e : that)
	            if (!content.containsEquivalent(e, equalityComparator))
	                return false;

	        return true;			
		}
		
		return false;
	}
	
	@Override
	public boolean isEqual(IValue other) {
		if (other == this)
			return true;
		if (other == null)
			return false;
		
		if (other instanceof ISet) {
			ISet that = (ISet) other;
			
			if (this.size() != that.size())
				return false;
			
	        // TODO: API is missing a containsAll() equivalent
			for (IValue e : that)
	            if (!content.containsEquivalent(e, equivalenceComparator))
	                return false;

	        return true;			
		}
		
		return false;
	}

	@Override
	public ISet union(ISet other) {
		if (other == this)
			return this;
		if (other == null)
			return this;

		if (other instanceof PDBPersistentHashSet) {
			PDBPersistentHashSet that = (PDBPersistentHashSet) other;

			ImmutableSet<IValue> one;
			ImmutableSet<IValue> two;
						
			if (that.size() >= this.size()) {
				one = that.content;
				two = this.content;
			} else {
				one = this.content;
				two = that.content;
			}

			ImmutableSet<IValue> result = one.__insertAllEquivalent(two,
					equivalenceComparator);

			return (result == one) ? this : new PDBPersistentHashSet(result);
		} else {
			return super.union(other);
		}
	}
	
	// TODO: check if operation modified set
	@Override
	public ISet intersect(ISet other) {
		if (other == this)
			return this;
//		if (other == null)
//			return this;

		if (other instanceof PDBPersistentHashSet) {
			PDBPersistentHashSet that = (PDBPersistentHashSet) other;

			if (that.size() >= this.size()) {
				return new PDBPersistentHashSet(
						this.content.__retainAllEquivalent(that.content,
								equivalenceComparator));
			} else {
				return new PDBPersistentHashSet(
						that.content.__retainAllEquivalent(this.content,
								equivalenceComparator));
			}
		} else {
			return super.intersect(other);
		}
	}

	// TODO: check if operation modified set
	@Override
	public ISet subtract(ISet other) {
//		if (other == this)
//			return this;
//		if (other == null)
//			return this;

		if (other instanceof PDBPersistentHashSet) {
			PDBPersistentHashSet that = (PDBPersistentHashSet) other;

			return new PDBPersistentHashSet(this.content.__removeAllEquivalent(
					that.content, equivalenceComparator));
		} else {
			return super.intersect(other);
		}
	}
		
}
