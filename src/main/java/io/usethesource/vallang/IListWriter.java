/*******************************************************************************
* Copyright (c) 2007 IBM Corporation.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*    Robert Fuhrer (rfuhrer@watson.ibm.com) - initial API and implementation

*******************************************************************************/

package io.usethesource.vallang;

import io.usethesource.vallang.exceptions.FactTypeUseException;

/**
 * This interface allows to gather the elements of a list efficiently and in a specific order.
 * When all elements have been gathered the done() method can be used to obtain an immutable IList
 * with the gathered elements in the specified order.
 *
 * Note: implementations are not required to guarantee thread-safe access to the writer object.
 */
public interface IListWriter extends IWriter<IList> {

    @Override
    public default IListWriter unique() {
        return (IListWriter) IWriter.super.unique();
    }

    /**
     * Inserts elements at a specific position, keeping the argument in order of appearance.
     *
     * @param index
     * @param value an array of elements to insert .
     * @throws FactTypeUseException when done() was called before or when the elements have an incompatible type.
     * @throws IndexOutOfBoundsException
     */
    public void insertAt(int index, IValue... value);

    /**
     * Inserts elements in front, keeping the argument in order of appearance.
     *
     * @param elems an array of elements to insert
     * @param start index to start copying elements from
     * @param length amount of elements to copy from the array
     *
     * @throws FactTypeUseException when done() was called before or when the elements have an incompatible type.
     * @throws IndexOutOfBoundsException
     */
    public void insert(IValue[] elems, int start, int length);

    /**
     * Inserts elements at a specific position, keeping the argument in order of appearance.
     *
     * @param index place to insert elements at
     * @param elems an array of elements to insert
     * @param start index to start copying elements from
     * @param length amount of elements to copy from the array
     *
     * @throws FactTypeUseException when done() was called before or when the elements have an incompatible type.
     * @throws IndexOutOfBoundsException
     */
    public void insertAt(int index, IValue[] elems, int start, int length);

    /**
     * Replaces an existing element at index in the list.
     * @param index the location where to replace the element
     * @param elem the new element
     * @throws FactTypeUseException when the type of the new element is not a subtype of the element type
     * @throws IndexOutOfBoundsException
     * @returns the replaced element
     */
    public IValue replaceAt(int index, IValue elem);

    /**
     * Return the ith element of the list.
     *
     * @param i
     * @return the ith element of the list
     * @throws IndexOutOfBoundsException when i < 0 or i >= IList.length
     */
    public IValue get(int i);

    /**
     * @return the number of elements in the list
     */
    public int length();
}
