/*******************************************************************************
* Copyright (c) 2009 Centrum Wiskunde en Informatica (CWI)
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*    Arnold Lankamp - interfaces and implementation
*******************************************************************************/
package io.usethesource.vallang.impl.persistent;

import java.util.Iterator;

import io.usethesource.vallang.IList;
import io.usethesource.vallang.IListWriter;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IWriter;
import io.usethesource.vallang.impl.util.collections.ShareableValuesList;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;

/**
 * Implementation of IListWriter.
 * 
 * @author Arnold Lankamp
 */
/*package*/ class ListWriter implements IListWriter{
    private Type elementType;
	private final ShareableValuesList data;
	private IList constructedList;
	private boolean unique = false;
	
	/*package*/ ListWriter() {
		super();
		
		this.elementType = TypeFactory.getInstance().voidType();
		data = new ShareableValuesList();
		
		constructedList = null;
	}
	
	private ListWriter(boolean unique) {
	    this();
	    this.unique = unique;
	}
	
	@Override
    public IWriter<IList> unique() {
        return new ListWriter(true);
    }

	@Override
	public Iterator<IValue> iterator() {
	    return data.iterator();
	}
	
	/*package*/ ListWriter(Type elementType, ShareableValuesList data){
		super();
		
		this.elementType = elementType;
		this.data = data;
		
		constructedList = null;
	}
	
	@Override
	public void insertTuple(IValue... fields) {
	    insert(Tuple.newTuple(fields));
	}
	
	public void append(IValue element){
		checkMutation();
		
		if (unique && data.contains(element)) {
		    return;
        }
		
		updateType(element);
		data.append(element);
	}
	
	@Override
	public void appendTuple(IValue... fields) {
	    append(Tuple.newTuple(fields));
	}
	
	private void updateType(IValue element) {
	    elementType = elementType.lub(element.getType());
	}

	public void append(IValue... elems){
		checkMutation();
		boolean notUnique = false;
		
		for (IValue elem : elems){
		    if (unique && data.contains(elem)) {
		        notUnique = true;
		        break;
		    }
			updateType(elem);
		}
		
		if (!notUnique) {
		    data.appendAll(elems);
		}
		else {
		    for (IValue next : elems) {
		        if (unique && data.contains(next)) {
	                continue;
	            }
	            
	            updateType(next);
	            data.append(next);
		    }
		}
	}
	
	public void appendAll(Iterable<? extends IValue> collection){
		checkMutation();
		
		for (IValue next : collection) {
			if (unique && data.contains(next)) {
	            continue;
	        }
			
			updateType(next);
			data.append(next);
		}
	}
	
	public void insert(IValue elem){
		checkMutation();
		if (unique && data.contains(elem)) {
		    return;
		}
		updateType(elem);
		data.insert(elem);
	}
	
	public void insert(IValue... elements){
		insert(elements, 0, elements.length);
	}
	
	public void insert(IValue[] elements, int start, int length){
		checkMutation();
		checkBounds(elements, start, length);
		
		for(int i = start + length - 1; i >= start; i--){
			updateType(elements[i]);
			
			if (unique && data.contains(elements[i])) {
			    continue;
			}
			
			data.insert(elements[i]);
		}
	}
	
	public void insertAll(Iterable<? extends IValue> collection){
		checkMutation();
		
		for (IValue next : collection) {
			if (unique && data.contains(next)) {
                continue;
            }
			updateType(next);
			data.insert(next);
		}
	}
	
	public void insertAt(int index, IValue element){
		checkMutation();
		
		updateType(element);
		data.insertAt(index, element);
	}
	
	public void insertAt(int index, IValue... elements){
		insertAt(index, elements, 0, 0);
	}
	
	public void insertAt(int index, IValue[] elements, int start, int length){
		checkMutation();
		checkBounds(elements, start, length);
		
		for(int i = start + length - 1; i >= start; i--){
			updateType(elements[i]);
			data.insertAt(index, elements[i]);
		}
	}
	
	public IValue replaceAt(int index, IValue element){
		checkMutation();
		
		updateType(element);
		return data.set(index, element);
	}

	@Override
	public IValue get(int i) throws IndexOutOfBoundsException {
		return data.get(i);
	}
	
	@Override
	public int length() {
		return data.size();
	}
	
	protected void checkMutation(){
		if(constructedList != null) throw new UnsupportedOperationException("Mutation of a finalized list is not supported.");
	}
	
	private void checkBounds(IValue[] elems, int start, int length){
		if(start < 0) throw new ArrayIndexOutOfBoundsException("start < 0");
		if((start + length) > elems.length) throw new ArrayIndexOutOfBoundsException("(start + length) > elems.length");
	}
	
	@Override
	public IList done() {
		if (constructedList == null) {
			constructedList = List.newList(elementType, data);
		}
		
		return constructedList;
	}
}