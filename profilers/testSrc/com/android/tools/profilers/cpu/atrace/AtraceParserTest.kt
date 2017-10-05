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
import org.junit.Test

import com.google.common.truth.Truth.assertThat

class AtraceParserTest {
  @Test
  fun testGetParseRange() {
    val parser = AtraceParser()
    parser.parse(CpuProfilerTestUtils.getTraceFile("atrace.ctrace"))

    // Value comes from atrace.ctrace file first entry and last entry.
    val expected = Range(1.54451424E8, 1.61470736E8)
    val actual = parser.range
    assertThat(actual.min).isWithin(DELTA).of(expected.min)
    assertThat(actual.max).isWithin(DELTA).of(expected.max)
    assertThat(actual.length).isWithin(DELTA).of(expected.length)
  }

  companion object {
    private val DELTA = .00000001
  }
}
