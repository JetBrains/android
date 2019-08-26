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
import com.android.tools.profilers.cpu.atrace.AtraceTestUtils.Companion.SECONDS_TO_US
import com.android.tools.profilers.cpu.atrace.AtraceTestUtils.Companion.TEST_PID
import com.android.tools.profilers.cpu.atrace.AtraceTestUtils.Companion.convertTimeStamps
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
    val reader = AtraceProducer()
    assertThat(reader.parseFile(file)).isTrue()
    val task = ImportTask(PrintlnImportFeedback())
    myModel = task.importBuffer(reader)
  }

  @Test
  fun testFramePerformance() {
    val bitmapsProcess = myModel.processes[TEST_PID]!!
    val frame = AtraceFrame(bitmapsProcess.threads[0].id, ::convertTimeStamps, SECONDS_TO_US.toLong() * 1, AtraceFrame.FrameThread.MAIN)
    val goodFrameRange = Range(1.0, 1.01)
    val badFrameRange = Range(1.0, 10.0)
    frame.addSlice(bitmapsProcess.threads[0].slices[0], goodFrameRange)
    assertThat(frame.perfClass).isEqualTo(AtraceFrame.PerfClass.GOOD)
    frame.addSlice(bitmapsProcess.threads[0].slices[1], badFrameRange)
    assertThat(frame.perfClass).isEqualTo(AtraceFrame.PerfClass.BAD)
  }

  @Test
  fun testRangeValues() {
    val bitmapsProcess = myModel.processes[TEST_PID]!!
    val frame = AtraceFrame(bitmapsProcess.threads[0].id, ::convertTimeStamps, 0, AtraceFrame.FrameThread.MAIN)
    val smallRange = Range(1.0 + myModel.beginTimestamp, 2.0 + myModel.beginTimestamp)
    val smallUserRange = Range(convertTimeStamps(smallRange.min).toDouble(), convertTimeStamps(smallRange.max).toDouble())
    frame.addSlice(bitmapsProcess.threads[0].slices[0], smallRange)
    validateRange(smallRange, frame.totalRangeSeconds)
    assertThat(smallUserRange.min.toLong()).isEqualTo(frame.startUs)
    assertThat(smallUserRange.max.toLong()).isEqualTo(frame.endUs)
    assertThat(bitmapsProcess.threads[0].slices[0].cpuTime).isWithin(DELTA).of(frame.cpuTimeSeconds)

    val largeRange = Range(3.0 + myModel.beginTimestamp, 6.0 + myModel.beginTimestamp)
    val largeUserRange = Range(convertTimeStamps(largeRange.min).toDouble(), convertTimeStamps(largeRange.max).toDouble())
    val totalRange = Range(smallRange.min, largeRange.max)
    frame.addSlice(bitmapsProcess.threads[0].slices[1], largeRange)
    assertThat(smallUserRange.min.toLong()).isEqualTo(frame.startUs)
    assertThat(largeUserRange.max.toLong()).isEqualTo(frame.endUs)
    assertThat(frame.durationUs).isEqualTo((totalRange.length * SECONDS_TO_US).toLong())
  }

  @Test
  fun associatedFramesPerfClass() {
    val bitmapsProcess = myModel.processes[TEST_PID]!!
    val goodFrame = AtraceFrame(bitmapsProcess.threads[0].id, ::convertTimeStamps, SECONDS_TO_US.toLong() * 1, AtraceFrame.FrameThread.MAIN)
    val badFrame = AtraceFrame(bitmapsProcess.threads[0].id, ::convertTimeStamps, SECONDS_TO_US.toLong() * 1, AtraceFrame.FrameThread.MAIN)
    val goodFrameRange = Range(1.0, 1.01)
    val badFrameRange = Range(1.0, 10.0)
    goodFrame.addSlice(bitmapsProcess.threads[0].slices[0], goodFrameRange)
    assertThat(goodFrame.perfClass).isEqualTo(AtraceFrame.PerfClass.GOOD)
    badFrame.addSlice(bitmapsProcess.threads[0].slices[0], badFrameRange)
    assertThat(badFrame.perfClass).isEqualTo(AtraceFrame.PerfClass.BAD)

    goodFrame.associatedFrame = badFrame
    badFrame.associatedFrame = goodFrame
    assertThat(goodFrame.totalPerfClass).isEqualTo(AtraceFrame.PerfClass.BAD)
    assertThat(badFrame.totalPerfClass).isEqualTo(AtraceFrame.PerfClass.BAD)

    assertThat(AtraceFrame.EMPTY.totalPerfClass).isEqualTo(AtraceFrame.PerfClass.NOT_SET)
  }

  fun validateRange(expected: Range, actual: Range) {
    assertThat(expected.min).isWithin(DELTA).of(actual.min)
    assertThat(expected.max).isWithin(DELTA).of(actual.max)
  }

  @Test
  fun testStoringSlices() {
    val bitmapsProcess = myModel.processes[TEST_PID] ?: error("No process for pid $TEST_PID")
    val frame = AtraceFrame(bitmapsProcess.threads[0].id, ::convertTimeStamps, SECONDS_TO_US.toLong() * 1, AtraceFrame.FrameThread.MAIN)
    val frameRange = Range(1.0, 2.0)
    val slice = bitmapsProcess.threads[0].slices[0]
    frame.addSlice(slice, frameRange)
    assertThat(frame.slices.contains(slice))
  }
}