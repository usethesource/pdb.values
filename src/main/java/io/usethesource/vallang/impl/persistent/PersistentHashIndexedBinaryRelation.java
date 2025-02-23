/*******************************************************************************
 * Copyright (c) 2013-2014 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *
 *   * Michael Steindorfer Michael.Steindorfer@cwi.nl CWI
 *******************************************************************************/
package io.usethesource.vallang.impl.persistent;

import static io.usethesource.vallang.impl.persistent.SetWriter.USE_MULTIMAP_BINARY_RELATIONS;
import static io.usethesource.vallang.impl.persistent.SetWriter.asInstanceOf;
import static io.usethesource.vallang.impl.persistent.SetWriter.isTupleOfArityTwo;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import io.usethesource.capsule.Set;
import io.usethesource.capsule.Set.Immutable;
import io.usethesource.capsule.SetMultimap;
import io.usethesource.capsule.core.PersistentTrieSetMultimap;
import io.usethesource.capsule.util.ArrayUtilsInt;
import io.usethesource.capsule.util.stream.CapsuleCollectors;
import io.usethesource.vallang.IRelation;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISetWriter;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.util.AbstractTypeBag;

/**
* Implements both ISet and IRelation, by indexing on the first column
* and reconstructing binary tuples while iterating. This class
* is faster for compose and closure because the index has been pre-computed.
*/
public final class PersistentHashIndexedBinaryRelation implements ISet, IRelation<ISet> {
    private @MonotonicNonNull Type cachedRelationType;
    private final AbstractTypeBag keyTypeBag;
    private final AbstractTypeBag valTypeBag;
    private final SetMultimap.Immutable<IValue, IValue> content;

    /**
    * Construction of persistent indexed binary relation with multi-map backend.
    *
    * DO NOT CALL OUTSIDE OF {@link PersistentSetFactory}.
    *
    * @param keyTypeBag precise dynamic type of first data column
    * @param valTypeBag precise dynamic type of second data column
    * @param content immutable multi-map
    */
    PersistentHashIndexedBinaryRelation(AbstractTypeBag keyTypeBag, AbstractTypeBag valTypeBag,
        SetMultimap.Immutable<IValue, IValue> content) {
        this.keyTypeBag = Objects.requireNonNull(keyTypeBag);
        this.valTypeBag = Objects.requireNonNull(valTypeBag);
        this.content = Objects.requireNonNull(content);

        assert USE_MULTIMAP_BINARY_RELATIONS
        && isTupleOfArityTwo.test(TF.tupleType(keyTypeBag.lub(), valTypeBag.lub()));
        assert USE_MULTIMAP_BINARY_RELATIONS && !content.isEmpty();
        assert USE_MULTIMAP_BINARY_RELATIONS && checkDynamicType(keyTypeBag, valTypeBag, content);
    }

    @Override
    public IRelation<ISet> asRelation() {
        return this;
    }

    private static final boolean checkDynamicType(final AbstractTypeBag keyTypeBag,
        final AbstractTypeBag valTypeBag, final SetMultimap.Immutable<IValue, IValue> content) {


        final AbstractTypeBag expectedKeyTypeBag = calcTypeBag(content, Map.Entry::getKey);
        final AbstractTypeBag expectedValTypeBag = calcTypeBag(content, Map.Entry::getValue);

        boolean keyTypesEqual = expectedKeyTypeBag.equals(keyTypeBag);
        boolean valTypesEqual = expectedValTypeBag.equals(valTypeBag);

        return keyTypesEqual && valTypesEqual;
    }

    @Override
    public ISetWriter writer() {
        return ValueFactory.getInstance().setWriter();
    }

    @Override
    public Type getType() {
        if (cachedRelationType == null) {
            cachedRelationType = TF.relType(keyTypeBag.lub(), valTypeBag.lub());
        }
        return cachedRelationType;
    }

    private final <K extends IValue, V extends IValue> BiFunction<IValue, IValue, ITuple> tupleConverter() {
        return (first, second) -> Tuple.newTuple(first, second);
    }

