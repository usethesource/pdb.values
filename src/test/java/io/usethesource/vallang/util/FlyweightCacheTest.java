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

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;

import org.junit.Before;
import org.junit.Test;

public class FlyweightCacheTest {
	
	private final class FixedHashEquals {
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
		public boolean equals(Object obj) {
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
		protected Object clone() throws CloneNotSupportedException {
			return new FixedHashEquals(hash, equals);
		}
		
	}
	
	private WeakReferenceFlyweightCache<FixedHashEquals, FixedHashEquals> target;
	
	private FixedHashEquals identityFlyweight(FixedHashEquals key) {
		return target.getFlyweight(key, x -> x);
	}

	@Before
	public void constructData() {
		target = new WeakReferenceFlyweightCache<>();
	}
	
	@Test
	public void restoreSameReference() {
		FixedHashEquals a = new FixedHashEquals(1,1);
		assertSame(a, identityFlyweight(a));
		assertSame(a, identityFlyweight(a));
	}

	@Test
	public void restoreDifferentReference() {
		FixedHashEquals a = new FixedHashEquals(1,1);
		FixedHashEquals b = new FixedHashEquals(1,2);
		assertSame(a, identityFlyweight(a));
		assertSame(b, identityFlyweight(b));

		assertSame(a, identityFlyweight(a));
	}

	@Test
	public void restoreOldReference() {
		FixedHashEquals a = new FixedHashEquals(1,1);
		FixedHashEquals b = new FixedHashEquals(1,1);
		assertSame(a, identityFlyweight(a));
		assertSame(a, identityFlyweight(b));
		assertSame(a, identityFlyweight(a));
	}
	
	@Test
	public void looseReference() throws InterruptedException {
		FixedHashEquals a = new FixedHashEquals(1,1);
		assertSame(a, identityFlyweight(a));
		a = null;
		System.gc();
		Thread.sleep(10); // wait for the GC to finish

		// a new reference, the target should not have kept the old reference
		a = new FixedHashEquals(1,1);
		assertSame(a, identityFlyweight(a));
	}
	
	
	@Test
	public void storeManyObjects() {
		List<FixedHashEquals> objects = new ArrayList<>();
		for (int i = 0; i < 1024*1024; i ++) {
			objects.add(new FixedHashEquals(i, i));
		}
		
		// store them 
		for (FixedHashEquals o : objects) {
			assertSame(o, identityFlyweight(o));
		}

		// and then again, to see that they are all in there
		for (FixedHashEquals o : objects) {
			assertSame(o, identityFlyweight(o));
		}
	}
	
	private static int weakenHash(int hash, int maxEntries) {
		return hash % maxEntries;
	}

	@Test
	public void storeManyObjectsAndLooseThem() throws CloneNotSupportedException, InterruptedException {
		FixedHashEquals[] objects = createTestObjects(1024*1024, 64);
		
		// store them 
		for (FixedHashEquals o : objects) {
			assertSame(o, identityFlyweight(o));
		}
		
		FixedHashEquals a = (FixedHashEquals) objects[0].clone();
		// loose  them
		Arrays.fill(objects, null);
		objects = null;
		System.gc();
		// wait for GC and reference queue to finish
		Thread.sleep(200);
		

		// check if the clone won't return the old reference
		assertSame(a, identityFlyweight(a));
	}
	

	@Test
	public void storeManyObjectsAndQueryThem() throws InterruptedException, CloneNotSupportedException {
		FixedHashEquals[] objects = createTestObjects(1024*1024, 64);
		
		// store them 
		for (FixedHashEquals o : objects) {
			assertSame(o, identityFlyweight(o));
		}
		
		// look up
		for (int i = 0; i < objects.length; i ++) {
			assertSame(objects[i],  identityFlyweight((FixedHashEquals) objects[i].clone()));
		}
	}

