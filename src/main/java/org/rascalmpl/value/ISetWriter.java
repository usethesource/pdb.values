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

package org.rascalmpl.value;

import org.rascalmpl.value.exceptions.FactTypeUseException;


public interface ISetWriter extends IWriter {
    void insert(IValue... v) throws FactTypeUseException ;
    void insertAll(Iterable<? extends IValue> collection) throws FactTypeUseException;
    ISet done();
}