    @Override
    public boolean isEmpty() {
        return content.isEmpty();
    }

    @Override
    public ISet insert(IValue value) {
        if (!isTupleOfArityTwo.test(value.getType())) {
            /*
            * NOTE: conversion of set representations are assumed to be scarce, but are costly if they
            * happen though.
            */
            final Stream<ITuple> tupleStream = content.tupleStream(tupleConverter());
            return Stream.concat(tupleStream, Stream.of(value)).collect(ValueCollectors.toSet());
        }

        final ITuple tuple = (ITuple) value;
        final IValue key = tuple.get(0);
        final IValue val = tuple.get(1);

        final SetMultimap.Immutable<IValue, IValue> contentNew = content.__insert(key, val);

        if (content == contentNew) {
            return this;
        }

        final AbstractTypeBag keyTypeBagNew = keyTypeBag.increase(key.getType());
        final AbstractTypeBag valTypeBagNew = valTypeBag.increase(val.getType());

        return PersistentSetFactory.from(keyTypeBagNew, valTypeBagNew, contentNew);
    }

    @Override
    public ISet delete(IValue value) {
        if (!isTupleOfArityTwo.test(value.getType())) {
            return this;
        }

        final ITuple tuple = (ITuple) value;
        final IValue key = tuple.get(0);
        final IValue val = tuple.get(1);

        final SetMultimap.Immutable<IValue, IValue> contentNew = content.__remove(key, val);

        if (content == contentNew) {
            return this;
        }

        final AbstractTypeBag keyTypeBagNew = keyTypeBag.decrease(key.getType());
        final AbstractTypeBag valTypeBagNew = valTypeBag.decrease(val.getType());

        return PersistentSetFactory.from(keyTypeBagNew, valTypeBagNew, contentNew);
    }

    @Override
    public int size() {
        return content.size();
    }

    @Override
    public boolean contains(IValue value) {
        if (!isTupleOfArityTwo.test(value.getType())) {
            return false;
        }

        final ITuple tuple = (ITuple) value;
        final IValue key = tuple.get(0);
        final IValue val = tuple.get(1);

        return content.containsEntry(key, val);
    }

    @Override
    public Iterator<IValue> iterator() {
        return content.tupleIterator(Tuple::newTuple);
    }

    @Override
    public int hashCode() {
        final int hashCode =
            StreamSupport.stream(spliterator(), false).mapToInt(tuple -> tuple.hashCode()).sum();

        return hashCode;
    }

    @Override
    public String toString() {
        return defaultToString();
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (other == this) {
            return true;
        }

        if (other == null) {
            return false;
        }

        if (other instanceof PersistentHashIndexedBinaryRelation) {
            PersistentHashIndexedBinaryRelation that = (PersistentHashIndexedBinaryRelation) other;

            if (this.getType() != that.getType()) {
                return false;
            }

            if (this.size() != that.size()) {
                return false;
            }

            return content.equals(that.content);
        }

        if (other instanceof ISet) {
            return defaultEquals(other);
        }

        return false;
    }

    @Override
    public ISet union(ISet other) {
        if (other == this) {
            return this;
        }
        if (other == null) {
            return this;
        }

        if (other instanceof PersistentHashIndexedBinaryRelation) {
            PersistentHashIndexedBinaryRelation that = (PersistentHashIndexedBinaryRelation) other;

            final SetMultimap.Immutable<IValue, IValue> one;
            final SetMultimap.Immutable<IValue, IValue> two;
            AbstractTypeBag keyTypeBagNew;
            AbstractTypeBag valTypeBagNew;
            final ISet def;

            if (that.size() >= this.size()) {
                def = that;
                one = that.content;
                keyTypeBagNew = that.keyTypeBag;
                valTypeBagNew = that.valTypeBag;
                two = this.content;
            } else {
                def = this;
                one = this.content;
                keyTypeBagNew = this.keyTypeBag;
                valTypeBagNew = this.valTypeBag;
                two = that.content;
            }

            final SetMultimap.Transient<IValue, IValue> tmp = one.asTransient();
            boolean modified = false;

            for (Map.Entry<IValue, IValue> entry : two.entrySet()) {
                final IValue key = entry.getKey();
                final IValue val = entry.getValue();

                if (tmp.__insert(key, val)) {
                    modified = true;
                    keyTypeBagNew = keyTypeBagNew.increase(key.getType());
                    valTypeBagNew = valTypeBagNew.increase(val.getType());
                }
            }

            if (modified) {
                return PersistentSetFactory.from(keyTypeBagNew, valTypeBagNew, tmp.freeze());
            }
            return def;
        } else {
            return ISet.super.union(other);
        }
    }

