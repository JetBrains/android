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
package com.android.tools.profilers.cpu.systemtrace

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit

class SystemTraceFrameTest {

  private companion object {
    private val GOOD = TraceEventModel("event",
                                       TimeUnit.SECONDS.toMicros(1),
                                       TimeUnit.SECONDS.toMicros(2),
                                       TimeUnit.SECONDS.toMicros(1),
                                       emptyList())

    val BAD = TraceEventModel("event",
                              TimeUnit.SECONDS.toMicros(3),
                              TimeUnit.SECONDS.toMicros(10),
                              TimeUnit.SECONDS.toMicros(7),
                              emptyList())

    private val LONG_FRAME_TIME = TimeUnit.SECONDS.toMicros(5)
  }

  @Test
  fun testFramePerformance_Good() {
    val frame = SystemTraceFrame(GOOD, LONG_FRAME_TIME, SystemTraceFrame.FrameThread.MAIN)
    assertThat(frame.perfClass).isEqualTo(SystemTraceFrame.PerfClass.GOOD)
  }

  @Test
  fun testFramePerformance_Bad() {
    val frame = SystemTraceFrame(BAD, LONG_FRAME_TIME, SystemTraceFrame.FrameThread.MAIN)
    assertThat(frame.perfClass).isEqualTo(SystemTraceFrame.PerfClass.BAD)
  }

  @Test
  fun associatedFramesPerfClass() {
    val goodFrame = SystemTraceFrame(GOOD, LONG_FRAME_TIME, SystemTraceFrame.FrameThread.MAIN)
    val badFrame = SystemTraceFrame(BAD, LONG_FRAME_TIME, SystemTraceFrame.FrameThread.MAIN)

    goodFrame.associatedFrame = badFrame
    badFrame.associatedFrame = goodFrame
    assertThat(goodFrame.totalPerfClass).isEqualTo(SystemTraceFrame.PerfClass.BAD)
    assertThat(badFrame.totalPerfClass).isEqualTo(SystemTraceFrame.PerfClass.BAD)

    assertThat(SystemTraceFrame.EMPTY.totalPerfClass).isEqualTo(
      SystemTraceFrame.PerfClass.NOT_SET)
  }
}