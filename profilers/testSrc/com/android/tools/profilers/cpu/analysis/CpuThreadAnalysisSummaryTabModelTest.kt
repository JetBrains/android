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
import com.android.tools.adtui.model.DefaultTimeline
import com.android.tools.adtui.model.MultiSelectionModel
import com.android.tools.profilers.Utils
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.CpuThreadInfo
import com.android.tools.profilers.cpu.CpuThreadTrackModel
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito

class CpuThreadAnalysisSummaryTabModelTest {
  @Test
  fun nodesInRange() {
    val timeline = DefaultTimeline().apply {
      dataRange.set(0.0, 100.0)
      selectionRange.set(10.0, 20.0)
    }
    val capture = Mockito.mock(CpuCapture::class.java).apply {
      whenever(getCaptureNode(123)).thenReturn(ROOT)
    }
    val cpuThreadTrackModel = CpuThreadTrackModel(
      capture,
      CpuThreadInfo(123, "foo"),
      timeline,
      MultiSelectionModel(),
      Utils::runOnUi)
    val model = CpuThreadAnalysisSummaryTabModel(timeline.dataRange, timeline.selectionRange).apply {
      dataSeries.add(cpuThreadTrackModel)
    }
    assertThat(model.getTopNodesInSelectionRange(1).map { it.data.name }).containsExactly("3")
    assertThat(model.getTopNodesInSelectionRange(10).map { it.data.name }).containsExactly("3", "1")
  }

  companion object {
    val ROOT = CaptureNode(SingleNameModel("Root")).apply {
      startGlobal = 0
      endGlobal = 100
      depth = 0
      addChild(CaptureNode(SingleNameModel("1")).apply {
        startGlobal = 0
        endGlobal = 30
        depth = 1
      })
      addChild(CaptureNode(SingleNameModel("2")).apply {
        startGlobal = 2
        endGlobal = 5
        depth = 1
      })
      addChild(CaptureNode(SingleNameModel("3")).apply {
        startGlobal = 15
        endGlobal = 50
        depth = 1
      })
    }
  }
}