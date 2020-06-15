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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CpuAnalysisModelTest {
  @Test
  fun noDupeTabModel() {
    val model = CpuAnalysisModel<Int>("foo")
    model.addTabModel(CpuAnalysisTabModel(CpuAnalysisTabModel.Type.SUMMARY))

    assertThat(model.tabSize).isEqualTo(1)
    assertThat(model.getTabModelAt(0).tabType).isEqualTo(CpuAnalysisTabModel.Type.SUMMARY)

    model.addTabModel(CpuAnalysisTabModel(CpuAnalysisTabModel.Type.SUMMARY))
    assertThat(model.tabSize).isEqualTo(1)
  }

  @Test
  fun getTabModelAt() {
    val model = CpuAnalysisModel<Int>("foo")
    model.addTabModel(CpuAnalysisTabModel(CpuAnalysisTabModel.Type.SUMMARY))
    model.addTabModel(CpuAnalysisTabModel(CpuAnalysisTabModel.Type.BOTTOM_UP))
    model.addTabModel(CpuAnalysisTabModel(CpuAnalysisTabModel.Type.FLAME_CHART))
    model.addTabModel(CpuAnalysisTabModel(CpuAnalysisTabModel.Type.TOP_DOWN))

    assertThat(model.getTabModelAt(0).tabType).isEqualTo(CpuAnalysisTabModel.Type.SUMMARY)
    assertThat(model.getTabModelAt(1).tabType).isEqualTo(CpuAnalysisTabModel.Type.TOP_DOWN)
    assertThat(model.getTabModelAt(2).tabType).isEqualTo(CpuAnalysisTabModel.Type.FLAME_CHART)
    assertThat(model.getTabModelAt(3).tabType).isEqualTo(CpuAnalysisTabModel.Type.BOTTOM_UP)
  }

  @Test
  fun tabsAreSorted() {
    val model = CpuAnalysisModel<Int>("foo")
    model.addTabModel(CpuAnalysisTabModel(CpuAnalysisTabModel.Type.FLAME_CHART))
    model.addTabModel(CpuAnalysisTabModel(CpuAnalysisTabModel.Type.SUMMARY))

    val tabs = model.tabModels.toList()
    assertThat(tabs.size).isEqualTo(2)
    assertThat(tabs[0].tabType).isEqualTo(CpuAnalysisTabModel.Type.SUMMARY)
    assertThat(tabs[1].tabType).isEqualTo(CpuAnalysisTabModel.Type.FLAME_CHART)
  }

  @Test
  fun mergeWith() {
    val model1 = CpuAnalysisModel<Int>("foo")
    val summary1 = CpuAnalysisTabModel<Int>(CpuAnalysisTabModel.Type.SUMMARY)
    val logs = CpuAnalysisTabModel<Int>(CpuAnalysisTabModel.Type.LOGS)
    summary1.dataSeries.add(1)
    logs.dataSeries.add(2)
    model1.addTabModel(summary1)
    model1.addTabModel(logs)

    val model2 = CpuAnalysisModel<Int>("bar")
    val summary2 = CpuAnalysisTabModel<Int>(CpuAnalysisTabModel.Type.SUMMARY)
    val topDown = CpuAnalysisTabModel<Int>(CpuAnalysisTabModel.Type.TOP_DOWN)
    summary2.dataSeries.add(3)
    topDown.dataSeries.add(4)
    model2.addTabModel(summary2)
    model2.addTabModel(topDown)

    val mergedModel = model1.mergeWith(model2)
    assertThat(mergedModel).isSameAs(model1)
    assertThat(mergedModel.tabSize).isEqualTo(3)
    val tabs = mergedModel.tabModels.toList()
    assertThat(tabs[0].tabType).isEqualTo(CpuAnalysisTabModel.Type.SUMMARY)
    assertThat(tabs[0].dataSeries).containsExactly(1, 3)
    assertThat(tabs[1].tabType).isEqualTo(CpuAnalysisTabModel.Type.TOP_DOWN)
    assertThat(tabs[1].dataSeries).containsExactly(4)
    assertThat(tabs[2].tabType).isEqualTo(CpuAnalysisTabModel.Type.LOGS)
    assertThat(tabs[2].dataSeries).containsExactly(2)
  }
}