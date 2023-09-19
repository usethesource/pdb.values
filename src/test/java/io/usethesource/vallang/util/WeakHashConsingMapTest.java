/** 
 * Copyright (c) 2017, Davy Landman, SWAT.engineering
 * All rights reserved. 
 *  
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met: 
 *  
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer. 
 *  
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution. 
 *  
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */ 
package io.usethesource.vallang.util;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

public class WeakHashConsingMapTest {
    private static class ThreadCount_CollisionCount_TestMapProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
            int[] threads = new int[] { 1, 8 };
            int[] collisions = new int[] { 1, 4 };
            return Arrays.stream(threads).boxed().<Arguments>flatMap(thr -> 
                     Arrays.stream(collisions).boxed().<Arguments>flatMap(col -> 
                        Stream.of(
                            Arguments.of(thr, col, new WeakReferenceHashConsingMap<FixedHashEquals>(16, 1)),
                            Arguments.of(thr, col, new WeakReferenceHashConsingBuffer<FixedHashEquals>(16)))
                     )
                   );
        }
    }
    
    /**
     * Class that allows fine grained control on the equality contract and makes it easier to control hash collisions
     */
    private static final class FixedHashEquals {
        private final int hash;
        private final int equals;

        public FixedHashEquals(int hash, int equals) {
            this.hash = hash;
            this.equals = equals;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj instanceof FixedHashEquals) {
                return ((FixedHashEquals)obj).hash == hash && ((FixedHashEquals)obj).equals == equals;
            }
            return false;
        }

        @Override
        public String toString() {
            return "" + hash + ":" + equals + "@" + System.identityHashCode(this);
        }

        @Override
        protected FixedHashEquals clone() {
            return new FixedHashEquals(hash, equals);
        }

    }

    @ParameterizedTest @ArgumentsSource(ThreadCount_CollisionCount_TestMapProvider.class)
    public void restoreSameReference(int threads, int collisions, HashConsingMap<FixedHashEquals> target) {
        FixedHashEquals a = new FixedHashEquals(1,1);
        assertSame(a, target.get(a));
        assertSame(a, target.get(a));
    }

    @ParameterizedTest @ArgumentsSource(ThreadCount_CollisionCount_TestMapProvider.class)
    public void restoreDifferentReference(int threads, int collisions, HashConsingMap<FixedHashEquals> target) {
        FixedHashEquals a = new FixedHashEquals(1,1);
        FixedHashEquals b = new FixedHashEquals(1,2);
        assertSame(a, target.get(a));
        assertSame(b, target.get(b));

        assertSame(a, target.get(a));
    }

    @ParameterizedTest @ArgumentsSource(ThreadCount_CollisionCount_TestMapProvider.class)
    public void restoreOldReference(int threads, int collisions, HashConsingMap<FixedHashEquals> target) {
        FixedHashEquals a = new FixedHashEquals(1,1);
        FixedHashEquals b = new FixedHashEquals(1,1);
        assertSame(a, target.get(a));
        assertSame(a, target.get(b));
        assertSame(a, target.get(a));
    }

    @ParameterizedTest @ArgumentsSource(ThreadCount_CollisionCount_TestMapProvider.class)
    public void looseReference(int threads, int collisions, HashConsingMap<FixedHashEquals> target) throws InterruptedException {
        FixedHashEquals a = new FixedHashEquals(1,1);
        assertSame(a, target.get(a));
        if (target instanceof WeakReferenceHashConsingBuffer) {
            for (int i = 0; i <= 16; i++) {
                target.get(new FixedHashEquals(i + 2, i +3));
            }
        }
        WeakReference<FixedHashEquals> ref = new WeakReference<>(a);
        a = null;
        System.gc();
        Thread.sleep(1100); // wait for the GC to finish and the cleanup to have been run
        while (ref.get() != null) {
        	System.gc();
        	Thread.sleep(100); // wait for the GC to finish and the cleanup to have been run
        }

        // a new reference, the target should not have kept the old reference
        a = new FixedHashEquals(1,1);
        assertSame(a, target.get(a));
    }


    @ParameterizedTest @ArgumentsSource(ThreadCount_CollisionCount_TestMapProvider.class)
    public void storeManyObjects(int threads, int collisions, HashConsingMap<FixedHashEquals> target) {
        List<FixedHashEquals> objects = new ArrayList<>();
        for (int i = 0; i < 1024*1024; i ++) {
            objects.add(new FixedHashEquals(i, i));
        }

        // store them 
        for (FixedHashEquals o : objects) {
            assertSame(o, target.get(o));
        }

        // and then again, to see that they are all in there
        for (FixedHashEquals o : objects) {
            assertSame(o, target.get(o));
        }
    }

    private static int weakenHash(int hash, int maxEntries) {
        return hash % maxEntries;
    }

    @ParameterizedTest @ArgumentsSource(ThreadCount_CollisionCount_TestMapProvider.class)
    public void storeManyObjectsAndLooseThem(int threads, int collisions, HashConsingMap<FixedHashEquals> target) throws CloneNotSupportedException, InterruptedException {
        FixedHashEquals[] objects = createTestObjects(1024*1024, collisions);

        // store them 
        for (FixedHashEquals o : objects) {
            assertSame(o, target.get(o));
        }


        var oldObject = new WeakReference<>(objects[0]);
        // clone a new value
        FixedHashEquals a = (FixedHashEquals) objects[0].clone();
        // loose  them
        Arrays.fill(objects, null);
        objects = null;
        System.gc();
        Thread.sleep(2100);
        while (oldObject.get() != null) {
            // wait for the object to get cleared
            System.gc();
            Thread.sleep(100);
        }

        // check if the clone won't return the old reference
        assertSame(a, target.get(a));
    }


    @ParameterizedTest @ArgumentsSource(ThreadCount_CollisionCount_TestMapProvider.class)
    public void storeManyObjectsAndQueryThem(int threads, int collisions, HashConsingMap<FixedHashEquals> target) throws InterruptedException, CloneNotSupportedException {
        FixedHashEquals[] objects = createTestObjects(1024*1024, collisions);

        // store them 
        for (FixedHashEquals o : objects) {
            assertSame(o, target.get(o));
        }

        // look up with a clone of them
        for (int i = 0; i < objects.length; i ++) {
            assertSame(objects[i],  target.get((FixedHashEquals) objects[i].clone()));
        }
    }

    private FixedHashEquals[] createTestObjects(int size, int collisions) {
        FixedHashEquals[] objects = new FixedHashEquals[size];
        for (int i = 0; i < size; i ++) {
            objects[i] = new FixedHashEquals(weakenHash(i, size / collisions), i);
        }
        return objects;
    }


    @ParameterizedTest @ArgumentsSource(ThreadCount_CollisionCount_TestMapProvider.class)
    public void clearMostAndQueryRest(int threads, int collisions, HashConsingMap<FixedHashEquals> target) throws InterruptedException, CloneNotSupportedException {
        FixedHashEquals[] objects = createTestObjects(1024*1024, collisions);

        // store them 
        for (FixedHashEquals o : objects) {
            assertSame(o, target.get(o));
        }

        for (int i=0; i < objects.length - 10; i++) {
            objects[i] = null;
        }
        System.gc();
        // wait for GC and reference queue to finish
        Thread.sleep(1000);

        for (int i=objects.length - 10; i < objects.length; i++) {
            assertSame(objects[i],  target.get((FixedHashEquals) objects[i].clone()));
        }
    }

    private static class Tuple<X, Y> { 
        public final X x; 
        public final Y y; 
        public Tuple(X x, Y y) { 
            this.x = x; 
            this.y = y; 
        } 
    } 

    /**
     * Test many concurrent threads putting in the same object, and getting the same one out of there
     */
    @ParameterizedTest @ArgumentsSource(ThreadCount_CollisionCount_TestMapProvider.class)
    public void multithreadedAccess(int threads, int collisions, HashConsingMap<FixedHashEquals> target) throws InterruptedException, BrokenBarrierException {
        FixedHashEquals[] objects = createTestObjects(64*1024, collisions);
        CyclicBarrier startRunning = new CyclicBarrier(threads + 1);
        CyclicBarrier startQuerying = new CyclicBarrier(threads);
        Semaphore doneRunning = new Semaphore(0);
        ConcurrentLinkedDeque<Tuple<FixedHashEquals, FixedHashEquals>> failures = new ConcurrentLinkedDeque<>();

        for (int i = 0; i < threads; i++) {
            List<FixedHashEquals> objects2 =  new ArrayList<>(objects.length);
            for (FixedHashEquals o: objects) {
                objects2.add(o);
            }

            new Thread(() -> {
                try {
                    Collections.shuffle(objects2);
                    startRunning.await();
                    for (FixedHashEquals o : objects2) {
                        FixedHashEquals result = target.get(o);
                        if (o != result) {
                            failures.push(new Tuple<>(o, result));
                        }
                    }
                    objects2.removeIf(h -> h.hash > 30);
                    System.gc();
                    Thread.sleep(1100);

                    startQuerying.await();
                    for (FixedHashEquals o : objects2) {
                        FixedHashEquals result = target.get(o.clone());
                        if (o != result) {
                            failures.push(new Tuple<>(o, result));
                        }
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                }
                finally {
                    doneRunning.release();
                }
            }).start();
        }

        Arrays.fill(objects, null);
        objects = null;
        startRunning.await();

        doneRunning.acquire(threads);
        if (!failures.isEmpty()) {
            Tuple<FixedHashEquals, FixedHashEquals> first = failures.pop();
            assertSame(first.x, first.y);
        }
    }

    private static final int TEST_SIZE = 16 << 8; // base size is 16, so test 8 resizes

    /**
     * Generate enough data to trigger at least 10 resize events. Then have multiple threads all inserting every object exacty once.
     * 
     * Then query the cache from all threads to see if they are all in there (but take a clone of the object, to make sure we are getting the one we expected, and not the one we just passed in
     */
    @ParameterizedTest @ArgumentsSource(ThreadCount_CollisionCount_TestMapProvider.class)
    public void multithreadedNothingIsLostDuringResizes(int threads, int collisions, HashConsingMap<FixedHashEquals> target) throws InterruptedException, BrokenBarrierException {
        CyclicBarrier startRunning = new CyclicBarrier(threads + 1);
        CyclicBarrier startQuerying = new CyclicBarrier(threads);
        Semaphore doneRunning = new Semaphore(0);
        ConcurrentLinkedDeque<Tuple<FixedHashEquals, FixedHashEquals>> failures = new ConcurrentLinkedDeque<>();

        List<FixedHashEquals> objects =  new ArrayList<>(TEST_SIZE);
        for (FixedHashEquals o: createTestObjects(TEST_SIZE, collisions)) {
            objects.add(o);
        }
        Collections.shuffle(objects, new Random(42));


        int chunkSize = objects.size() / threads;
        for (int i = 0; i < threads; i++) {
            final List<FixedHashEquals> mySlice = objects.subList(i*chunkSize, (i + 1) * chunkSize);

            new Thread(() -> {
            	boolean awaitQuerySend = false;
                try {
                    startRunning.await();
                    for (FixedHashEquals o : mySlice) {
                        FixedHashEquals result = target.get(o);
                        if (o != result) {
                            failures.push(new Tuple<>(o, result));
                        }
                    }
                    awaitQuerySend = true;
                    startQuerying.await();
                    // query, and make sure we are not getting a fresh entry, but getting the one that was inserted before, by any of the threads
                    for (FixedHashEquals o : objects.subList(0, threads * chunkSize)) {
                        FixedHashEquals result = target.get(o.clone());
                        if (o != result) {
                            failures.push(new Tuple<>(o, result));
                        }
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                }
                finally {
                	if (!awaitQuerySend) {
                		try {
							startQuerying.await();
						} catch (InterruptedException | BrokenBarrierException e) {
						}
                	}
                    doneRunning.release();
                }
            }).start();
        }
        startRunning.await();

        doneRunning.acquire(threads);
        if (!failures.isEmpty()) {
            Tuple<FixedHashEquals, FixedHashEquals> first = failures.pop();
            assertSame(first.x, first.y);
        }
    }
    
    /**
     * Add a lot of data, causing multiple resizes. Then clear almost all, and see if after the cleanup of the collection has happened, if ones that were left over, still return the same value.
     */
    @ParameterizedTest @ArgumentsSource(ThreadCount_CollisionCount_TestMapProvider.class)
    public void nothingIsLostDuringGCCollects(int threads, int collisions, HashConsingMap<FixedHashEquals> target) throws InterruptedException, BrokenBarrierException {
        List<FixedHashEquals> objects =  new ArrayList<>(TEST_SIZE);
        for (FixedHashEquals o: createTestObjects(TEST_SIZE, collisions)) {
            objects.add(o);
        }
        Collections.shuffle(objects, new Random(42));

        // we keep a small selection to query afterwards
        List<FixedHashEquals> toKeep = new ArrayList<>();
        Random r = new Random(42 * 42);
        for (int i = 0; i < TEST_SIZE >> 4; i++) {
            toKeep.add(objects.get(r.nextInt(objects.size())));
        }


        for (FixedHashEquals o : objects) {
        	assertSame(o, target.get(o));
        }

        objects.clear();
        System.gc();
        Thread.sleep(1100);

        for (FixedHashEquals o : toKeep) {
        	assertSame(o, target.get(o.clone()));
        }
    }

    /**
     * Add a lot of data (from multiple threads), causing multiple resizes. Then clear almost all, and see if after the cleanup of the collection has happened, if ones that were left over, still return the same value.
     */
    @ParameterizedTest @ArgumentsSource(ThreadCount_CollisionCount_TestMapProvider.class)
    public void multithreadedNothingIsLostDuringGCCollects(int threads, int collisions, HashConsingMap<FixedHashEquals> target) throws InterruptedException, BrokenBarrierException {
        CyclicBarrier startRunning = new CyclicBarrier(threads + 1);
        CyclicBarrier stoppedInserting = new CyclicBarrier(threads + 1);
        CyclicBarrier startQuerying = new CyclicBarrier(threads + 1);
        Semaphore doneRunning = new Semaphore(0);
        ConcurrentLinkedDeque<Tuple<FixedHashEquals, FixedHashEquals>> failures = new ConcurrentLinkedDeque<>();

        List<FixedHashEquals> objects =  new ArrayList<>(TEST_SIZE);
        for (FixedHashEquals o: createTestObjects(TEST_SIZE, collisions)) {
            objects.add(o);
        }
        Collections.shuffle(objects, new Random(42));

        // we keep a small selection to query afterwards
        List<FixedHashEquals> toKeep = new ArrayList<>();
        Random r = new Random(42 * 42);
        for (int i = 0; i < TEST_SIZE >> 4; i++) {
            toKeep.add(objects.get(r.nextInt(objects.size())));
        }


        int chunkSize = objects.size() / threads;
        for (int i = 0; i < threads; i++) {
            List<FixedHashEquals> mySlice = objects.subList(i*chunkSize, (i + 1) * chunkSize);
            new Thread(() -> {
            	boolean awaitQuerySend = false;
                try {
                    startRunning.await();
                    for (FixedHashEquals o : mySlice) {
                        FixedHashEquals result = target.get(o);
                        if (o != result) {
                            failures.push(new Tuple<>(o, result));
                        }
                    }
                    stoppedInserting.await();
                    System.gc();
                    awaitQuerySend = true;
                    startQuerying.await();
                    for (FixedHashEquals o : toKeep) {
                        FixedHashEquals result = target.get(o.clone());
                        if (o != result) {
                            failures.push(new Tuple<>(o, result));
                        }
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                }
                finally {
                	if (!awaitQuerySend) {
                		try {
							startQuerying.await();
						} catch (InterruptedException | BrokenBarrierException e) {
						}
                	}
                    doneRunning.release();
                }
            }).start();
        }
        startRunning.await();
        stoppedInserting.await();
        objects.clear();
        System.gc();
        Thread.sleep(1100);
        startQuerying.await();

        doneRunning.acquire(threads);
        if (!failures.isEmpty()) {
            Tuple<FixedHashEquals, FixedHashEquals> first = failures.pop();
            assertSame(first.x, first.y);
        }
    }

    /**
     * Test the first two guarantee: for every get there is only one result, across threads. 
     * 
     * We test this by inserting clones of the same objects (in the same sequnce) from a lot of threads, and then check if only one result was returned
     */
    @ParameterizedTest @ArgumentsSource(ThreadCount_CollisionCount_TestMapProvider.class)
    public void insertMultipleCopiesAllReturnSameInstance(int threads, int collisions, HashConsingMap<FixedHashEquals> target) throws InterruptedException, BrokenBarrierException {
        CyclicBarrier startRunning = new CyclicBarrier((threads * 2) + 1);
        Semaphore doneRunning = new Semaphore(0);
        ConcurrentLinkedDeque<FixedHashEquals> results = new ConcurrentLinkedDeque<>();

        List<FixedHashEquals> objects =  new ArrayList<>(TEST_SIZE / 4);
        for (FixedHashEquals o: createTestObjects(TEST_SIZE / 4, collisions)) {
            objects.add(o);
        }
        Collections.shuffle(objects, new Random(42 * 42));


        for (int i = 0; i < (threads * 2); i++) {
            new Thread(() -> {
                try {
                    startRunning.await();
                    for (FixedHashEquals o : objects) {
                        results.push(target.get(o.clone()));
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                }
                finally {
                    doneRunning.release();
                }
            }).start();
        }
        startRunning.await();

        doneRunning.acquire(threads * 2);
        Map<FixedHashEquals, FixedHashEquals> seen = new HashMap<>(objects.size());
        for (FixedHashEquals e : results) {
            if (seen.containsKey(e)) {
                assertSame(e, seen.get(e), "We expect the same reference to be returned everythin for something that is equal");
            }
            else {
                seen.put(e, e);
            }
            
        }
    }
}
