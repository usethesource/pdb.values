/*******************************************************************************
 * Copyright (c) 2009-2013 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:

 *   * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI
 *   * Mark Hills - Mark.Hills@cwi.nl (CWI)
 *   * Arnold Lankamp - Arnold.Lankamp@cwi.nl
 *   * Anastasia Izmaylova - A.Izmaylova@cwi.nl - CWI
*******************************************************************************/
package io.usethesource.vallang.type;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IListWriter;
import io.usethesource.vallang.ISetWriter;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.exceptions.FactTypeUseException;
import io.usethesource.vallang.exceptions.IllegalOperationException;
import io.usethesource.vallang.type.TypeFactory.RandomTypesConfig;
import io.usethesource.vallang.type.TypeFactory.TypeReifier;
import io.usethesource.vallang.type.TypeFactory.TypeValues;

/**
 * Function types are here to prepare for the coming of `IFunction extends IValue` to vallang.
 * They are used by the Rascal run-time system. They are build on top of (implementation details of)
 * tuple types in the interest of reuse and brevity, but there is a significant difference
 * between tuple types and parameter lists. Namely when when of the parameters is of type `void`
 * the argument list does not reduce to `void` itself. Instead the tuple remains with its arity fixed.
 * This is essential and it is managed by avoiding the reduction of tuple types with void fields which is implemented
 * in the TypeFactory. Other interesting details are the subtype and lub implementation of functions,
 * which follow the typing rules of Rascal functions (co and contra-variant in their argument types).
 */
public class FunctionType extends DefaultSubtypeOfValue {
    private final Type returnType;
    private final TupleType argumentTypes;
    private final @Nullable TupleType keywordParameters;

    /*package*/ FunctionType(Type returnType, TupleType argumentTypes, TupleType keywordParameters) {
        this.argumentTypes = argumentTypes;
        this.returnType = returnType;
        this.keywordParameters = keywordParameters == null ? null : (keywordParameters.getArity() == 0 ? null : keywordParameters);
    }

    public static class Reifier extends TypeReifier {
        public Reifier(TypeValues symbols) {
            super(symbols);
        }

        @Override
        public Type getSymbolConstructorType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<Type> getSymbolConstructorTypes() {
            return Arrays.stream(new Type[] {
                    normalFunctionSymbol()
            }).collect(Collectors.toSet());
        }

        private Type normalFunctionSymbol() {
            return symbols().typeSymbolConstructor("func", symbols().symbolADT(), "ret", TF.listType(symbols().symbolADT()), "parameters", TF.listType(symbols().symbolADT()), "kwTypes");
        }

        @Override
        public Type fromSymbol(IConstructor symbol, TypeStore store, Function<IConstructor, Set<IConstructor>> grammar) {
            Type returnType = symbols().fromSymbol((IConstructor) symbol.get("ret"), store, grammar);
            Type parameters = symbols().fromSymbols((IList) symbol.get("parameters"), store, grammar);
            Type kwTypes = symbols().fromSymbols((IList) symbol.get("kwTypes"), store, grammar);

            return TypeFactory.getInstance().functionType(returnType, parameters, kwTypes);
        }

        @Override
        public boolean isRecursive() {
            return true;
        }

        @Override
        public Type randomInstance(Supplier<Type> next, TypeStore store, RandomTypesConfig rnd) {
            // TODO: as we do not have IFunction yet in vallang, we should not generate random
            // function types either. It will lead to exceptions otherwise.
            // return TF.functionType(next.get(), (TupleType) TF.tupleType(next.get()), (TupleType) TF.tupleEmpty());
            return TF.integerType();
        }

        @Override
        public void asProductions(Type type, IValueFactory vf, TypeStore store, ISetWriter grammar,
                Set<IConstructor> done) {
            ((FunctionType) type).getReturnType().asProductions(vf, store, grammar, done);

            for (Type arg : ((FunctionType) type).getFieldTypes()) {
                arg.asProductions(vf, store, grammar, done);
            }
        }

        @Override
        public IConstructor toSymbol(Type type, IValueFactory vf, TypeStore store,  ISetWriter grammar, Set<IConstructor> done) {
            IListWriter w = vf.listWriter();

            int i = 0;
            Type args = ((FunctionType) type).getFieldTypes();
            for (Type arg : args) {
                IConstructor sym = arg.asSymbol(vf, store, grammar, done);
                if (args.hasFieldNames()) {
                    sym = symbols().labelSymbol(vf, sym, args.getFieldName(i));
                }
                i++;
                w.append(sym);
            }

            IListWriter kw = vf.listWriter();

            i = 0;
            Type kwArgs = ((FunctionType) type).getKeywordParameterTypes();
            if (kwArgs != null && !kwArgs.isBottom()) {
                for (Type arg : kwArgs) {
                    IConstructor sym = arg.asSymbol(vf, store, grammar, done);
                    if (kwArgs.hasFieldNames()) {
                        sym = symbols().labelSymbol(vf, sym, kwArgs.getFieldName(i));
                    }
                    i++;
                    kw.append(sym);
                }
            }


            return vf.constructor(normalFunctionSymbol(), ((FunctionType) type).getReturnType().asSymbol(vf, store, grammar, done), w.done(), kw.done());
        }
    }

