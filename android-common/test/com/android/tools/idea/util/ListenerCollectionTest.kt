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
package com.android.tools.idea.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.LinkedList
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

typealias MyListener = () -> Unit

class ListenerCollectionTest {
  @Test
  fun testAddRemoveSemantics() {
    val handler = ListenerCollection.createWithDirectExecutor<MyListener>()
    val listener1 = {}
    val listener2 = {}
    val listener3 = {}

    assertTrue(handler.add(listener1))
    assertFalse(handler.add(listener1))
    assertEquals(1, handler.size())
    handler.clear()
    assertEquals(0, handler.size())
    assertTrue(handler.add(listener1))
    assertTrue(handler.add(listener2))
    assertFalse(handler.add(listener1))
    assertFalse(handler.remove(listener3)) // Not yet added
    assertTrue(handler.add(listener3))
    assertEquals(3, handler.size())
    assertTrue(handler.remove(listener3))
    assertFalse(handler.remove(listener3)) // Already removed
    assertTrue(handler.remove(listener1))
    assertEquals(1, handler.size())
    handler.clear()
    assertEquals(0, handler.size())
    assertFalse(handler.remove(listener2)) // Already removed
  }

  @Test
  fun testOnCurrentThread() {
    val handler = ListenerCollection.createWithDirectExecutor<MyListener>()

    val callCount = AtomicInteger(0)
    handler.forEach(Consumer { callCount.incrementAndGet() })
    assertEquals(0, callCount.get().toLong())

    handler.add({})
    handler.forEach(Consumer { callCount.incrementAndGet() })
    assertEquals(1, callCount.get().toLong())

    callCount.set(0)
    handler.add({})
    // Now we have two listeners, so next invocation should increment the counter by 2
    handler.forEach(Consumer { callCount.incrementAndGet() })
    assertEquals(2, callCount.get().toLong())

    callCount.set(0)
    handler.clear()
    handler.forEach(Consumer { callCount.incrementAndGet() })
    assertEquals(0, callCount.get().toLong())

    // Check reentrant handler
    handler.add({ handler.add({}) })
    handler.forEach(Consumer { l ->
      callCount.incrementAndGet()
      l()
    })
    assertEquals(1, callCount.get().toLong())
  }

  @Test
  fun testSeparateExecutor() {
    val calls = LinkedList<Runnable>()
    val fakeExecutor = Executor { calls.add(it) }
    val handler = ListenerCollection.createWithExecutor<MyListener>(fakeExecutor)

    handler.forEach(Consumer { it() })
    assertTrue(calls.isEmpty()) // No listeners to call

    val called = AtomicBoolean(false)
    handler.add({ called.set(true) })
    handler.forEach(Consumer { it() })
    assertEquals(1, calls.size.toLong())
    assertFalse(called.get()) // The call was added but the listener has not executed the method yet.

    calls.forEach(Consumer<Runnable> { it.run() })
    assertTrue(called.get()) // The call was added but the listener has not executed the method yet.
  }
}