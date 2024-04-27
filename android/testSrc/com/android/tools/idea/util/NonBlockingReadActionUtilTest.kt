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
package com.android.tools.idea.util

import com.android.tools.idea.concurrency.AndroidExecutors
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class NonBlockingReadActionUtilTest {

  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun waitInterruptibly() {
    val requestedReads = 50
    val reads = AtomicInteger(requestedReads)
    invokeLater {
      while (reads.get() > 0) {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue() // Let the non-blocking read action restart.
        Thread.sleep(Random.nextLong(3)) // Let it start and maybe finish.
        runWriteAction { // Interrupt after a random wait.
          Thread.sleep(Random.nextLong(2))
        }
      }
    }
    var attempts = 0
    while (reads.decrementAndGet() > 0) {
      val result = ReadAction.nonBlocking(
        Callable {
          val future = CompletableFuture.runAsync(
            { Thread.sleep(Random.nextLong(5)) },
            AndroidExecutors.getInstance().workerThreadExecutor
          )
          runReadAction {
            attempts++
            future.waitInterruptibly()
            future.get()
          }
        }).executeSynchronously()
    }
    assertThat(attempts).isGreaterThan(requestedReads * 120 / 100) // At last some reads will restart. It usually completes with attempts >= 120.
  }
}
