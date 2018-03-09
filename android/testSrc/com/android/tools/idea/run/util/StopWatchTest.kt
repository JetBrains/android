/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.util

import org.junit.After
import org.junit.Before
import org.junit.Test

import com.google.common.truth.Truth.assertThat

class StopWatchTest {
  private  lateinit var testTimeSource: TestTimeSource

  @Before
  fun setUp() {
    testTimeSource = TestTimeSource()
    StopWatchTimeSource.overrideDefault(testTimeSource)
  }

  @After
  fun cleanUp() {
    StopWatchTimeSource.resetDefault()
  }

  @Test
  fun stopWatchShouldStartByDefault() {
    val sw = StopWatch()

    assertThat(sw.duration.toMillis()).isEqualTo(0)
    testTimeSource.advance(10)
    assertThat(sw.duration.toMillis()).isEqualTo(10)

    testTimeSource.advance(20)
    assertThat(sw.duration.toMillis()).isEqualTo(30)
  }

  @Test
  fun stopWatchStopShouldWork() {
    val sw = StopWatch()
    testTimeSource.advance(10)

    assertThat(sw.duration.toMillis()).isEqualTo(10)
    sw.stop()

    testTimeSource.advance(10)
    assertThat(sw.duration.toMillis()).isEqualTo(10)
  }

  @Test
  fun stopWatchRestartShouldResetTime() {
    val sw = StopWatch()
    testTimeSource.advance(10)
    assertThat(sw.duration.toMillis()).isEqualTo(10)

    sw.restart()
    assertThat(sw.duration.toMillis()).isEqualTo(0)

    testTimeSource.advance(10)
    assertThat(sw.duration.toMillis()).isEqualTo(10)
  }

  private class TestTimeSource : StopWatchTimeSource.StopWatchTimeSourceOverride {
    private var myTicks: Long = 0

    override val currentTimeMillis: Long
      get() = myTicks

    fun advance(ticks: Long) {
      myTicks += ticks
    }
  }
}
