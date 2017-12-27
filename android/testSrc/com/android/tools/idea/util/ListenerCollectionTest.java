/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.util;

import org.junit.Test;

import java.util.LinkedList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ListenerCollectionTest {
  @Test
  public void testAddRemoveSemantics() {
    ListenerCollection<MyListener> handler = ListenerCollection.createWithDirectExecutor();
    MyListener listener1 = () -> {
    };
    MyListener listener2 = () -> {
    };
    MyListener listener3 = () -> {
    };

    assertTrue(handler.add(listener1));
    assertFalse(handler.add(listener1));
    handler.clear();
    assertTrue(handler.add(listener1));
    assertTrue(handler.add(listener2));
    assertFalse(handler.add(listener1));
    assertFalse(handler.remove(listener3)); // Not yet added
    assertTrue(handler.add(listener3));
    assertTrue(handler.remove(listener3));
    assertFalse(handler.remove(listener3)); // Already removed
    assertTrue(handler.remove(listener1));
    handler.clear();
    assertFalse(handler.remove(listener2)); // Already removed
  }

  @Test
  public void testOnCurrentThread() {
    ListenerCollection<MyListener> handler = ListenerCollection.createWithDirectExecutor();

    AtomicInteger callCount = new AtomicInteger(0);
    handler.forEach(l -> callCount.incrementAndGet());
    assertEquals(0, callCount.get());

    handler.add(() -> {
    });
    handler.forEach(l -> callCount.incrementAndGet());
    assertEquals(1, callCount.get());

    callCount.set(0);
    handler.add(() -> {
    });
    // Now we have two listeners, so next invocation should increment the counter by 2
    handler.forEach(l -> callCount.incrementAndGet());
    assertEquals(2, callCount.get());

    callCount.set(0);
    handler.clear();
    handler.forEach(l -> callCount.incrementAndGet());
    assertEquals(0, callCount.get());

    // Check reentrant handler
    handler.add(() -> handler.add(() -> {
    }));
    handler.forEach(l -> {
      callCount.incrementAndGet();
      l.call();
    });
    assertEquals(1, callCount.get());
  }

  @Test
  public void testSeparateExecutor() {
    LinkedList<Runnable> calls = new LinkedList<>();
    Executor fakeExecutor = calls::add;
    ListenerCollection<MyListener> handler = ListenerCollection.createWithExecutor(fakeExecutor);

    handler.forEach(MyListener::call);
    assertTrue(calls.isEmpty()); // No listeners to call

    AtomicBoolean called = new AtomicBoolean(false);
    handler.add(() -> called.set(true));
    handler.forEach(MyListener::call);
    assertEquals(1, calls.size());
    assertFalse(called.get()); // The call was added but the listener has not executed the method yet.

    calls.forEach(Runnable::run);
    assertTrue(called.get()); // The call was added but the listener has not executed the method yet.
  }

  interface MyListener {
    void call();
  }
}