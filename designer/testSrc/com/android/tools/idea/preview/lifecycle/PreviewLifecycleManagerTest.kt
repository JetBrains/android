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

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.Disposable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull


private enum class ActiveState {
  INITIALIZED,
  RESUMED,
  DEACTIVATED,
  FULLY_DEACTIVATED
}

class PreviewLifecycleManagerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testExecutesOnlyIfActive() = runBlocking {
    val manager = PreviewLifecycleManager(projectRule.project, projectRule.fixture.testRootDisposable, this@runBlocking, {}, {}, {}, {})

    assertNull(manager.executeIfActive { 1 })

    manager.activate()

    assertEquals(1, manager.executeIfActive { 1 })

    manager.deactivate()

    assertNull(manager.executeIfActive { 1 })
  }

  @Test
  fun testActivationLifecycle() = runBlocking {
    var state = ActiveState.DEACTIVATED

    var delayedCallback: () -> Unit = {}

    val delayedExecutor = { ignore: Disposable, callback: () -> Unit ->
      delayedCallback = callback
    }

    val manager = PreviewLifecycleManager(
      projectRule.project,
      projectRule.fixture.testRootDisposable,
      this@runBlocking,
      { state = ActiveState.INITIALIZED },
      { state = ActiveState.RESUMED },
      { state = ActiveState.DEACTIVATED },
      { state = ActiveState.FULLY_DEACTIVATED },
      delayedExecutor)

    manager.activate()
    assertEquals(ActiveState.INITIALIZED, state)

    manager.deactivate()
    assertEquals(ActiveState.DEACTIVATED, state)

    manager.activate()
    assertEquals(ActiveState.RESUMED, state)

    // Resumed before the time the delayed callback was scheduled
    delayedCallback()
    assertEquals(ActiveState.RESUMED, state)

    manager.deactivate()
    assertEquals(ActiveState.DEACTIVATED, state)

    delayedCallback()
    assertEquals(ActiveState.FULLY_DEACTIVATED, state)

    manager.activate()
    assertEquals(ActiveState.RESUMED, state)

    manager.deactivate()
  }

  @Test
  fun testExecutionCancelledIfScopeIsCancelled() = runBlocking {
    val number = AtomicInteger(0)

    val job = launch {
      val manager = PreviewLifecycleManager(projectRule.project, projectRule.fixture.testRootDisposable, this@launch, {}, {}, {}, {})

      manager.activate()

      manager.executeIfActive {
        runBlocking {
          delay(1000)
          number.set(1)
        }
      }
    }

    job.cancel()
    job.join()

    assertEquals(0, number.get())
  }
}