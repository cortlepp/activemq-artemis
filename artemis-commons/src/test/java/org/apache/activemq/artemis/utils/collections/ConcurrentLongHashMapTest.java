/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.utils.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongFunction;

import org.junit.jupiter.api.Test;

public class ConcurrentLongHashMapTest {

   @Test
   public void simpleInsertions() {
      ConcurrentLongHashMap<String> map = new ConcurrentLongHashMap<>(16);

      assertTrue(map.isEmpty());
      assertNull(map.put(1, "one"));
      assertFalse(map.isEmpty());

      assertNull(map.put(2, "two"));
      assertNull(map.put(3, "three"));

      assertEquals(map.size(), 3);

      assertEquals(map.get(1), "one");
      assertEquals(map.size(), 3);

      assertEquals(map.remove(1), "one");
      assertEquals(map.size(), 2);
      assertEquals(map.get(1), null);
      assertEquals(map.get(5), null);
      assertEquals(map.size(), 2);

      assertNull(map.put(1, "one"));
      assertEquals(map.size(), 3);
      assertEquals(map.put(1, "uno"), "one");
      assertEquals(map.size(), 3);
   }

   @Test
   public void testRemove() {
      ConcurrentLongHashMap<String> map = new ConcurrentLongHashMap<>();

      assertTrue(map.isEmpty());
      assertNull(map.put(1, "one"));
      assertFalse(map.isEmpty());

      assertFalse(map.remove(0, "zero"));
      assertFalse(map.remove(1, "uno"));

      assertFalse(map.isEmpty());
      assertTrue(map.remove(1, "one"));
      assertTrue(map.isEmpty());
   }

   @Test
   public void testNegativeUsedBucketCount() {
      ConcurrentLongHashMap<String> map = new ConcurrentLongHashMap<>(16, 1);

      map.put(0, "zero");
      assertEquals(1, map.getUsedBucketCount());
      map.put(0, "zero1");
      assertEquals(1, map.getUsedBucketCount());
      map.remove(0);
      assertEquals(0, map.getUsedBucketCount());
      map.remove(0);
      assertEquals(0, map.getUsedBucketCount());
   }

   @Test
   public void testRehashing() {
      int n = 16;
      ConcurrentLongHashMap<Integer> map = new ConcurrentLongHashMap<>(n / 2, 1);
      assertEquals(map.capacity(), n);
      assertEquals(map.size(), 0);

      for (int i = 0; i < n; i++) {
         map.put(i, i);
      }

      assertEquals(map.capacity(), 2 * n);
      assertEquals(map.size(), n);
   }

   @Test
   public void testRehashingWithDeletes() {
      int n = 16;
      ConcurrentLongHashMap<Integer> map = new ConcurrentLongHashMap<>(n / 2, 1);
      assertEquals(map.capacity(), n);
      assertEquals(map.size(), 0);

      for (int i = 0; i < n / 2; i++) {
         map.put(i, i);
      }

      for (int i = 0; i < n / 2; i++) {
         map.remove(i);
      }

      for (int i = n; i < (2 * n); i++) {
         map.put(i, i);
      }

      assertEquals(map.capacity(), 2 * n);
      assertEquals(map.size(), n);
   }

   @Test
   public void concurrentInsertions() throws Throwable {
      ConcurrentLongHashMap<String> map = new ConcurrentLongHashMap<>();
      ExecutorService executor = Executors.newCachedThreadPool();

      final int nThreads = 16;
      final int N = 100_000;
      String value = "value";

      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < nThreads; i++) {
         final int threadIdx = i;

         futures.add(executor.submit(() -> {
            Random random = new Random();

            for (int j = 0; j < N; j++) {
               long key = random.nextLong();
               // Ensure keys are uniques
               key -= key % (threadIdx + 1);

               map.put(key, value);
            }
         }));
      }

      for (Future<?> future : futures) {
         future.get();
      }

      assertEquals(map.size(), N * nThreads);

