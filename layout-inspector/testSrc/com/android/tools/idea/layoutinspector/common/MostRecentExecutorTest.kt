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
package com.android.tools.idea.layoutinspector.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MostRecentExecutorTest {
  private class TestRunnable(waitForProceed: Boolean = false, val fail: Boolean = false) :
    Runnable {
    var ran = false
      private set

    private val waitLatch = CountDownLatch(if (waitForProceed) 1 else 0)
    val finishedLatch = CountDownLatch(1)

    fun proceed() {
      waitLatch.countDown()
    }

    override fun run() {
      waitLatch.await(10, TimeUnit.SECONDS)
      ran = true
      if (fail) {
        throw Exception("Expected")
      }
      finishedLatch.countDown()
    }
  }

  @Test
  fun recentExecutorSkipsOverIntermediateWork() {
    val r1 = TestRunnable(waitForProceed = true)
    val r2 = TestRunnable()
    val r3 = TestRunnable()
    val r4 = TestRunnable()

    val recentExecutor = MostRecentExecutor(Executors.newSingleThreadExecutor())

    recentExecutor.execute(r1)
    recentExecutor.execute(r2)
    recentExecutor.execute(r3)
    recentExecutor.execute(r4)

    r1.proceed()
    r4.finishedLatch.await(10, TimeUnit.SECONDS)

    assertThat(r1.ran).isTrue()
    assertThat(r2.ran).isFalse()
    assertThat(r3.ran).isFalse()
    assertThat(r4.ran).isTrue()
  }

  @Test
  fun recentExecutorContinuesAfterFailure() {
    val r1 = TestRunnable(fail = true)
    val r2 = TestRunnable()

    val recentExecutor = MostRecentExecutor(Executors.newSingleThreadExecutor())

    recentExecutor.execute(r1)
    recentExecutor.execute(r2)

    assertThat(r2.finishedLatch.await(10, TimeUnit.SECONDS)).isTrue()

    assertThat(r1.ran).isTrue()
    assertThat(r2.ran).isTrue()
  }
}