    @Override
    public TypeReifier getTypeReifier(TypeValues symbols) {
        return new Reifier(symbols);
    }

    @Override
    public boolean isFunction() {
        return true;
    }

    @Override
    public Type getFieldType(int i) {
        return argumentTypes.getFieldType(i);
    }

    @Override
    public Type getFieldType(String fieldName) throws FactTypeUseException {
        return argumentTypes.getFieldType(fieldName);
    }

    @Override
    public int getFieldIndex(String fieldName) {
        return argumentTypes.getFieldIndex(fieldName);
    }

    @Override
    public String getFieldName(int i) {
        return argumentTypes.getFieldName(i);
    }

    @Override
    public String[] getFieldNames() {
        return argumentTypes.getFieldNames();
    }

    @Override
    public Type getFieldTypes() {
        return argumentTypes;
    }

    @Override
    public <T, E extends Throwable> T accept(ITypeVisitor<T, E> visitor) throws E {
        return visitor.visitFunction(this);
    }

    @Override
    public Type getReturnType() {
        return returnType;
    }

    @Override
    public int getArity() {
        return argumentTypes.getArity();
    }

    @Override
    public Type getKeywordParameterTypes() {
        return keywordParameters == null ? TypeFactory.getInstance().tupleEmpty() : keywordParameters;
    }

    @Override
    public @Nullable Type getKeywordParameterType(String label) {
        return keywordParameters != null ? keywordParameters.getFieldType(label) : null;
    }

    @Override
    public boolean hasKeywordParameter(String label) {
        return keywordParameters != null ? keywordParameters.hasField(label) : false;
    }

    @Override
    public boolean hasKeywordParameters() {
        return keywordParameters != null;
    }

    @Override
    protected boolean isSupertypeOf(Type type) {
        return type.isSubtypeOfFunction(this);
    }

    @Override
    public Type lub(Type type) {
        return type.lubWithFunction(this);
    }

    @Override
    public Type glb(Type type) {
        return type.glbWithFunction(this);
    }

    @Override
    public boolean intersects(Type type) {
        return type.intersectsWithFunction(this);
    }

    @Override
    protected boolean intersectsWithFunction(Type other) {
        FunctionType otherType = (FunctionType) other;

        if (other.getArity() != getArity()) {
            return false;
        }

        if (otherType.getReturnType().isBottom() && getReturnType().isBottom()) {
            return otherType.getFieldTypes().intersects(getFieldTypes());
        }

        // TODO should the return type intersect or just be comparable?
        return otherType.getReturnType().intersects(getReturnType())
            && otherType.getFieldTypes().intersects(getFieldTypes());
    }

    @Override
    public boolean isSubtypeOfFunction(Type other) {
        // Vallang functions are co-variant in the return type position and
        // *variant* (co- _and_ contra-variant) in their argument positions, such that a sub-function
        // can safely simulate a super function and in particular overloaded functions may have contributions which
        // do not match the currently requested function type.

        // For example, an overloadeded function `X f(int) + X f(str)` is substitutable at high-order parameter positions of type `X (int)`
        // even though its function type is `X (value)`. Rascal's type system does not check completeness of function definitions,
        // only _possible_ applicability in this manner. Every function may throw `CallFailed` at run-time
        // if non of their aguments match for none of their alternatives.

        FunctionType otherType = (FunctionType) other;

        if (getReturnType().isSubtypeOf(otherType.getReturnType())) {

            Type argTypes = getFieldTypes();
            Type otherArgTypes = otherType.getFieldTypes();

            if (argTypes.getArity() != otherArgTypes.getArity()) {
                return false;
            }

            int N = argTypes.getArity();

            for (int i = 0; i < N; i++) {
                Type field = argTypes.getFieldType(i);
                Type otherField = otherArgTypes.getFieldType(i);

                if (field.isBottom() || otherField.isBottom()) {
                    continue;
                }

                if (!field.intersects(otherField)) {
                    return false;
                }
            }

            return true;

        }

        return false;
    }

