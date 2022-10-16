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
package com.android.tools.idea.concurrency

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.Assert
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutinesUtilsTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule)

  @JvmField
  @Rule
  var exceptionRule: ExpectedException = ExpectedException.none()

  @Test
  fun androidCoroutineScopeIsCancelledOnDispose() {
    runBlocking {
      // Prepare
      val disposable = Disposer.newDisposable()
      val scope = AndroidCoroutineScope(disposable)
      val job = scope.launch {
        while (true) {
          delay(1_000)
        }
      }

      // Act
      Disposer.dispose(disposable)

      // Assert
      Assert.assertTrue(job.isCancelled)
    }
  }

  @Test
  fun androidCoroutineScopeAllowsOverridingDispatcher() {
    val disposable = Disposer.newDisposable()
    try {
      runBlockingTest {
        // Prepare
        val scope = AndroidCoroutineScope(disposable, coroutineContext)

        // Act: We launch a coroutine in the new AndroidCoroutineScope. Usually, this
        // would result in a "leaked" coroutine exception to be thrown by runBlockingTest.
        // However, the AndroidCoroutineScope uses the same Dispatcher as the TestCoroutineScope,
        // so the coroutine runs in the same dispatcher, so it needs to finish before the
        // parent scope finishes.
        var result: Int? = 5
        // This job never finishes because it uses the same TestDispatcher
        val job = scope.launch {
          result = withTimeoutOrNull(1_000) {
            while (true) {
              delay(50)
            }
            5
          }
        }
        job.join()

        // Assert
        Assert.assertNull(result)
      }
    } finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  @Ignore("Test is disabled because of a bug in AndroidCoroutineScope")
  fun androidCoroutineScopeIsCancelledOnDisposeInRunBlockingTest() {
    runBlockingTest {
      // Prepare
      val disposable = Disposer.newDisposable()
      val scope = AndroidCoroutineScope(disposable, coroutineContext)
      val job = scope.launch {
        while (true) {
          delay(50)
          yield()
        }
      }

      // Act
      Disposer.dispose(disposable)

      // Assert
      Assert.assertTrue(job.isCancelled)
    }
  }

  @Test
  fun childScopeIsCancelledOnDispose() {
    runBlockingTest {
      // Prepare
      val disposable = Disposer.newDisposable()
      val scope = this.createChildScope(parentDisposable = disposable)
      scope.launch {
        while (true) {
          delay(1_000)
        }
      }

      // Act
      Disposer.dispose(disposable)

      // Assert
      Assert.assertFalse(scope.isActive)
      Assert.assertTrue(scope.coroutineContext.job.isCancelled)
    }
  }

  @Test
  fun childScopeIsNotDisposedOnCancel() {
    runBlockingTest {
      // Prepare
      val disposable = Disposer.newCheckedDisposable()
      val scope = this.createChildScope(parentDisposable = disposable)
      scope.launch {
        while (isActive) {
          delay(1_000)
          yield()
        }
      }

      // Act
      scope.cancel()

      // Assert
      try {
        Assert.assertFalse(disposable.isDisposed)
      } finally {
        Disposer.dispose(disposable)
      }
    }
  }
}
