/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.progress

import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.runInEdtAndWait
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Tests for [StudioProgressRunner] */
class StudioProgressRunnerTest : BareTestFixtureTestCase() {
  @get:Rule
  var runInEdt: TestRule = TestRule { base: Statement, description: Description ->
    object : Statement() {
      @Throws(Throwable::class)
      override fun evaluate() {
        runInEdtAndWait { base.evaluate() }
      }
    }
  }

  @Test
  @Throws(Exception::class)
  fun testSyncWithProgress() {
    val runner = StudioProgressRunner(false, "test", null)
    val invoked = AtomicBoolean(false)

    runner.runSyncWithProgress { _ ->
      assertFalse(ApplicationManager.getApplication().isDispatchThread)
      try {
        Thread.sleep(100)
      } catch (_: InterruptedException) {
        fail()
      }
      invoked.set(true)
    }

    assertTrue(invoked.get())
  }

  @Test
  @Throws(Exception::class)
  fun testAsyncWithProgress() {
    val runner = StudioProgressRunner(false, "test", null)
    val lock = Semaphore(1)
    lock.acquire()

    val coroutineScope = CoroutineScope(EmptyCoroutineContext)
    coroutineScope.launch {
      runner.runAsyncWithProgress { _ ->
        assertFalse(ApplicationManager.getApplication().isDispatchThread)
        try {
          lock.acquire()
        } catch (_: InterruptedException) {
          fail()
        }
      }
    }
    lock.release()
    coroutineScope.cancel()
  }

  @Test
  fun testSyncFromNonUi() {
    val f =
      ApplicationManager.getApplication().executeOnPooledThread {
        val runner = StudioProgressRunner(false, "test", null)
        val invoked = AtomicBoolean(false)

        runner.runSyncWithProgress { _ ->
          assertFalse(ApplicationManager.getApplication().isDispatchThread)
          try {
            Thread.sleep(100)
          } catch (_: InterruptedException) {
            fail()
          }
          invoked.set(true)
        }

        assertTrue(invoked.get())
      }

    pumpEventsAndWaitForFuture(f, 10, TimeUnit.SECONDS)
  }

  @Test
  fun repoLoad() {
    // Model of a RepoManager interaction, where we first do an async load, which has a callback
    // that switches context to the EDT, and simultaneously do a sync load that piggybacks on the
    // async load.
    val runner = StudioProgressRunner(cancellable = false, "test", null)

    val loadStart = CompletableDeferred<Unit>()
    val loadFinish = CompletableDeferred<Unit>()
    val callback = CompletableDeferred<Unit>()

    runner.runAsyncWithProgress { _ ->
      loadStart.await()
      // As usual, Dispatchers.EDT is instant deadlock.
      withContext(Dispatchers.Main) { callback.complete(Unit) }
      loadFinish.complete(Unit)
    }

    runner.runSyncWithProgress { _ ->
      loadStart.complete(Unit)
      withTimeout(5000) {
        callback.await()
        loadFinish.await()
      }
    }
  }
}
