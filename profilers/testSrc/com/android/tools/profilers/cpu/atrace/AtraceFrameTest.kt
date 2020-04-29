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
import com.android.tools.profilers.cpu.atrace.AtraceTestUtils.Companion.convertTimeStamps
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import trebuchet.model.SchedSlice
import trebuchet.model.base.SliceGroup

class AtraceFrameTest {

  @Test
  fun testFramePerformance_Good() {
    val goodSlice = TestSliceGroup(SECONDS_TO_US * 0.25, SECONDS_TO_US * 0.75, SECONDS_TO_US * 0.6)
    val frame = AtraceFrame(goodSlice, ::convertTimeStamps, SECONDS_TO_US.toLong() * 1, AtraceFrame.FrameThread.MAIN)
    assertThat(frame.perfClass).isEqualTo(AtraceFrame.PerfClass.GOOD)
  }

  @Test
  fun testFramePerformance_Bad() {
    val badSlice = TestSliceGroup(SECONDS_TO_US * 0.25, SECONDS_TO_US * 1.5, SECONDS_TO_US * 0.9)
    val frame = AtraceFrame(badSlice, ::convertTimeStamps, SECONDS_TO_US.toLong() * 1, AtraceFrame.FrameThread.MAIN)
    assertThat(frame.perfClass).isEqualTo(AtraceFrame.PerfClass.BAD)
  }

  @Test
  fun associatedFramesPerfClass() {
    val goodSlice = TestSliceGroup(SECONDS_TO_US * 0.25, SECONDS_TO_US * 0.75, SECONDS_TO_US * 0.6)
    val goodFrame = AtraceFrame(goodSlice, ::convertTimeStamps, SECONDS_TO_US.toLong() * 1, AtraceFrame.FrameThread.MAIN)

    val badSlice = TestSliceGroup(SECONDS_TO_US * 0.25, SECONDS_TO_US * 1.5, SECONDS_TO_US * 0.9)
    val badFrame = AtraceFrame(badSlice, ::convertTimeStamps, SECONDS_TO_US.toLong() * 1, AtraceFrame.FrameThread.MAIN)

    goodFrame.associatedFrame = badFrame
    badFrame.associatedFrame = goodFrame
    assertThat(goodFrame.totalPerfClass).isEqualTo(AtraceFrame.PerfClass.BAD)
    assertThat(badFrame.totalPerfClass).isEqualTo(AtraceFrame.PerfClass.BAD)

    assertThat(AtraceFrame.EMPTY.totalPerfClass).isEqualTo(AtraceFrame.PerfClass.NOT_SET)
  }

  private inner class TestSliceGroup(override val startTime: Double,
                                     override val endTime: Double,
                                     override val cpuTime: Double) : SliceGroup {
    override val children: List<SliceGroup> = listOf()
    override val scheduledSlices: List<SchedSlice> = listOf()
    override val name: String = ""
    override val didNotFinish: Boolean = false

  }
}