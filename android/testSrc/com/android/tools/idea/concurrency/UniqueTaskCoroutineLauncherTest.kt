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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class UniqueTaskCoroutineLauncherTest {
  private var myTasksCompletedCount: Int = 0

  @Before
  fun setUp() {
    myTasksCompletedCount = 0
  }

  @Test
  fun testLaunchCancellation_suspendFun() = runBlocking {
    coroutineScope {
      val taskLauncher = UniqueTaskCoroutineLauncher(this, "Test")
      launch { taskLauncher.launch { myTask() } }
      launch { taskLauncher.launch { myTask() } }
    }
    delay(3000L)
    assertEquals(1, myTasksCompletedCount)
  }

  @Test
  fun testLaunchCancellation_Job() = runBlocking {
    coroutineScope {
      val taskLauncher = UniqueTaskCoroutineLauncher(this, "Test")
      launch { taskLauncher.launch { myJobTask(this@coroutineScope) } }
      launch { taskLauncher.launch { myJobTask(this@coroutineScope) } }
    }
    delay(3000L)
    assertEquals(1, myTasksCompletedCount)
  }

  private suspend fun myTask() {
    delay(1000L)
    myTasksCompletedCount++
  }

  private suspend fun myJobTask(coroutineScope: CoroutineScope) {
    var myJob: Job? = null
    try {
      myJob = coroutineScope.launch { myTask() }
      myJob.join()
    } catch (e: CancellationException) {
      // Use runBlocking to make sure to wait until the cancellation is completed.
      runBlocking {
        myJob?.cancelAndJoin()
        throw e
      }
    }
  }
}
