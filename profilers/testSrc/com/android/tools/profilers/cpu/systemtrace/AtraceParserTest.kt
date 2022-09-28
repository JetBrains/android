/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.adtui.model.Range
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.MainProcessSelector
import com.android.tools.profilers.cpu.ThreadState
import com.android.tools.profilers.cpu.systemtrace.SystemTraceCpuCaptureBuilder.Companion.UTILIZATION_BUCKET_LENGTH_US
import com.google.common.collect.Iterables
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class AtraceParserTest {

  val myParser = AtraceParser(MainProcessSelector(idHint = TEST_PID))
  lateinit var myCapture: CpuCapture

  @Before
  fun setup() {
    myCapture = myParser.parse(CpuProfilerTestUtils.getTraceFile("atrace.ctrace"), 0)
  }

  @Test
  fun testGetParseRange() {
    // Value comes from atrace.ctrace file first entry and last entry.
    val expected = Range(EXPECTED_MIN_RANGE, EXPECTED_MAX_RANGE)
    val actual = myCapture.range
    assertThat(actual.min).isWithin(DELTA).of(expected.min)
    assertThat(actual.max).isWithin(DELTA).of(expected.max)
    assertThat(actual.length).isWithin(DELTA).of(expected.length)
  }

  @Test
  fun testGetCaptureTrees() {
    val range = myCapture.range

    assertThat(myCapture.captureNodes).hasSize(20)

    val cpuThreadInfo = Iterables.find(myCapture.threads) { t -> t?.id == TEST_PID }
    assertThat(cpuThreadInfo.id).isEqualTo(TEST_PID)
    // Atrace only contains the last X characters, in the log file.
    assertThat(cpuThreadInfo.name).isEqualTo("splayingbitmaps")
    assertThat(cpuThreadInfo.id).isEqualTo(TEST_PID)
    // Validate capture trees sets the process name and id for threads.
    assertThat(cpuThreadInfo).isInstanceOf(CpuThreadSliceInfo::class.java)
    val cpuProcessInfo = cpuThreadInfo as CpuThreadSliceInfo
    assertThat(cpuProcessInfo.processName).isEqualTo("splayingbitmaps")
    assertThat(cpuProcessInfo.processId).isEqualTo(TEST_PID)
    assertThat(cpuProcessInfo.isMainThread).isTrue()

    // Base node is a root node that is equivalent to the length of capture.
    val captureNode = myCapture.getCaptureNode(TEST_PID)!!
    assertThat(captureNode.startGlobal).isEqualTo(range.min.toLong())
    assertThat(captureNode.endGlobal).isEqualTo(range.max.toLong())
    assertThat(captureNode.childCount).isEqualTo(EXPECTED_CHILD_COUNT)
    assertThat(captureNode.getChildAt(0).start).isEqualTo(SINGLE_CHILD_EXPECTED_START)
    assertThat(captureNode.getChildAt(0).end).isEqualTo(SINGLE_CHILD_EXPECTED_END)
    assertThat(captureNode.getChildAt(0).data.name).isEqualTo(EXPECTED_METHOD_NAME)
    assertThat(captureNode.getChildAt(0).start).isGreaterThan(range.min.toLong())
    assertThat(captureNode.getChildAt(0).start).isLessThan(range.max.toLong())
    assertThat(captureNode.getChildAt(0).end).isGreaterThan(range.min.toLong())
    assertThat(captureNode.getChildAt(0).end).isLessThan(range.max.toLong())
    assertThat(captureNode.getChildAt(0).depth).isEqualTo(1)
    assertThat(captureNode.getChildAt(2).getChildAt(0).depth).isEqualTo(2)
  }

  @Test
  fun testGetCaptureTreesSetsThreadTime() {
    val captureNode = myCapture.getCaptureNode(TEST_PID)!!
    // Grab the element at index 1 and use its child because, the child is the element that has idle cpu time.
    val child = captureNode.getChildAt(1)
    // Validate our child's thread time starts at our global start.
    assertThat(child.startThread).isEqualTo(child.startGlobal)
    // Validate our end time does not equal our global end time.
    assertThat(child.endThread).isNotEqualTo(child.endGlobal)
    assertThat(child.endThread).isEqualTo(EXPECTED_THREAD_END_TIME)
  }

  @Test
  fun testGetThreadStateDataSeries() {
    val dataSeries = myCapture.systemTraceData!!.getThreadStatesForThread(THREAD_ID)
    assertThat(dataSeries.size).isEqualTo(THREAD_STATE_SIZE)
    assertThat(dataSeries[0].x).isGreaterThan(EXPECTED_MIN_RANGE.toLong())
    // Waking / Runnable = RUNNABLE.
    assertThat(dataSeries[0].value).isEqualTo(ThreadState.RUNNABLE_CAPTURED)
    // Running = RUNNING
    assertThat(dataSeries[1].value).isEqualTo(ThreadState.RUNNING_CAPTURED)
  }

  @Test
  fun testGetCpuUtilizationDataSeries() {
    val dataSeries = myCapture.systemTraceData!!.cpuUtilizationSeries
    var avg = 0.0
    // No values should exceed the bounds
    for (data in dataSeries) {
      assertThat(data.value).isAtLeast(0)
      assertThat(data.value).isAtMost(100)
      avg += data.value.toDouble()
    }
    assertThat(avg/dataSeries.size).isWithin(.01).of(23.05)
  }

  @Test
  fun testGetCpuProcessData() {
    for (i in 0..3) {
      val dataSeries = myCapture.systemTraceData!!.getCpuThreadSliceInfoStates(i)
      assertThat(dataSeries).isNotEmpty()
    }
    // Verify that we have a perfd process, null process, then rcu process.
    // Verifying the null process is important as it ensures we render the data properly.
    val threadNames = myCapture.systemTraceData!!.getCpuThreadSliceInfoStates(0).map { it.value.name }
    assertThat(threadNames).containsAllOf("perfd", CpuThreadSliceInfo.NULL_THREAD.name, "rcu_preempt")
  }

  @Test
  fun missingDataCaptureReturnsMissingdata() {
    val parser = AtraceParser()
    val capture = parser.parse(CpuProfilerTestUtils.getTraceFile("atrace_processid_1.ctrace"), 0)

    assertThat(capture.systemTraceData!!.isMissingData).isTrue()
  }

  @Test
  fun atraceCpuUtilizationBucketed() {
    // Cpu utilization is computed at the same time cpu slices are generated, this makes it challenging to test in isolation.
    // Here we take an already parsed capture and test it has utilization series, as well as the max value in the series.
    val series = myCapture.systemTraceData!!.cpuUtilizationSeries
    assertThat(series).hasSize(268)
    // To test the bucketing we verify the delta of each point in the series is the bucket time.
    for (i in 1 until series.size) {
      assertThat(series[i].x - series[i - 1].x).isEqualTo(UTILIZATION_BUCKET_LENGTH_US)
    }
  }

  companion object {
    private val DELTA = .00000001

    // Setting const for atrace file in one location so if we update file we can update const in one location.
    private val EXPECTED_MIN_RANGE = 8.7688546852E10
    private val EXPECTED_MAX_RANGE = 8.7701855476E10
    private val SINGLE_CHILD_EXPECTED_START = 87691109724
    private val SINGLE_CHILD_EXPECTED_END = 87691109942
    private val EXPECTED_THREAD_END_TIME = 87691120728
    private val EXPECTED_CHILD_COUNT = 213
    private val EXPECTED_METHOD_NAME = "setupGridItem"
    private val TEST_PID = 2652
    private val THREAD_ID = 2659
    private val THREAD_STATE_SERIES_SIZE = 20
    private val THREAD_STATE_SIZE = 1317
  }
}