    @Override
    public ISet intersect(ISet other) {
        if (other == this) {
            return this;
        }
        if (other == null) {
            return EmptySet.EMPTY_SET;
        }

        if (other instanceof PersistentHashIndexedBinaryRelation) {
            PersistentHashIndexedBinaryRelation that = (PersistentHashIndexedBinaryRelation) other;

            final SetMultimap.Immutable<IValue, IValue> one;
            final SetMultimap.Immutable<IValue, IValue> two;
            AbstractTypeBag keyTypeBagNew;
            AbstractTypeBag valTypeBagNew;
            final ISet def;

            if (that.size() >= this.size()) {
                def = this;
                one = this.content;
                keyTypeBagNew = this.keyTypeBag;
                valTypeBagNew = this.valTypeBag;
                two = that.content;
            } else {
                def = that;
                one = that.content;
                keyTypeBagNew = that.keyTypeBag;
                valTypeBagNew = that.valTypeBag;
                two = this.content;
            }

            final SetMultimap.Transient<IValue, IValue> tmp = one.asTransient();
            boolean modified = false;

            for (Iterator<Map.Entry<IValue, IValue>> it = tmp.entryIterator(); it.hasNext(); ) {
                final Map.Entry<IValue, IValue> tuple = it.next();
                final IValue key = tuple.getKey();
                final IValue val = tuple.getValue();

                if (!two.containsEntry(key, val)) {
                    it.remove();
                    modified = true;
                    keyTypeBagNew = keyTypeBagNew.decrease(key.getType());
                    valTypeBagNew = valTypeBagNew.decrease(val.getType());
                }
            }

            if (modified) {
                return PersistentSetFactory.from(keyTypeBagNew, valTypeBagNew, tmp.freeze());
            }
            return def;
        } else {
            return ISet.super.intersect(other);
        }
    }

    @Override
    public ISet subtract(ISet other) {
        if (other == this) {
            return EmptySet.EMPTY_SET;
        }
        if (other == null) {
            return this;
        }

        if (other instanceof PersistentHashIndexedBinaryRelation) {
            PersistentHashIndexedBinaryRelation that = (PersistentHashIndexedBinaryRelation) other;

            final SetMultimap.Immutable<IValue, IValue> one;
            final SetMultimap.Immutable<IValue, IValue> two;
            AbstractTypeBag keyTypeBagNew;
            AbstractTypeBag valTypeBagNew;
            final ISet def;

            def = this;
            one = this.content;
            keyTypeBagNew = this.keyTypeBag;
            valTypeBagNew = this.valTypeBag;
            two = that.content;

            final SetMultimap.Transient<IValue, IValue> tmp = one.asTransient();
            boolean modified = false;

            for (Map.Entry<IValue, IValue> tuple : two.entrySet()) {
                final IValue key = tuple.getKey();
                final IValue val = tuple.getValue();

                if (tmp.__remove(key, val)) {
                    modified = true;
                    keyTypeBagNew = keyTypeBagNew.decrease(key.getType());
                    valTypeBagNew = valTypeBagNew.decrease(val.getType());
                }
            }

            if (modified) {
                return PersistentSetFactory.from(keyTypeBagNew, valTypeBagNew, tmp.freeze());
            }
            return def;
        } else {
            return ISet.super.subtract(other);
        }
    }