      executor.shutdown();
   }

   @Test
   public void concurrentInsertionsAndReads() throws Throwable {
      ConcurrentLongHashMap<String> map = new ConcurrentLongHashMap<>();
      ExecutorService executor = Executors.newCachedThreadPool();

      final int nThreads = 16;
      final int N = 100_000;
      String value = "value";

      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < nThreads; i++) {
         final int threadIdx = i;

         futures.add(executor.submit(() -> {
            Random random = new Random();

            for (int j = 0; j < N; j++) {
               long key = random.nextLong();
               // Ensure keys are uniques
               key -= key % (threadIdx + 1);

               map.put(key, value);
            }
         }));
      }

      for (Future<?> future : futures) {
         future.get();
      }

      assertEquals(map.size(), N * nThreads);

      executor.shutdown();
   }

   @Test
   public void testIteration() {
      ConcurrentLongHashMap<String> map = new ConcurrentLongHashMap<>();

      assertEquals(map.keys(), Collections.emptyList());
      assertEquals(map.values(), Collections.emptyList());

      map.put(0, "zero");

      assertEquals(map.keys(), Arrays.asList(0L));
      assertEquals(map.values(), Arrays.asList("zero"));

      map.remove(0);

      assertEquals(map.keys(), Collections.emptyList());
      assertEquals(map.values(), Collections.emptyList());

      map.put(0, "zero");
      map.put(1, "one");
      map.put(2, "two");

      List<Long> keys = map.keys();
      Collections.sort(keys);
      assertEquals(keys, Arrays.asList(0L, 1L, 2L));

      List<String> values = map.values();
      Collections.sort(values);
      assertEquals(values, Arrays.asList("one", "two", "zero"));

      map.put(1, "uno");

      keys = map.keys();
      Collections.sort(keys);
      assertEquals(keys, Arrays.asList(0L, 1L, 2L));

      values = map.values();
      Collections.sort(values);
      assertEquals(values, Arrays.asList("two", "uno", "zero"));

      map.clear();
      assertTrue(map.isEmpty());
   }

   @Test
   public void testHashConflictWithDeletion() {
      final int Buckets = 16;
      ConcurrentLongHashMap<String> map = new ConcurrentLongHashMap<>(Buckets, 1);

      // Pick 2 keys that fall into the same bucket
      long key1 = 1;
      long key2 = 27;

      int bucket1 = ConcurrentLongHashMap.signSafeMod(ConcurrentLongHashMap.hash(key1), Buckets);
      int bucket2 = ConcurrentLongHashMap.signSafeMod(ConcurrentLongHashMap.hash(key2), Buckets);
      assertEquals(bucket1, bucket2);

      assertEquals(map.put(key1, "value-1"), null);
      assertEquals(map.put(key2, "value-2"), null);
      assertEquals(map.size(), 2);

      assertEquals(map.remove(key1), "value-1");
      assertEquals(map.size(), 1);

      assertEquals(map.put(key1, "value-1-overwrite"), null);
      assertEquals(map.size(), 2);

      assertEquals(map.remove(key1), "value-1-overwrite");
      assertEquals(map.size(), 1);

      assertEquals(map.put(key2, "value-2-overwrite"), "value-2");
      assertEquals(map.get(key2), "value-2-overwrite");

      assertEquals(map.size(), 1);
      assertEquals(map.remove(key2), "value-2-overwrite");
      assertTrue(map.isEmpty());
   }

   @Test
   public void testPutIfAbsent() {
      ConcurrentLongHashMap<String> map = new ConcurrentLongHashMap<>();
      assertEquals(map.putIfAbsent(1, "one"), null);
      assertEquals(map.get(1), "one");

      assertEquals(map.putIfAbsent(1, "uno"), "one");
      assertEquals(map.get(1), "one");
   }

   @Test
   public void testComputeIfAbsent() {
      ConcurrentLongHashMap<Integer> map = new ConcurrentLongHashMap<>(16, 1);
      AtomicInteger counter = new AtomicInteger();
      LongFunction<Integer> provider = key -> counter.getAndIncrement();

      assertEquals(map.computeIfAbsent(0, provider).intValue(), 0);
      assertEquals(map.get(0).intValue(), 0);

      assertEquals(map.computeIfAbsent(1, provider).intValue(), 1);
      assertEquals(map.get(1).intValue(), 1);

      assertEquals(map.computeIfAbsent(1, provider).intValue(), 1);
      assertEquals(map.get(1).intValue(), 1);

      assertEquals(map.computeIfAbsent(2, provider).intValue(), 2);
      assertEquals(map.get(2).intValue(), 2);
   }

   int Iterations = 1;
   int ReadIterations = 100;
   int N = 1_000_000;

   public void benchConcurrentLongHashMap() throws Exception {
      // public static void main(String args[]) {
      ConcurrentLongHashMap<String> map = new ConcurrentLongHashMap<>(N, 1);

      for (long i = 0; i < Iterations; i++) {
         for (int j = 0; j < N; j++) {
            map.put(i, "value");
         }

         for (long h = 0; h < ReadIterations; h++) {
            for (int j = 0; j < N; j++) {
               map.get(i);
            }
         }

         for (int j = 0; j < N; j++) {
            map.remove(i);
         }
      }
   }

   public void benchConcurrentHashMap() throws Exception {
      ConcurrentMap<Long, String> map = new ConcurrentHashMap<>(N, 0.66f, 1);

      for (long i = 0; i < Iterations; i++) {
         for (int j = 0; j < N; j++) {
            map.put(i, "value");
         }

         for (long h = 0; h < ReadIterations; h++) {
            for (int j = 0; j < N; j++) {
               map.get(i);
            }
         }

         for (int j = 0; j < N; j++) {
            map.remove(i);
         }
      }
   }

   void benchHashMap() throws Exception {
      Map<Long, String> map = new HashMap<>(N, 0.66f);

      for (long i = 0; i < Iterations; i++) {
         for (int j = 0; j < N; j++) {
            map.put(i, "value");
         }

         for (long h = 0; h < ReadIterations; h++) {
            for (int j = 0; j < N; j++) {
               map.get(i);
            }
         }

         for (int j = 0; j < N; j++) {
            map.remove(i);
         }
      }
   }

   public static void main(String[] args) throws Exception {
      ConcurrentLongHashMapTest t = new ConcurrentLongHashMapTest();

      long start = System.nanoTime();
      // t.benchHashMap();
      long end = System.nanoTime();

      System.out.println("HM:   " + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");

      start = System.nanoTime();
      t.benchConcurrentHashMap();
      end = System.nanoTime();

      System.out.println("CHM:  " + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");

      start = System.nanoTime();
      // t.benchConcurrentLongHashMap();
      end = System.nanoTime();

      System.out.println("CLHM: " + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");

   }
}