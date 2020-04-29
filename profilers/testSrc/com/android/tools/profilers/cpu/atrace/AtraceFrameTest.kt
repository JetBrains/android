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
package com.android.tools.profilers.cpu.atrace

import com.android.tools.profilers.cpu.atrace.AtraceTestUtils.Companion.SECONDS_TO_US
import com.android.tools.profilers.systemtrace.TraceEventModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AtraceFrameTest {

  private companion object {
    private val GOOD = TraceEventModel("event",
                                    SECONDS_TO_US.toLong() * 1,
                                    SECONDS_TO_US.toLong() * 2,
                                    SECONDS_TO_US.toLong() * 1,
                                    emptyList())

    val BAD = TraceEventModel("event",
                                   SECONDS_TO_US.toLong() * 3,
                                   SECONDS_TO_US.toLong() * 10,
                                   SECONDS_TO_US.toLong() * 7,
                                   emptyList())

    private val LONG_FRAME_TIME = SECONDS_TO_US.toLong() * 5
  }

  @Test
  fun testFramePerformance_Good() {
    val frame = AtraceFrame(GOOD, LONG_FRAME_TIME, AtraceFrame.FrameThread.MAIN)
    assertThat(frame.perfClass).isEqualTo(AtraceFrame.PerfClass.GOOD)
  }

  @Test
  fun testFramePerformance_Bad() {
    val frame = AtraceFrame(BAD, LONG_FRAME_TIME, AtraceFrame.FrameThread.MAIN)
    assertThat(frame.perfClass).isEqualTo(AtraceFrame.PerfClass.BAD)
  }

  @Test
  fun associatedFramesPerfClass() {
    val goodFrame = AtraceFrame(GOOD, LONG_FRAME_TIME, AtraceFrame.FrameThread.MAIN)
    val badFrame = AtraceFrame(BAD, LONG_FRAME_TIME, AtraceFrame.FrameThread.MAIN)

    goodFrame.associatedFrame = badFrame
    badFrame.associatedFrame = goodFrame
    assertThat(goodFrame.totalPerfClass).isEqualTo(AtraceFrame.PerfClass.BAD)
    assertThat(badFrame.totalPerfClass).isEqualTo(AtraceFrame.PerfClass.BAD)

    assertThat(AtraceFrame.EMPTY.totalPerfClass).isEqualTo(AtraceFrame.PerfClass.NOT_SET)
  }
}