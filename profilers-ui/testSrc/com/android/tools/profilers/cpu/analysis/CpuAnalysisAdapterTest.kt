/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.profilers.cpu.analysis.CpuAnalysisTabModel.Type.*
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import javax.swing.JLabel

class CpuAnalysisAdapterTest {

  private val modelList = listOf(
    model("All Threads",
          tab(SUMMARY, 1, 2, 3),
          tab(TOP_DOWN, 1, 2, 3),
          tab(FRAMES, 1, 2, 3)),
    model("3 threads",
          tab(SUMMARY, 1, 2, 3),
          tab(TOP_DOWN, 1, 2, 3)),
  )

  @Test
  fun `plain adapter works`() {
    PlainCpuAnalysisAdapter(::simpleTabView).make(modelList).let { res ->
      assertThat(res).hasSize(2)
      assertThat(res[0].name).isEqualTo("All Threads")
      assertThat(res[0].tabs.map { it.name }).containsExactly("Summary", "Top Down", "Frames")
      assertThat(res[1].name).isEqualTo("3 threads")
      assertThat(res[1].tabs.map { it.name }).containsExactly("Summary", "Top Down")
    }
  }

  @Test
  fun `frames-at-top adapter works`() {
    FramesAtTopCpuAnalysisAdapter(::simpleTabView).make(modelList).let { res ->
      assertThat(res).hasSize(3)
      assertThat(res[0].name).isEqualTo("All Threads")
      assertThat(res[0].tabs.map { it.name }).containsExactly("Summary", "Top Down")
      assertThat(res[1].name).isEqualTo("Frames")
      assertThat(res[1].tabs.map { it.name }).containsExactly("All Frames")
      assertThat(res[2].name).isEqualTo("3 threads")
      assertThat(res[2].tabs.map { it.name }).containsExactly("Summary", "Top Down")
    }
  }

  private fun simpleTabView(t: CpuAnalysisTabModel<*>) = JLabel(t.tabType.name)

  private fun<T> model(name: String, vararg tabs: CpuAnalysisTabModel<T>) =
    object : CpuAnalysisModel<T>(name){}.apply { tabs.forEach(::addTabModel) }

  private fun<T> tab(type: CpuAnalysisTabModel.Type, vararg data: T) =
    object : CpuAnalysisTabModel<T>(type){}.apply { data.forEach(dataSeries::add) }
}