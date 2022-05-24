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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.DefaultTimeline
import com.android.tools.adtui.model.SeriesData
import com.android.tools.profilers.cpu.systemtrace.CpuKernelTooltip
import com.android.tools.profilers.cpu.systemtrace.CpuThreadSliceInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JLabel
import javax.swing.JPanel

class CpuKernelTooltipViewTest {
  private val timeline = DefaultTimeline()
  private lateinit var tooltip: CpuKernelTooltip
  private lateinit var tooltipView: FakeCpuKernelTooltipView

  @Before
  fun setUp() {
    tooltip = CpuKernelTooltip(timeline, 123)
    tooltipView = FakeCpuKernelTooltipView(tooltip)
    timeline.dataRange.set(0.0, TimeUnit.SECONDS.toMicros(5).toDouble())
    timeline.viewRange.set(0.0, TimeUnit.SECONDS.toMicros(10).toDouble())
  }

  @Test
  fun textUpdateOnRangeChange() {
    val testSeriesData = ArrayList<SeriesData<CpuThreadSliceInfo>>()
    testSeriesData.add(SeriesData(0, CpuThreadSliceInfo(0, "SomeThread", 123, "MyProcess", TimeUnit.SECONDS.toMicros(2))))
    testSeriesData.add(SeriesData(5, CpuThreadSliceInfo.NULL_THREAD))
    val series = LazyDataSeries { testSeriesData }
    tooltip.setCpuSeries(1, series)
    timeline.tooltipRange.set(1.0, 1.0)
    val labels = TreeWalker(tooltipView.tooltipPanel).descendants().filterIsInstance<JLabel>()
    assertThat(labels).hasSize(5)
    assertThat(labels[0].text).isEqualTo("00:00.000")
    assertThat(labels[1].text).isEqualTo("Thread: SomeThread")
    assertThat(labels[2].text).isEqualTo("Process: MyProcess")
    assertThat(labels[3].text).isEqualTo("Duration: 2 s")
    assertThat(labels[4].text).isEqualTo("CPU: 1")
  }

  @Test
  fun otherDetailsAppearOnOtherApps() {
    val testSeriesData = ArrayList<SeriesData<CpuThreadSliceInfo>>()
    testSeriesData.add(SeriesData(0, CpuThreadSliceInfo(0, "SomeThread", 22, "MyProcess")))
    val series = LazyDataSeries { testSeriesData }
    tooltip.setCpuSeries(1, series)
    timeline.tooltipRange.set(1.0, 1.0)
    val labels = TreeWalker(tooltipView.tooltipPanel).descendants().filterIsInstance<JLabel>()
    assertThat(labels.stream().anyMatch { label -> label.text.equals("Other (not selectable)") }).isTrue()
  }

  private class FakeCpuKernelTooltipView(tooltip: CpuKernelTooltip) : CpuKernelTooltipView(JPanel(), tooltip) {
    val tooltipPanel = createComponent()
  }
}
