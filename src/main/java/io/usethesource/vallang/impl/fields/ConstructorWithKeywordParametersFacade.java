/*******************************************************************************
 * Copyright (c) 2015 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *
 *   * Michael Steindorfer - Michael.Steindorfer@cwi.nl - CWI
 *******************************************************************************/
package io.usethesource.vallang.impl.fields;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IWithKeywordParameters;
import io.usethesource.vallang.io.StandardTextWriter;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.visitors.IValueVisitor;

public class ConstructorWithKeywordParametersFacade implements IConstructor {
    protected final IConstructor content;
    protected final io.usethesource.capsule.Map.Immutable<String, IValue> parameters;

    public ConstructorWithKeywordParametersFacade(final IConstructor content, final io.usethesource.capsule.Map.Immutable<String, IValue> parameters) {
        this.content = content;
        this.parameters = parameters;
    }

    @Override
    public INode setChildren(IValue[] childArray) {
        return content.setChildren(childArray).asWithKeywordParameters().setParameters(parameters);
    }

    @Override
    public Type getType() {
        return content.getType();
    }

    @Override
    public <T, E extends Throwable> T accept(IValueVisitor<T, E> v) throws E {
        return v.visitConstructor(this);
    }

    @Override
    public IValue get(int i) {
        return content.get(i);
    }

    @Override
    public IConstructor set(int i, IValue newChild) {
        IConstructor newContent = content.set(i, newChild);
        return new ConstructorWithKeywordParametersFacade(newContent, parameters); // TODO: introduce wrap() here as well
    }

    @Override
    public int arity() {
        return content.arity();
    }

    @Override
    public String toString() {
        return StandardTextWriter.valueToString(this);
    }

    @Override
    public String getName() {
        return content.getName();
    }

    @Override
    public Iterable<IValue> getChildren() {
        return content.getChildren();
    }

    @Override
    public Iterator<IValue> iterator() {
        return content.iterator();
    }

    @Override
    public IConstructor replace(int first, int second, int end, IList repl) {
        throw new UnsupportedOperationException("Replace not supported on constructor.");
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == this) {
            return true;
        }

        if (o == null) {
            return false;
        }

        if(o.getClass() == getClass()){
            ConstructorWithKeywordParametersFacade other = (ConstructorWithKeywordParametersFacade) o;

            return content.equals(other.content) &&
                    parameters.equals(other.parameters);
        }

        return false;
    }

    @Override
    public boolean match(IValue other) {
        if (other instanceof ConstructorWithKeywordParametersFacade) {
            return content.match(((ConstructorWithKeywordParametersFacade) other).content);
        }

        if (other instanceof IConstructor) {
            return IConstructor.super.match(other);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return 131 + 3 * content.hashCode() + 101 * parameters.hashCode();
    }

    @Override
    public boolean mayHaveKeywordParameters() {
        return true;
    }

    @Override
    public IWithKeywordParameters<? extends IConstructor> asWithKeywordParameters() {
        return new AbstractDefaultWithKeywordParameters<IConstructor>(content, parameters) {
            @Override
            protected IConstructor wrap(IConstructor content, io.usethesource.capsule.Map.Immutable<String, IValue> parameters) {
                return new ConstructorWithKeywordParametersFacade(content, parameters);
            }

            @Override
            public boolean hasParameters() {
                return parameters != null && parameters.size() > 0;
            }

            @Override
            @SuppressWarnings("return.type.incompatible")
            public Set<String> getParameterNames() {
                return Collections.unmodifiableSet(parameters.keySet());
            }

            @Override
            @Pure
            public Map<String, IValue> getParameters() {
                return Collections.unmodifiableMap(parameters);
            }
        };
    }

    @Override
    public Type getConstructorType() {
        return content.getConstructorType();
    }

    @Override
    public Type getUninstantiatedConstructorType() {
        return content.getUninstantiatedConstructorType();
    }

    @Override
    public IValue get(String label) {
        return content.get(label);
    }

    @Override
    public IConstructor set(String label, IValue newChild) {
        return new ConstructorWithKeywordParametersFacade(content.set(label, newChild), parameters);
    }

    @Override
    public boolean has(String label) {
        return content.has(label);
    }


    @Override
    public Type getChildrenTypes() {
        return content.getChildrenTypes();
    }
}
