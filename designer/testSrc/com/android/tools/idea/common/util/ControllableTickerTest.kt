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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.lang.Thread.sleep
import java.time.Duration

private const val TICKER_STEP_MILLIS = 100L
// 10% should be ok
private const val ACCEPTABLE_DEVIATION = (TICKER_STEP_MILLIS * 0.1f).toLong()
private val ACCEPTABLE_RANGE: LongRange = TICKER_STEP_MILLIS-ACCEPTABLE_DEVIATION..TICKER_STEP_MILLIS+ACCEPTABLE_DEVIATION

class ControllableTickerTest {
  @Test
  fun testTickerPeriod() {
    val timesList = mutableListOf<Long>()
    val timeRecorder: () -> Unit = { timesList.add(System.nanoTime()) }

    val timeDiffs = mutableListOf<Long>()

    val ticker = ControllableTicker(timeRecorder, Duration.ofMillis(TICKER_STEP_MILLIS))

    ticker.start()

    sleep(Duration.ofSeconds(1).toMillis())

    ticker.stop()

    for (i in 0 until timesList.count()-1) {
      timeDiffs.add(Duration.ofNanos((timesList[i+1]-timesList[i])).toMillis())
    }

    // Check that the difference between the subsequent ticks is acceptable
    for (i in 0 until timeDiffs.count()) {
      assertThat(timeDiffs[i]).isIn(ACCEPTABLE_RANGE)
    }
    // Check that it stopped in time. Ideally, there should be 10 periods (1s/100ms). Adding one to compensate possible rounding error.
    assertThat(timeDiffs.size).isLessThan(12)
  }

  @Test
  fun testCanRestart() {
    val ticker = ControllableTicker({}, Duration.ofMillis(10))

    ticker.start()

    sleep(Duration.ofMillis(50).toMillis())

    ticker.stop()

    sleep(Duration.ofMillis(50).toMillis())

    ticker.start()

    sleep(Duration.ofMillis(50).toMillis())

    ticker.stop()
  }
}