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

import com.android.tools.adtui.model.Range
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.atrace.AtraceTestUtils.Companion.DELTA
import com.android.tools.profilers.cpu.atrace.AtraceTestUtils.Companion.RENDER_THREAD_ID
import com.android.tools.profilers.cpu.atrace.AtraceTestUtils.Companion.SECONDS_TO_US
import com.android.tools.profilers.cpu.atrace.AtraceTestUtils.Companion.TEST_PID
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import trebuchet.model.Model
import trebuchet.task.ImportTask
import trebuchet.util.PrintlnImportFeedback

class AtraceFrameTest {

  private lateinit var myModel: Model;

  @Before
  fun setup() {
    val file = CpuProfilerTestUtils.getTraceFile("atrace.ctrace")
    val reader = AtraceDecompressor(file)
    val task = ImportTask(PrintlnImportFeedback())
    myModel = task.importBuffer(reader)
  }

  @Test
  fun testFramePerformance() {
    val frame = AtraceFrame(myModel)
    val bitmapsProcess = myModel.processes[TEST_PID]!!
    val goodFrameRange = Range(1.0, 1.01)
    val badFrameRange = Range(1.0, 2.0)
    frame.addSlice(bitmapsProcess.threads[0].slices[0], goodFrameRange, bitmapsProcess.threads[0])
    assertThat(frame.perfClass).isEqualTo(AtraceFrame.PerfClass.GOOD)
    frame.addSlice(bitmapsProcess.threads[0].slices[1], badFrameRange, bitmapsProcess.threads[0])
    assertThat(frame.perfClass).isEqualTo(AtraceFrame.PerfClass.BAD)
  }

  @Test
  fun testRangeValues() {
    val frame = AtraceFrame(myModel)
    val bitmapsProcess = myModel.processes[TEST_PID]!!
    val smallRange = Range(1.0 + myModel.beginTimestamp, 2.0 + myModel.beginTimestamp)
    var smallUserRange = Range((1.0 + myModel.parentTimestamp) * SECONDS_TO_US, (2.0 + myModel.parentTimestamp) * SECONDS_TO_US)
    frame.addSlice(bitmapsProcess.threads[0].slices[0], smallRange, bitmapsProcess.threads[0])
    validateRange(smallRange, frame.totalRangeSeconds)
    assertThat(frame.ranges).hasSize(1)
    validateRange(smallRange, frame.ranges[0])
    assertThat(smallUserRange.min.toLong()).isEqualTo(frame.startUs)
    assertThat(smallUserRange.max.toLong()).isEqualTo(frame.endUs)
    validateRange(smallUserRange, frame.totalRangeProcessTime)
    validateRange(smallUserRange, frame.uiThreadRangeProcessTime)
    validateRange(Range(0.0, 0.0), frame.renderThreadRangeProcessTime)
    assertThat(bitmapsProcess.threads[0].slices[0].cpuTime).isWithin(DELTA).of(frame.cpuTimeSeconds)

    val largeRange = Range(3.0 + myModel.beginTimestamp, 6.0 + myModel.beginTimestamp)
    var largeUserRange = Range((3.0 + myModel.parentTimestamp) * SECONDS_TO_US, (6.0 + myModel.parentTimestamp) * SECONDS_TO_US)
    val totalRange = Range(smallRange.min, largeRange.max)
    val totalUserRange = Range(smallUserRange.min, largeUserRange.max)
    frame.addSlice(bitmapsProcess.threads[RENDER_THREAD_ID].slices[0], largeRange, bitmapsProcess.threads[RENDER_THREAD_ID])
    assertThat(frame.ranges).hasSize(2)
    validateRange(totalRange, frame.totalRangeSeconds)
    validateRange(totalUserRange, frame.totalRangeProcessTime)
    validateRange(smallUserRange, frame.uiThreadRangeProcessTime)
    validateRange(largeUserRange, frame.renderThreadRangeProcessTime)
    assertThat(smallUserRange.min.toLong()).isEqualTo(frame.startUs)
    assertThat(largeUserRange.max.toLong()).isEqualTo(frame.endUs)
    assertThat(frame.slices).hasSize(2)
    assertThat(frame.schedSlice).hasSize(
        bitmapsProcess.threads[0].schedSlices.size +
            bitmapsProcess.threads[RENDER_THREAD_ID].schedSlices.size
    )
    assertThat(frame.durationUs).isEqualTo((totalRange.length * SECONDS_TO_US).toLong())
  }

  fun validateRange(expected: Range, actual: Range) {
    assertThat(expected.min).isWithin(DELTA).of(actual.min)
    assertThat(expected.max).isWithin(DELTA).of(actual.max)
  }
}