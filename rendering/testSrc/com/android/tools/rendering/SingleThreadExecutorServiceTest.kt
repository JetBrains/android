/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.rendering

import com.android.testutils.VirtualTimeScheduler
import org.junit.Assert
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SingleThreadExecutorServiceTest {
  @Test
  fun testThreadProfiling() {
    var slowThreadCounter = 0
    val scheduledExecutor = VirtualTimeScheduler()
    val executor =
      SingleThreadExecutorService.create(
        "Test thread",
        threadProfileSettings =
          ThreadProfileSettings(
            1000,
            500,
            3,
            scheduledExecutorService = scheduledExecutor,
            onSlowThread = { slowThreadCounter++ },
          ),
      )

    val executing = CountDownLatch(1)
    val wait = CompletableFuture<Unit>()
    // Submit a task that just blocks
    executor.submit {
      executing.countDown()
      wait.get()
    }

    // Wait for the task to be executing so we know the timings are accurate.
    executing.await()
    // The profiling will trigger after 1s and then every 500ms
    scheduledExecutor.advanceBy(1100, TimeUnit.MILLISECONDS)
    Assert.assertEquals(1, slowThreadCounter)
    scheduledExecutor.advanceBy(500, TimeUnit.MILLISECONDS)
    Assert.assertEquals(2, slowThreadCounter)
    scheduledExecutor.advanceBy(500, TimeUnit.MILLISECONDS)
    Assert.assertEquals(3, slowThreadCounter)

    // From here, no more samples are taken
    scheduledExecutor.advanceBy(500, TimeUnit.MILLISECONDS)
    Assert.assertEquals(3, slowThreadCounter)
    scheduledExecutor.advanceBy(5500, TimeUnit.MILLISECONDS)
    Assert.assertEquals(3, slowThreadCounter)

    executor.shutdown()
  }

  @Test
  fun testHasSpawnedCurrentThread() {
    val executor =
      SingleThreadExecutorService.create(
        "Test thread",
        threadProfileSettings = ThreadProfileSettings.disabled,
      )

    var exception: Throwable? = null
    executor
      .submit {
        assertTrue(executor.hasSpawnedCurrentThread())

        Thread {
            // Child threads must also return hasSpawnedCurrentThread
            assertTrue(executor.hasSpawnedCurrentThread())
          }
          .also {
            it.setUncaughtExceptionHandler { t, e -> exception = e }
            it.start()
            it.join()
          }
        exception?.let { throw it }
      }
      .get()
    assertFalse(executor.hasSpawnedCurrentThread())
  }
}
