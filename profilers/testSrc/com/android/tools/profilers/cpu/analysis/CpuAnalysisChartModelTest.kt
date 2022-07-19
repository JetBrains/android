/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.model.Range
import com.android.tools.perflib.vmtrace.ClockType
import com.android.tools.profilers.Utils
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CpuAnalysisChartModelTest {

  @Test
  fun selectionRangeDecoupledFromAxisComponentModel() {
    // Note: The numbers used here are taken from a live capture that illustrated a bug, this test validates the fix for that bug.
    // Changing the numbers may result in this test passing when it shouldn't
    val minRange = 2930527342743.0
    val maxRange = 2930531342743.0
    val delta = 80000.0
    val selectionRange = Range(minRange, maxRange)
    val capture = CpuProfilerTestUtils.getValidCapture()
    val model = CpuAnalysisChartModel<CaptureNodeAnalysisModel>(CpuAnalysisTabModel.Type.FLAME_CHART, selectionRange, capture,
                                                                { listOf() }, Utils::runOnUi)
    selectionRange.set(minRange+delta, maxRange)
    model.axisComponentModel.updateImmediately()
    assertThat(selectionRange.min).isEqualTo(minRange + delta)
    assertThat(selectionRange.max).isEqualTo(maxRange)
  }

  @Test
  fun selectionRangeDecoupledFromClockType() {
    // Note: Numbers taken from valid capture.
    val minRange = 2930527342743.0
    val maxRange = 2930531342743.0
    val selectionRange = Range(minRange, maxRange)
    val capture = CpuProfilerTestUtils.getValidCapture()
    val model = CpuAnalysisChartModel<CpuCapture>(CpuAnalysisTabModel.Type.TOP_DOWN, selectionRange, capture,
                                                  { capture.captureNodes }, Utils::runOnUi)
    model.dataSeries.add(capture)
    model.axisComponentModel.updateImmediately()
    // Test Global
    assertThat(selectionRange.min).isEqualTo(minRange)
    assertThat(selectionRange.max).isEqualTo(maxRange)
    assertThat(selectionRange.isSameAs(model.captureConvertedRange)).isTrue()
    assertThat(model.clockType).isEqualTo(ClockType.GLOBAL)

    // Test Thread
    model.clockType = ClockType.THREAD
    assertThat(selectionRange.min).isEqualTo(minRange)
    assertThat(selectionRange.max).isEqualTo(maxRange)
    assertThat(selectionRange.isSameAs(model.captureConvertedRange)).isFalse()
    assertThat(model.clockType).isEqualTo(ClockType.THREAD)
  }
}