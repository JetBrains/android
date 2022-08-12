/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.preview.lifecycle

import com.android.testutils.VirtualTimeScheduler
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.util.concurrent.TimeUnit

internal class DelayedLruActionQueueTest {
  lateinit var testDisposable: Disposable

  @Before
  fun setup() {
    testDisposable = Disposer.newDisposable()
  }

  @After
  fun tearDown() {
    Disposer.dispose(testDisposable)
  }

  @Test
  fun `run an action after timer expires`() {
    val scheduler = VirtualTimeScheduler()
    val lruActionQueue = DelayedLruActionQueue(5, Duration.ofMinutes(2), scheduler)

    var executionCount = 0
    lruActionQueue.addDelayedAction(testDisposable) { executionCount++ }
    assertEquals(1, lruActionQueue.queueSize())

    assertEquals(0, executionCount)
    scheduler.advanceBy(1, TimeUnit.MINUTES)
    assertEquals(0, executionCount)
    scheduler.advanceBy(2, TimeUnit.MINUTES)
    assertEquals(1, executionCount)
    assertEquals(0, lruActionQueue.queueSize())

    lruActionQueue.addDelayedAction(testDisposable) { executionCount++ }
    scheduler.advanceBy(3, TimeUnit.MINUTES)
    assertEquals(2, executionCount)
    assertEquals(0, lruActionQueue.queueSize())
  }

  @Test
  fun `run an action after eviction`() {
    val scheduler = VirtualTimeScheduler()
    val lruActionQueue = DelayedLruActionQueue(3, Duration.ofMinutes(2), scheduler)

    var executionCount = 0
    repeat(3) {
      lruActionQueue.addDelayedAction(testDisposable) { executionCount++ }
    }
    assertEquals(0, executionCount)
    assertEquals(3, lruActionQueue.queueSize())
    // Next one will evict one action that will get executed
    lruActionQueue.addDelayedAction(testDisposable) { executionCount++ }
    assertEquals(1, executionCount)
    assertEquals(3, lruActionQueue.queueSize())
    lruActionQueue.addDelayedAction(testDisposable) { executionCount++ }
    assertEquals(2, executionCount)
    assertEquals(3, lruActionQueue.queueSize())
  }

  @Test
  fun `disposed actions are not executed`() {
    val scheduler = VirtualTimeScheduler()
    val lruActionQueue = DelayedLruActionQueue(3, Duration.ofMinutes(2), scheduler)

    run {
      var executionCount = 0
      val disposable = Disposer.newDisposable()
      lruActionQueue.addDelayedAction(disposable) { executionCount++ }
      lruActionQueue.addDelayedAction(testDisposable) { executionCount++ }
      assertEquals(2, lruActionQueue.queueSize())
      Disposer.dispose(disposable)
      assertEquals(1, lruActionQueue.queueSize())
      scheduler.advanceBy(5, TimeUnit.MINUTES)
      assertEquals(1, executionCount)
    }

    run {
      var executionCount = 0
      assertEquals(0, lruActionQueue.queueSize())
      // Check the cancellation also works for evictions
      repeat(2) {
        lruActionQueue.addDelayedAction(testDisposable) { executionCount++ }
      }
      val disposable = Disposer.newDisposable()
      lruActionQueue.addDelayedAction(disposable) { executionCount++ }

      // The next two additions will evict not cancelled actions
      lruActionQueue.addDelayedAction(testDisposable) { executionCount++ }
      lruActionQueue.addDelayedAction(testDisposable) { executionCount++ }
      Disposer.dispose(disposable)
      assertEquals(2, executionCount)
      // But this one will evict a cancelled one
      lruActionQueue.addDelayedAction(testDisposable) { executionCount++ }
      assertEquals(2, executionCount)
    }
  }

  @Test
  fun `same action can not be scheduled more than once`() {
    class ActionCounter {
      var executionCount = 0

      fun action1() {
        executionCount++
      }

      fun action2() {
        executionCount++
      }
    }

    val scheduler = VirtualTimeScheduler()
    val lruActionQueue = DelayedLruActionQueue(3, Duration.ofMinutes(5), scheduler)

    val actionCounter = ActionCounter()
    lruActionQueue.addDelayedAction(testDisposable, actionCounter::action1)
    scheduler.advanceBy(4, TimeUnit.MINUTES)
    assertEquals(1, lruActionQueue.queueSize())
    lruActionQueue.addDelayedAction(testDisposable, actionCounter::action1)
    // The action should not have been re-added but rescheduled
    assertEquals(1, lruActionQueue.queueSize())
    scheduler.advanceBy(4, TimeUnit.MINUTES)
    assertEquals(0, actionCounter.executionCount)
    lruActionQueue.addDelayedAction(testDisposable, actionCounter::action2)
    assertEquals(2, lruActionQueue.queueSize())
  }

  @Test
  fun `disposed actions are removed`() {
    val scheduler = VirtualTimeScheduler()
    val lruActionQueue = DelayedLruActionQueue(3, Duration.ofMinutes(2), scheduler)
    val parentDisposable = Disposer.newDisposable(testDisposable, "test")

    var executionCount = 0
    lruActionQueue.addDelayedAction(parentDisposable) { executionCount++ }
    lruActionQueue.addDelayedAction(parentDisposable) { executionCount++ }
    assertEquals(2, lruActionQueue.queueSize())
    Disposer.dispose(parentDisposable)
    assertEquals(0, lruActionQueue.queueSize())
  }
}