	private FixedHashEquals[] createTestObjects(int size, int groups) {
		FixedHashEquals[] objects = new FixedHashEquals[size];
		for (int i = 0; i < size; i ++) {
			objects[i] = new FixedHashEquals(weakenHash(i, size / groups), i);
		}
		return objects;
	}

	
	@Test
	public void clearMostAndQueryRest() throws InterruptedException, CloneNotSupportedException {
		FixedHashEquals[] objects = createTestObjects(1024*1024, 64);
		
		// store them 
		for (FixedHashEquals o : objects) {
			assertSame(o, identityFlyweight(o));
		}
		
		for (int i=0; i < objects.length - 10; i++) {
			objects[i] = null;
		}
		System.gc();
		// wait for GC and reference queue to finish
		Thread.sleep(1000);
		
		for (int i=objects.length - 10; i < objects.length; i++) {
			assertSame(objects[i],  identityFlyweight((FixedHashEquals) objects[i].clone()));
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
	private static final int THREAD_COUNT = 8;
	@Test
	public void multithreadedAccess() throws InterruptedException, BrokenBarrierException {
		FixedHashEquals[] objects = createTestObjects(64*1024, 1024);
		CyclicBarrier startRunning = new CyclicBarrier(THREAD_COUNT + 1);
		CyclicBarrier startQuerying = new CyclicBarrier(THREAD_COUNT);
		Semaphore doneRunning = new Semaphore(0);
		ConcurrentLinkedDeque<Tuple<FixedHashEquals, FixedHashEquals>> failures = new ConcurrentLinkedDeque<>();
		
		for (int i = 0; i < THREAD_COUNT; i++) {
			List<FixedHashEquals> objects2 =  new ArrayList<>(objects.length);
			for (FixedHashEquals o: objects) {
				objects2.add(o);
			}
		
            new Thread(() -> {
                try {
                    Collections.shuffle(objects2);
                    startRunning.await();
                    for (FixedHashEquals o : objects2) {
                    	FixedHashEquals result = identityFlyweight(o);
                    	if (o != result) {
                    		failures.push(new Tuple<>(o, result));
                    	}
                    }
                    objects2.removeIf(h -> h.hash > 30);
                    System.gc();
                    Thread.sleep(100);

                    startQuerying.await();
                    for (FixedHashEquals o : objects2) {
                    	FixedHashEquals result = identityFlyweight((FixedHashEquals) o.clone());
                    	if (o != result) {
                    		failures.push(new Tuple<>(o, result));
                    	}
                    }
                } catch (InterruptedException | BrokenBarrierException | CloneNotSupportedException e) {
                }
                finally {
                    doneRunning.release();
                }
            }).start();
		}
		
		Arrays.fill(objects, null);
		objects = null;
		startRunning.await();
		
		doneRunning.acquire(THREAD_COUNT);
		if (!failures.isEmpty()) {
			Tuple<FixedHashEquals, FixedHashEquals> first = failures.pop();
			assertSame(first.x, first.y);
		}
	}
	
	private static final int TEST_SIZE = 32 << 10;

	@Test
	public void multithreadedNothingIsLostDuringResizes() throws InterruptedException, BrokenBarrierException {
		CyclicBarrier startRunning = new CyclicBarrier(THREAD_COUNT + 1);
		CyclicBarrier startQuerying = new CyclicBarrier(THREAD_COUNT);
		Semaphore doneRunning = new Semaphore(0);
		ConcurrentLinkedDeque<Tuple<FixedHashEquals, FixedHashEquals>> failures = new ConcurrentLinkedDeque<>();

		List<FixedHashEquals> objects =  new ArrayList<>(TEST_SIZE);
		for (FixedHashEquals o: createTestObjects(TEST_SIZE, TEST_SIZE >> 2)) {
			objects.add(o);
		}
		Collections.shuffle(objects, new Random(42));

		
		int chunkSize = objects.size() / THREAD_COUNT;
		for (int i = 0; i < THREAD_COUNT; i++) {
			final List<FixedHashEquals> mySlice = objects.subList(i*chunkSize, (i + 1) * chunkSize);
		
            new Thread(() -> {
                try {
                    startRunning.await();
                    for (FixedHashEquals o : mySlice) {
                    	FixedHashEquals result = identityFlyweight(o);
                    	if (o != result) {
                    		failures.push(new Tuple<>(o, result));
                    	}
                    }
                    startQuerying.await();
                    for (FixedHashEquals o : objects.subList(0, THREAD_COUNT * chunkSize)) {
                    	FixedHashEquals result = identityFlyweight((FixedHashEquals) o.clone());
                    	if (o != result) {
                    		failures.push(new Tuple<>(o, result));
                    	}
                    }
                } catch (InterruptedException | BrokenBarrierException | CloneNotSupportedException e) {
                }
                finally {
                    doneRunning.release();
                }
            }).start();
		}
		startRunning.await();
		
		doneRunning.acquire(THREAD_COUNT);
		if (!failures.isEmpty()) {
			Tuple<FixedHashEquals, FixedHashEquals> first = failures.pop();
			assertSame(first.x, first.y);
		}
	}

	@Test
	public void multithreadedNothingIsLostDuringGCCollects() throws InterruptedException, BrokenBarrierException {
		CyclicBarrier startRunning = new CyclicBarrier(THREAD_COUNT + 1);
		CyclicBarrier stoppedInserting = new CyclicBarrier(THREAD_COUNT + 1);
		CyclicBarrier startQuerying = new CyclicBarrier(THREAD_COUNT + 1);
		Semaphore doneRunning = new Semaphore(0);
		ConcurrentLinkedDeque<Tuple<FixedHashEquals, FixedHashEquals>> failures = new ConcurrentLinkedDeque<>();

		List<FixedHashEquals> objects =  new ArrayList<>(TEST_SIZE);
		for (FixedHashEquals o: createTestObjects(TEST_SIZE, TEST_SIZE >> 2)) {
			objects.add(o);
		}
		Collections.shuffle(objects, new Random(42));

		// we keep a small selection to query afterwards
		List<FixedHashEquals> toKeep = new ArrayList<>();
		Random r = new Random(42 * 42);
		for (int i = 0; i < TEST_SIZE >> 4; i++) {
			toKeep.add(objects.get(r.nextInt(objects.size())));
		}

		
		int chunkSize = objects.size() / THREAD_COUNT;
		for (int i = 0; i < THREAD_COUNT; i++) {
            List<FixedHashEquals> mySlice = objects.subList(i*chunkSize, (i + 1) * chunkSize);
            new Thread(() -> {
                try {
                    startRunning.await();
                    for (FixedHashEquals o : mySlice) {
                    	FixedHashEquals result = identityFlyweight(o);
                    	if (o != result) {
                    		failures.push(new Tuple<>(o, result));
                    	}
                    }
                    stoppedInserting.await();
                    System.gc();
                    startQuerying.await();
                    for (FixedHashEquals o : toKeep) {
                    	FixedHashEquals result = identityFlyweight((FixedHashEquals) o.clone());
                    	if (o != result) {
                    		failures.push(new Tuple<>(o, result));
                    	}
                    }
                } catch (InterruptedException | BrokenBarrierException | CloneNotSupportedException e) {
                }
                finally {
                    doneRunning.release();
                }
            }).start();
		}
		startRunning.await();
		stoppedInserting.await();
		objects.clear();
		System.gc();
		Thread.sleep(300);
		startQuerying.await();

		
		doneRunning.acquire(THREAD_COUNT);
		if (!failures.isEmpty()) {
			Tuple<FixedHashEquals, FixedHashEquals> first = failures.pop();
			assertSame(first.x, first.y);
		}
	}
}