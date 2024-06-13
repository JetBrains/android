/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.common.util

import com.android.testutils.VirtualTimeScheduler
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Test

private const val TICKER_STEP_MILLIS = 100L

/**
 * This is a workaround to fix the fact that [VirtualTimeScheduler] is blocking thread on
 * [awaitTermination]
 */
private class VirtualTimeSchedulerNonBlocking : VirtualTimeScheduler() {
  override fun isTerminated() = true
}

class ControllableTickerTest {

  private lateinit var executorProvider: () -> ScheduledExecutorService
  private var currentExecutor: VirtualTimeScheduler? = null

  @Before
  fun setUp() {
    executorProvider = { VirtualTimeSchedulerNonBlocking().apply { currentExecutor = this } }
  }

  @Test
  fun testTickerPeriod() {
    val tickCounter =
      object {
        var counter: Int = 0
      }
    val counterIncrementer: () -> Unit = { tickCounter.counter++ }

    val ticker =
      ControllableTicker(
        counterIncrementer,
        Duration.ofMillis(TICKER_STEP_MILLIS),
        executorProvider,
      )

    ticker.start()

    currentExecutor!!.advanceBy(1, TimeUnit.SECONDS)

    ticker.stop()

    assertThat(tickCounter.counter).isEqualTo(11)
  }

  @Test
  fun testCanRestart() {
    val ticker = ControllableTicker({}, Duration.ofMillis(10), executorProvider)

    ticker.start()

    currentExecutor!!.advanceBy(50, TimeUnit.MILLISECONDS)

    ticker.stop()

    currentExecutor!!.advanceBy(50, TimeUnit.MILLISECONDS)

    ticker.start()

    currentExecutor!!.advanceBy(50, TimeUnit.MILLISECONDS)

    ticker.stop()
  }
}
