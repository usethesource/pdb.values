/*******************************************************************************
 * Copyright (c) 2013 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *
 *   * Michael Steindorfer - Michael.Steindorfer@cwi.nl - CWI
 *   * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI
 *   * Paul Klint - Paul.Klint@cwi.nl - CWI
 *   * Anya Helene Bagge - anya@ii.uib.no - UiB
 *
 * Based on code by:
 *
 *   * Robert Fuhrer (rfuhrer@watson.ibm.com) - initial API and implementation
 *******************************************************************************/
package io.usethesource.vallang.impl.reference;

import java.util.Iterator;
import java.util.Map.Entry;

import io.usethesource.vallang.IMap;
import io.usethesource.vallang.IMapWriter;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.exceptions.FactTypeUseException;
import io.usethesource.vallang.exceptions.UnexpectedMapKeyTypeException;
import io.usethesource.vallang.exceptions.UnexpectedMapValueTypeException;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;

/*package*/ class MapWriter implements IMapWriter {
	private Type staticKeyType;
	private Type staticValueType;
	private final boolean inferred;
	private final java.util.HashMap<IValue, IValue> mapContent;
	private Map constructedMap;

	/*package*/ MapWriter(){
		super();
		
		this.staticKeyType = TypeFactory.getInstance().voidType();
		this.staticValueType = TypeFactory.getInstance().voidType();
		this.inferred = true;
		
		mapContent = new java.util.HashMap<>();
	}

	/*package*/ MapWriter(Type mapType){
		super();
		
		if(mapType.isFixedWidth() && mapType.getArity() >= 2) {
			mapType = TypeFactory.getInstance().mapTypeFromTuple(mapType);
		}
		
		this.staticKeyType = mapType.getKeyType();
		this.staticValueType = mapType.getValueType();
		this.inferred = false;
		
		mapContent = new java.util.HashMap<>();
	}
	
	@Override
	public Iterator<IValue> iterator() {
	    return mapContent.keySet().iterator();
	}
	
	@Override
	public IValue get(IValue key) {
	    return mapContent.get(key);
	}
	
	@Override
	public void insertTuple(IValue... fields) {
	    if (fields.length != 2) {
	        throw new IllegalArgumentException("can only insert tuples of arity 2 into a map");
	    }
	    
	    put(fields[0], fields[1]);
	}
	
	private static void check(Type key, Type value, Type keyType, Type valueType)
			throws FactTypeUseException {
		if (!key.isSubtypeOf(keyType)) {
			throw new UnexpectedMapKeyTypeException(keyType, key);
		}
		if (!value.isSubtypeOf(valueType)) {
			throw new UnexpectedMapValueTypeException(valueType, value);
		}
	}
	
	private void checkMutation() {
		if (constructedMap != null)
			throw new UnsupportedOperationException(
					"Mutation of a finalized list is not supported.");
	}
	
	@Override
	public void putAll(IMap map) throws FactTypeUseException{
		checkMutation();
		
		for(IValue key : map){
			IValue value = map.get(key);
			updateTypes(key, value);
			mapContent.put(key, value);
		}
	}
	
	private void updateTypes(IValue key, IValue value) {
		if (inferred) {
			staticKeyType = staticKeyType.lub(key.getType());
			staticValueType = staticValueType.lub(value.getType());
		}
		
	}

	@Override
	public void putAll(java.util.Map<IValue, IValue> map) throws FactTypeUseException{
		checkMutation();
		for(Entry<IValue, IValue> entry : map.entrySet()){
			IValue value = entry.getValue();
			updateTypes(entry.getKey(), value);
			check(entry.getKey().getType(), value.getType(), staticKeyType, staticValueType);
			mapContent.put(entry.getKey(), value);
		}
	}

	@Override
	public void put(IValue key, IValue value) throws FactTypeUseException{
		checkMutation();
		updateTypes(key,value);
		mapContent.put(key, value);
	}
	
	@Override
	public void insert(IValue... value) throws FactTypeUseException {
		for (IValue tuple : value) {
			ITuple t = (ITuple) tuple;
			IValue key = t.get(0);
			IValue value2 = t.get(1);
			updateTypes(key,value2);
			put(key, value2);
		}
	}
	
	@Override
	public Iterator<Entry<IValue, IValue>> entryIterator() {
	    return mapContent.entrySet().iterator();
	}
	
	@Override
	public IMap done(){
		if (constructedMap == null) {
			constructedMap = new Map(computeType(), mapContent);
		}

		return constructedMap;
	}	
	
}
