/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.model.Range
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.Utils
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.analysis.CpuAnalysisTabModel.Type
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito

class CaptureNodeAnalysisModelTest {
  @Test
  fun analysisTabs() {
    val capture = Mockito.mock(CpuCapture::class.java).apply {
      whenever(this.range).thenReturn(Range())
      whenever(this.type).thenReturn(Trace.UserOptions.TraceType.PERFETTO)
    }
    val model = CaptureNodeAnalysisModel(CaptureNode(SingleNameModel("Foo")), capture, Utils::runOnUi)
    val tabs = model.analysisModel.tabModels.map(CpuAnalysisTabModel<*>::getTabType).toSet()
    assertThat(tabs).containsExactly(Type.SUMMARY, Type.FLAME_CHART, Type.TOP_DOWN, Type.BOTTOM_UP, Type.EVENTS)
  }

  @Test
  fun getLongestRunningOccurrences() {
    val capture = Mockito.mock(CpuCapture::class.java).apply {
      whenever(this.range).thenReturn(Range())
    }
    assertThat(CaptureNodeAnalysisModel(ROOT_NODE, capture, Utils::runOnUi).getLongestRunningOccurrences(3)).containsExactly(ROOT_NODE).inOrder()
    assertThat(CaptureNodeAnalysisModel(FOO_1, capture, Utils::runOnUi).getLongestRunningOccurrences(3)).containsExactly(FOO_2, FOO_1).inOrder()
    assertThat(CaptureNodeAnalysisModel(BAR_11, capture, Utils::runOnUi).getLongestRunningOccurrences(3)).containsExactly(BAR_23, BAR_12, BAR_11).inOrder()
  }

  @Test
  fun getAllOccurrencesStats() {
    val capture = Mockito.mock(CpuCapture::class.java).apply {
      whenever(this.range).thenReturn(Range())
    }
    val stats = CaptureNodeAnalysisModel(BAR_11, capture, Utils::runOnUi).allOccurrenceStats
    assertThat(stats.count).isEqualTo(5)
    assertThat(stats.average).isWithin(EPSILON).of(13.2)
    assertThat(stats.max).isEqualTo(35)
    assertThat(stats.min).isEqualTo(1)
    assertThat(stats.standardDeviation).isWithin(EPSILON).of(11.873)
  }

  @Test
  fun `models with equal fields are equal`() {
    val capture1 = Mockito.mock(CpuCapture::class.java)
    val capture2 = Mockito.mock(CpuCapture::class.java)
    val m1 = CaptureNodeAnalysisModel(ROOT_NODE, capture1, Utils::runOnUi)
    val m2 = CaptureNodeAnalysisModel(ROOT_NODE, capture2, Utils::runOnUi)
    val m3 = CaptureNodeAnalysisModel(FOO_1, capture1, Utils::runOnUi)
    val m4 = CaptureNodeAnalysisModel(ROOT_NODE, capture1, Utils::runOnUi)
    assertThat(m1).isEqualTo(m4)
    assertThat(setOf(m1, m2, m3, m4)).hasSize(3)
  }

  companion object {
    private const val EPSILON = 0.001
    /**
     * Build a capture node tree with different timestamp.
     *
     * Name    Start-End (us) Duration
     * -------------------------------
     * Root    ( 0-99)        99
     * |-Foo   ( 0-35)        35
     *   |-Bar ( 0-10)        10
     *   |-Bar (20-35)        15
     * |-Foo   (40-90)        50
     *   |-Bar (40-45)         5
     *   |-Bar (50-51)         1
     *   |-Bar (55-90)        35
     */
    private val BAR_11 = CaptureNode(SingleNameModel("Bar")).apply {
      startGlobal = 0
      endGlobal = 10
    }
    private val BAR_12 = CaptureNode(SingleNameModel("Bar")).apply {
      startGlobal = 20
      endGlobal = 35
    }
    private val BAR_21 = CaptureNode(SingleNameModel("Bar")).apply {
      startGlobal = 40
      endGlobal = 45
    }
    private val BAR_22 = CaptureNode(SingleNameModel("Bar")).apply {
      startGlobal = 50
      endGlobal = 51
    }
    private val BAR_23 = CaptureNode(SingleNameModel("Bar")).apply {
      startGlobal = 55
      endGlobal = 90
    }
    private val FOO_1 = CaptureNode(SingleNameModel("Foo")).apply {
      startGlobal = 0
      endGlobal = 35
      addChild(BAR_11)
      addChild(BAR_12)
    }
    private val FOO_2 = CaptureNode(SingleNameModel("Foo")).apply {
      startGlobal = 40
      endGlobal = 90
      addChild(BAR_21)
      addChild(BAR_22)
      addChild(BAR_23)
    }
    private val ROOT_NODE = CaptureNode(SingleNameModel("Root")).apply {
      startGlobal = 0
      endGlobal = 99
      addChild(FOO_1)
      addChild(FOO_2)
    }
  }
}