    @Override
    public ISet compose(IRelation<ISet> otherSetRelation) {
        if (otherSetRelation.getClass() != this.getClass()) {
            return IRelation.super.compose(otherSetRelation);
        }

        // Here we can optimize the compose operation because we have already an index in memory
        // for both relations

        final PersistentHashIndexedBinaryRelation thatSet =
            (PersistentHashIndexedBinaryRelation) otherSetRelation.asContainer();

        final SetMultimap.Immutable<IValue, IValue> xy = content;
        final SetMultimap.Immutable<IValue, IValue> yz = thatSet.content;

        /**
        * The code below is still sub-optimal because it operates on the logical (rather than the structural) level.
        *
        * TODO: nodes should get proper support for stream processing such that the following template can be used:
        *
        *    // @formatter:off
        *    final Stream<BiConsumer<IValue, IValue>> localStream = null;
        *    final Node updatedNode = localStream
        *    .filter((x, y) -> yz.containsKey(y))
        *    .mapValues(y -> yz.get(y))
        *    .collect(toNode());
        *    // @formatter:on
        */
        final SetMultimap.Transient<IValue, IValue> xz = xy.asTransient();

        for (IValue x : xy.keySet()) {
            final Set.Immutable<IValue> ys = xy.get(x);
            // TODO: simplify expression with nullable data
            final Set.Immutable<IValue> zs = ys.stream()
                .flatMap(y -> Optional.ofNullable(yz.get(y)).orElseGet(Set.Immutable::of).stream())
                .collect(CapsuleCollectors.toSet());

            if (zs == null) {
                xz.__remove(x);
            } else {
                // xz.__put(x, zs); // TODO: requires node batch update support

                xz.__remove(x);
                zs.forEach(z -> xz.__insert(x, z));
            }
        }

        final SetMultimap.Immutable<IValue, IValue> data = xz.freeze();


        final AbstractTypeBag keyTypeBag = calcTypeBag(data, Map.Entry::getKey);
        final AbstractTypeBag valTypeBag = calcTypeBag(data, Map.Entry::getValue);

        return PersistentSetFactory.from(keyTypeBag, valTypeBag, data);
    }

    @Override
    public int arity() {
        return 2;
    }

    @Override
    public ISet project(int... fieldIndexes) {
        if (Arrays.equals(fieldIndexes, ArrayUtilsInt.arrayOfInt(0))) {
            return domain();
        }

        if (Arrays.equals(fieldIndexes, ArrayUtilsInt.arrayOfInt(1))) {
            return range();
        }

        if (Arrays.equals(fieldIndexes, ArrayUtilsInt.arrayOfInt(0, 1))) {
            return this;
        }

        // TODO: replace by `inverse` API of subsequent capsule release
        if (Arrays.equals(fieldIndexes, ArrayUtilsInt.arrayOfInt(1, 0))) {
            final SetMultimap.Transient<IValue, IValue> builder =
                PersistentTrieSetMultimap.transientOf(Object::equals);

            content.entryIterator().forEachRemaining(
                tuple -> builder.__insert(tuple.getValue(), tuple.getKey()));


            return PersistentSetFactory.from(valTypeBag, keyTypeBag, builder.freeze());
        }

        throw new IllegalStateException("Binary relation patterns exhausted.");
    }

    /**
    * Flattening Set[Tuple[Tuple[K, V]], _] to Multimap[K, V].
    *
    * @return canonical set of keys
    */
    @Override
    public ISet domain() {
        final Type fieldType0 = this.getType().getFieldType(0);

        if (isTupleOfArityTwo.test(fieldType0)) {
            // TODO: use lazy keySet view instead of materialized data structure
            return this.content.keySet().stream().map(asInstanceOf(ITuple.class))
            .collect(ValueCollectors.toSetMultimap(tuple -> tuple.get(0), tuple -> tuple.get(1)));
        }

        /**
        * NOTE: the following call to {@code stream().collect(toSet())} is suboptimal because
        * {@code thisSet.content.keySet()} already produces the result (modulo dynamic types). The
        * usage of streams solely hides the calculation of precise dynamic type of the set.
        */
        return this.content.keySet().stream().collect(ValueCollectors.toSet());
    }

