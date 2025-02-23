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
 *
 * Based on code by:
 *
 *   * Robert Fuhrer (rfuhrer@watson.ibm.com) - initial API and implementation
 *******************************************************************************/
package io.usethesource.vallang.impl.reference;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.function.Supplier;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import io.usethesource.vallang.IList;
import io.usethesource.vallang.IListWriter;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IWriter;
import io.usethesource.vallang.exceptions.FactTypeUseException;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;

/**
 * This class does not guarantee thread-safety. Users must lock the writer object for thread safety.
 * It is thread-friendly however.
 */
/*package*/ class ListWriter implements IListWriter {
    private Type eltType;
    private final java.util.List<IValue> listContent;
    private @MonotonicNonNull IList constructedList;
    private boolean unique;

    /*package*/ ListWriter() {
        super();

        this.eltType = TypeFactory.getInstance().voidType();
        listContent = new LinkedList<>();
        unique = false;
    }

    private ListWriter(boolean unique) {
        this();
        this.unique = unique;
    }

    @Override
    public IListWriter unique() {
        return new ListWriter(true);
    }

    @Override
    public Iterator<IValue> iterator() {
        return listContent.iterator();
    }

    private void checkMutation(){
        if(constructedList != null) { throw new UnsupportedOperationException("Mutation of a finalized list is not supported."); }
    }

    private void put(int index, IValue elem) {
        if (unique && listContent.contains(elem)) {
            return;
        }

        eltType = eltType.lub(elem.getType());
        listContent.add(index, elem);
    }

    public void insert(IValue elem) throws FactTypeUseException {
        checkMutation();

        put(0, elem);
    }

    @Override
    public void insert(IValue[] elems, int start, int length) throws FactTypeUseException{
        checkMutation();
        checkBounds(elems, start, length);

        for(int i = start + length - 1; i >= start; i--){
            updateType(elems[i]);
            put(0, elems[i]);
        }
    }

    @Override
    public IValue replaceAt(int index, IValue elem) throws FactTypeUseException, IndexOutOfBoundsException {
        checkMutation();
        updateType(elem);
        return listContent.set(index, elem);
    }

    @Override
    public void insert(IValue... elems) throws FactTypeUseException{
        insert(elems, 0, elems.length);
    }

    @Override
    public void insertAt(int index, IValue[] elems, int start, int length) throws FactTypeUseException{
        checkMutation();
        checkBounds(elems, start, length);

        for(int i = start + length - 1; i >= start; i--) {
            eltType = eltType.lub(elems[i].getType());
            put(index, elems[i]);
        }
    }

    @Override
    public void insertAt(int index, IValue... elems) throws FactTypeUseException{
        insertAt(index,  elems, 0, 0);
    }

    public void append(IValue elem) throws FactTypeUseException{
        checkMutation();
        updateType(elem);
        put(listContent.size(), elem);
    }

    @Override
    public void append(IValue... elems) throws FactTypeUseException{
        checkMutation();

        for(IValue elem : elems){
            updateType(elem);
            put(listContent.size(), elem);
        }
    }

    @Override
    public void appendTuple(IValue... fields) {
        append(new Tuple(fields));
    }

    @Override
    public void appendAll(Iterable<? extends IValue> collection) throws FactTypeUseException{
        checkMutation();

        for(IValue v : collection){
            put(listContent.size(), v);
        }
    }

    private void updateType(IValue v) {
        eltType = eltType.lub(v.getType());
    }

    @Override
    public IList done() {
        if (constructedList == null) {
            constructedList = new List(eltType, listContent);
        }

        return constructedList;
    }

    private void checkBounds(IValue[] elems, int start, int length) {
        if(start < 0) { throw new ArrayIndexOutOfBoundsException("start < 0"); }
        if((start + length) > elems.length) { throw new ArrayIndexOutOfBoundsException("(start + length) > elems.length"); }
    }

    @Override
    public IValue get(int i) throws IndexOutOfBoundsException {
        return listContent.get(i);
    }

    @Override
    public int length() {
        return listContent.size();
    }

    @Override
    public void insertTuple(IValue... fields) {
        insert(ValueFactory.getInstance().tuple(fields));
    }

    @Override
    public Supplier<IWriter<IList>> supplier() {
        return () -> ValueFactory.getInstance().listWriter();
    }

}
