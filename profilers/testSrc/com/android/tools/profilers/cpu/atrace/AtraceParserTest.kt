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
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AtraceParserTest {

  @Test
  fun testGetParseRange() {
    val parser = AtraceParser(TEST_PID)
    parser.parse(CpuProfilerTestUtils.getTraceFile("atrace.ctrace"))

    // Value comes from atrace.ctrace file first entry and last entry.
    val expected = Range(EXPECTED_MIN_RANGE, EXPECTED_MAX_RANGE)
    val actual = parser.range
    assertThat(actual.min).isWithin(DELTA).of(expected.min)
    assertThat(actual.max).isWithin(DELTA).of(expected.max)
    assertThat(actual.length).isWithin(DELTA).of(expected.length)
  }

  @Test
  fun testGetCaptureTrees() {
    val parser = AtraceParser(TEST_PID)
    parser.parse(CpuProfilerTestUtils.getTraceFile("atrace.ctrace"))
    val range = parser.range
    val result = parser.captureTrees
    assertThat(result.size).isEqualTo(1)
    val cpuThreadInfo = result.keys.first()
    assertThat(cpuThreadInfo.id).isEqualTo(TEST_PID)
    // Atrace only contains the last X characters, in the log file.
    assertThat(cpuThreadInfo.name).isEqualTo("splayingbitmaps")

    // Base node is a root node that is equivlant to the length of capture.
    val captureNode = result[cpuThreadInfo]!!
    assertThat(captureNode.startGlobal).isEqualTo(range.min.toLong())
    assertThat(captureNode.endGlobal).isEqualTo(range.max.toLong())
    assertThat(captureNode.childCount).isEqualTo(EXPECTED_CHILD_COUNT)
    assertThat(captureNode.getChildAt(0).start).isEqualTo(SINGLE_CHILD_EXPECTED_START)
    assertThat(captureNode.getChildAt(0).end).isEqualTo(SINGLE_CHILD_EXPECTED_END)
    assertThat(captureNode.getChildAt(0).data.name).isEqualTo(EXPECTED_METHOD_NAME)
    assertThat(captureNode.getChildAt(0).start).isGreaterThan(parser.range.min.toLong())
    assertThat(captureNode.getChildAt(0).start).isLessThan(parser.range.max.toLong())
    assertThat(captureNode.getChildAt(0).end).isGreaterThan(parser.range.min.toLong())
    assertThat(captureNode.getChildAt(0).end).isLessThan(parser.range.max.toLong())
  }

  companion object {
    private val DELTA = .00000001

    // Setting const for atrace file in one location so if we update file we can update const in one location.
    private val EXPECTED_MIN_RANGE = 1.1008678125E11
    private val EXPECTED_MAX_RANGE = 1.10097111995E11
    private val SINGLE_CHILD_EXPECTED_START = 110091892263
    private val SINGLE_CHILD_EXPECTED_END = 110091912914
    private val EXPECTED_CHILD_COUNT = 3
    private val EXPECTED_METHOD_NAME = "activityDestroy"
    private val TEST_PID = 23340
  }
}