    /**
    * Flattening Set[Tuple[_, Tuple[K, V]]] to Multimap[K, V].
    *
    * @return canonical set of values
    */
    @Override
    public ISet range() {
        return content.values().stream().collect(ValueCollectors.toSet());
    }

    @Override
    public ISet index(IValue key) {
        Immutable<IValue> values = content.get(key);
        if (values == null) {
            return EmptySet.EMPTY_SET;
        }

        return PersistentSetFactory.from(values);
    }

    @Override
    public ISet asContainer() {
        return this;
    }

    @Override
    public Type getElementType() {
        return ISet.super.getElementType();
    }

    @Override
    public ISet empty() {
        return ISet.super.empty();
    }

    private static AbstractTypeBag calcTypeBag(SetMultimap<IValue, IValue> contents, Function<Map.Entry<IValue, IValue>, IValue> mapper) {
        return contents.entrySet().stream().map(mapper)
        .map(IValue::getType).collect(AbstractTypeBag.toTypeBag());
    }

    @Override
    public ISet closure() {
        Type tupleType = getElementType();
        assert tupleType.getArity() == 2;
        Type keyType = tupleType.getFieldType(0);
        Type valueType = tupleType.getFieldType(1);

        if (!keyType.comparable(valueType)) {
            // if someone tries, then we have a very quick answer
            return this;
        }

        var result = computeClosure(content);

        final AbstractTypeBag keyTypeBag;
        final AbstractTypeBag valTypeBag;

        if (keyType == valueType && isConcreteValueType(keyType)) {
            // this means no other types can be introduced other than the originals,
            // so iteration is no longer necessary to construct the new type bag
            keyTypeBag = AbstractTypeBag.of(keyType, result.size());
            valTypeBag = keyTypeBag;
        }
        else {
            keyTypeBag = calcTypeBag(result, Map.Entry::getKey);
            valTypeBag = calcTypeBag(result, Map.Entry::getValue);
        }

        return PersistentSetFactory.from(keyTypeBag, valTypeBag, result.freeze());
    }

    private boolean isConcreteValueType(Type keyType) {
        return keyType.isSourceLocation()
            || keyType.isInteger()
            || keyType.isRational()
            || keyType.isReal()
            || keyType.isDateTime()
            || (keyType.isAbstractData() && !keyType.isParameterized())
            || keyType.isString()
            || keyType.isBool()
            ;
    }

    @Override
    public ISet closureStar() {
        Type tupleType = getElementType();
        assert tupleType.getArity() == 2;
        Type keyType = tupleType.getFieldType(0);
        Type valueType = tupleType.getFieldType(1);

        var result = computeClosure(content);

        for (var carrier: content.entrySet()) {
            result.__insert(carrier.getKey(), carrier.getKey());
            result.__insert(carrier.getValue(), carrier.getValue());
        }

        final AbstractTypeBag keyTypeBag;
        final AbstractTypeBag valTypeBag;

        if (keyType == valueType && isConcreteValueType(keyType)) {
            // this means no other types can be introduced other than the originals,
            // so iteration is no longer necessary to construct the new type bag
            keyTypeBag = AbstractTypeBag.of(keyType, result.size());
            valTypeBag = keyTypeBag;
        }
        else {
            keyTypeBag = calcTypeBag(result, Map.Entry::getKey);
            valTypeBag = calcTypeBag(result, Map.Entry::getValue);
        }

        return PersistentSetFactory.from(keyTypeBag, valTypeBag, result.freeze());
    }

    private static SetMultimap.Transient<IValue, IValue> computeClosure(final SetMultimap.Immutable<IValue, IValue> content) {
        return content.size() > 256
            ? computeClosureDepthFirst(content)
            : computeClosureBreadthFirst(content)
            ;
    }

