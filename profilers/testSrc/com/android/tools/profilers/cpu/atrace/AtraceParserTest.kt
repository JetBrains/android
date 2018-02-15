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
package com.android.tools.profilers.cpu.atrace

import com.android.tools.adtui.model.Range
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.CpuThreadInfo
import com.google.common.collect.Iterables
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class AtraceParserTest {

  val myParser = AtraceParser(TEST_PID)

  @Before
  fun setup() {
    myParser.parse(CpuProfilerTestUtils.getTraceFile("atrace.ctrace"), 0)
  }

  @Test
  fun testGetParseRange() {
    // Value comes from atrace.ctrace file first entry and last entry.
    val expected = Range(EXPECTED_MIN_RANGE, EXPECTED_MAX_RANGE)
    val actual = myParser.range
    assertThat(actual.min).isWithin(DELTA).of(expected.min)
    assertThat(actual.max).isWithin(DELTA).of(expected.max)
    assertThat(actual.length).isWithin(DELTA).of(expected.length)
  }

  @Test
  fun testGetCaptureTrees() {
    val range = myParser.range
    val result = myParser.captureTrees
    assertThat(result).hasSize(20)
    val cpuThreadInfo = Iterables.find(result.keys, {key -> key?.id == TEST_PID } )
    assertThat(cpuThreadInfo.id).isEqualTo(TEST_PID)
    // Atrace only contains the last X characters, in the log file.
    assertThat(cpuThreadInfo.name).isEqualTo("splayingbitmaps")

    // Base node is a root node that is equivlant to the length of capture.
    var captureNode = result.get(cpuThreadInfo)!!
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
    assertThat(captureNode.getChildAt(0).depth).isEqualTo(0)
    assertThat(captureNode.getChildAt(2).getChildAt(0).depth).isEqualTo(1)

  }

  @Test
  fun testGetThreadStateDataSeries() {
    val dataSeries = myParser.threadStateDataSeries
    assertThat(dataSeries).hasSize(THREAD_STATE_SERIES_SIZE)
    assertThat(dataSeries[THREAD_ID]!!.size).isEqualTo(THREAD_STATE_SIZE)
    assertThat(dataSeries[THREAD_ID]!!.get(0).x).isGreaterThan(EXPECTED_MIN_RANGE.toLong())
    // Waking / Runnable = RUNNABLE.
    assertThat(dataSeries[THREAD_ID]!!.get(0).value).isEqualTo(CpuProfilerStage.ThreadState.RUNNABLE_CAPTURED);
    // Running = RUNNING
    assertThat(dataSeries[THREAD_ID]!!.get(1).value).isEqualTo(CpuProfilerStage.ThreadState.RUNNING_CAPTURED);
  }

  @Test
  fun testGetCpuUtilizationDataSeries() {
    val dataSeries = myParser.cpuUtilizationSeries
    val size = 100 / myParser.cpuThreadInfoStates.size.toDouble()
    // No values should exceed the bounds
    for (data in dataSeries) {
      assertThat(data.value).isAtLeast(0)
      assertThat(data.value).isAtMost(100)
      assertThat(data.value % size).isEqualTo(0.0)
    }
  }

  @Test
  fun testGetCpuProcessData() {
    val dataSeries = myParser.cpuThreadInfoStates
    assertThat(dataSeries).hasSize(4)
    for (i in 0..3) {
      assertThat(dataSeries.containsKey(i))
    }
    // Verify that we have a perfd process, null process, then rcu process.
    // Verifying the null process is important as it ensures we render the data properly.
    assertThat(dataSeries[0]!![0].value.name).matches("perfd")
    assertThat(dataSeries[0]!![1].value).isEqualTo(CpuThreadInfo.NULL_THREAD)
    assertThat(dataSeries[0]!![2].value.name).matches("rcu_preempt")

  }

  companion object {
    private val DELTA = .00000001

    // Setting const for atrace file in one location so if we update file we can update const in one location.
    private val EXPECTED_MIN_RANGE = 8.7688546875E10
    private val EXPECTED_MAX_RANGE = 8.7701855499E10
    private val SINGLE_CHILD_EXPECTED_START = 87691109747
    private val SINGLE_CHILD_EXPECTED_END = 87691109965
    private val EXPECTED_CHILD_COUNT = 213
    private val EXPECTED_METHOD_NAME = "setupGridItem"
    private val TEST_PID = 2652
    private val THREAD_ID = 2659
    private val THREAD_STATE_SERIES_SIZE = 20
    private val THREAD_STATE_SIZE = 1316
  }
}