    @Override
    protected Type lubWithFunction(Type type) {
        if (this == type) {
            return this;
        }

        FunctionType f = (FunctionType) type;

        Type returnType = getReturnType().lub(f.getReturnType());
        Type argumentTypes = getFieldTypes().lub(f.getFieldTypes());

        if (argumentTypes.isTuple() && argumentTypes.getArity() == getArity()) {
            return TypeFactory.getInstance().functionType(returnType,
                argumentTypes,
                getKeywordParameterTypes() == f.getKeywordParameterTypes()
                    ? getKeywordParameterTypes()
                    : TF.tupleEmpty());
        }

        return TF.valueType();
    }

    @Override
    protected Type glbWithFunction(Type type) {
        if (this == type) {
            return this;
        }

        FunctionType f = (FunctionType) type;

        Type returnType = getReturnType().glb(f.getReturnType());
        Type argumentTypes = getFieldTypes().lub(f.getFieldTypes());

        if (argumentTypes.isTuple()) {
            // TODO: figure out what glb means for keyword parameters
            return TF.functionType(returnType, argumentTypes, TF.tupleEmpty());
        }

        return TF.voidType();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(returnType);
        sb.append(' ');
        sb.append('(');
        int i = 0;
        for (Type arg : argumentTypes) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(arg.toString());
            if (argumentTypes.hasFieldNames()) {
                sb.append(" " + argumentTypes.getFieldName(i));
            }

            i++;
        }

        if (keywordParameters != null) {
            i = 0;
            for (Type arg : keywordParameters) {
                sb.append(", ");
                sb.append(arg.toString());
                if (keywordParameters.hasFieldNames()) {
                    sb.append(" " + keywordParameters.getFieldName(i) + " = ...");
                }

                i++;
            }
        }
        sb.append(')');
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return 19 + 19 * returnType.hashCode() + 23 * argumentTypes.hashCode()
                + (keywordParameters != null ? 29 * keywordParameters.hashCode() : 0)
                ;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null) {
            return false;
        }

        if (o == this) {
            return true;
        }

        if (o instanceof FunctionType) {
            FunctionType other = (FunctionType) o;

            if (returnType != other.returnType) {
                return false;
            }

            if (argumentTypes != other.argumentTypes) {
                return false;
            }

            if (keywordParameters != other.keywordParameters) {
                return false;
            }

            return true;
        }

        return false;
    }

    @Override
    public Type instantiate(Map<Type, Type> bindings) {
        return TF.functionType(returnType.instantiate(bindings), instantiateTuple(argumentTypes, bindings), keywordParameters != null ? instantiateTuple(keywordParameters, bindings) : TypeFactory.getInstance().tupleEmpty());
    }

    @Override
    public boolean isOpen() {
        return returnType.isOpen() || argumentTypes.isOpen();
    }

    @Override
    public boolean match(Type matched, Map<Type, Type> bindings)
            throws FactTypeUseException {
        if (matched.isBottom()) {
            return argumentTypes.match(matched, bindings) && returnType.match(matched, bindings);
        } else {
            // Fix for cases where we have aliases to function types, aliases to aliases to function types, etc
            while (matched.isAliased()) {
                matched = matched.getAliased();
            }

            if (matched.isFunction()) {
                FunctionType matchedFunction = (FunctionType) matched;

                if (argumentTypes.getArity() != matchedFunction.getArity()) {
                    return false;
                }

                for (int i = argumentTypes.getArity() - 1; i >= 0; i--) {
                    Type fieldType = argumentTypes.getFieldType(i);
                    Type otherFieldType = matchedFunction.getFieldTypes().getFieldType(i);
                    Map<Type, Type> originalBindings = new HashMap<>();
                    originalBindings.putAll(bindings);

                    if (!fieldType.match(otherFieldType, bindings)) {
                        bindings = originalBindings;
                        if (!otherFieldType.match(matchedFunction, bindings)) {
                            // neither co nor contra-variant
                            return false;
                        }
                    }
                }

                return returnType.match(matchedFunction.getReturnType(), bindings);
            }
            else {
                return false;
            }
        }
    }

    @Override
    public Type compose(Type right) {
        if (right.isBottom()) {
            return right;
        }

        if (right.isFunction()) {
            if (TF.tupleType(right.getReturnType()).isSubtypeOf(this.argumentTypes)) {
                return TF.functionType(this.returnType, right.getFieldTypes(), right.getKeywordParameterTypes());
            }
        }  else {
            throw new IllegalOperationException("compose", this, right);
        }

        return TF.voidType();
    }

    @Override
    public IValue randomValue(Random random, IValueFactory vf, TypeStore store, Map<Type, Type> typeParameters,
            int maxDepth, int maxBreadth) {
        throw new RuntimeException("randomValue on FunctionType not yet implemented");
    }
}