    @SuppressWarnings("unchecked")
    private static SetMultimap.Transient<IValue, IValue> computeClosureDepthFirst(final SetMultimap.Immutable<IValue, IValue> content) {
        final SetMultimap.Transient<IValue, IValue> result = content.asTransient();
        var todo = new ArrayDeque<IValue>();
        var done = new HashSet<IValue>(); // keep track of LHS we already did, so we don't have to go into the depth of them anymore
        var mainIt = content.nativeEntryIterator();
        while (mainIt.hasNext()) {
            final var focus = mainIt.next();
            final IValue lhs = focus.getKey();
            final Object values =focus.getValue();

            assert todo.isEmpty();
            if (values instanceof IValue) {
                todo.push((IValue)values);
            }
            else if (values instanceof Set) {
                todo.addAll((Set<IValue>)values);
            }
            else {
                throw new IllegalArgumentException("Unexpected map entry");
            }
            // to avoid recalculating `lhs` next time we see it, we mark it as done.
            // so that the next time we come across if on the rhs, we know the range is
            // already the transitive closure.
            // We add it before we've done it, just to avoid scheduling
            // <a,a> when it occurs during the depth scan for lhs.
            done.add(lhs);
            IValue rhs;
            while ((rhs = todo.poll()) != null) {
                if (lhs == rhs) {
                    // no need to handle <a,a>
                    continue;
                }
                boolean rhsDone = done.contains(rhs);
                for (IValue composed : result.get(rhs)) {
                    if (result.__insert(lhs, composed) && !rhsDone) {
                        todo.push(composed);
                    }
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static SetMultimap.Transient<IValue, IValue> computeClosureBreadthFirst(final SetMultimap.Immutable<IValue, IValue> content) {
        /*
        * we want to compute the closure of R, which in essence is a composition on itself.
        * until nothing changes:
        *
        * solve(R) {
        *    R = R o R;
        * }
        *
        * The algorithm below realizes the following things:
        *
        * - Instead of recomputing the compose for the whole of R, we only have to
        *   compose for the newly added edges (called todo in the algorithm).
        * - Since the LHS of `R o R` will be using the range of R as a lookup in R
        *   we store the todo in inverse.
        *
        * In essence the algorithm becomes:
        *
        * result = R;
        * todo = invert(R);
        *
        * while (todo != {}) {
        *  composed = fastCompose(todo, R);
        *  newEdges = composed - result;
        *  todo = invert(newEdges);
        *  result += newEdges;
        * }
        *
        * fastCompose(todo, R) = { l * R[r] | <r, l> <- todo};
        *
        */
        final SetMultimap.Transient<IValue, IValue> result = content.asTransient();

        SetMultimap<IValue, IValue> todo = content.inverseMap();
        while (!todo.isEmpty()) {
            final SetMultimap.Transient<IValue,IValue> nextTodo = PersistentTrieSetMultimap.transientOf(Object::equals);

            var todoIt = todo.nativeEntryIterator();
            while (todoIt.hasNext()) {
                var next = todoIt.next();
                IValue lhs = next.getKey();
                Immutable<IValue> values = content.get(lhs);
                if (!values.isEmpty()) {
                    Object keys = next.getValue();
                    if (keys instanceof IValue) {
                        singleCompose(result, nextTodo, values, (IValue)keys);
                    }
                    else if (keys instanceof Set) {
                        for (IValue key : (Set<IValue>)keys) {
                            singleCompose(result, nextTodo, values, key);
                        }
                    }
                    else {
                        throw new IllegalArgumentException("Unexpected map entry");
                    }
                }
            }

            todo = nextTodo;
        }

        return result;
    }

    private static void singleCompose(final SetMultimap.Transient<IValue, IValue> result,
        final SetMultimap.Transient<IValue, IValue> nextTodo, Immutable<IValue> values, IValue key) {
        for (IValue val: values) {
            if (result.__insert(key, val)) {
                nextTodo.__insert(val, key);
            }
        }
    }

}
