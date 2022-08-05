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
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JLabel
import javax.swing.JPanel

class CpuThreadsTooltipViewTest {
  private val timeline = DefaultTimeline()
  private lateinit var cpuThreadsTooltip: CpuThreadsTooltip
  private lateinit var cpuThreadsTooltipView: FakeCpuThreadsTooltipView

  @Before
  fun setUp() {
    cpuThreadsTooltip = CpuThreadsTooltip(timeline)
    cpuThreadsTooltipView = FakeCpuThreadsTooltipView(cpuThreadsTooltip)
    timeline.dataRange.set(0.0, TimeUnit.SECONDS.toMicros(10).toDouble())
    timeline.viewRange.set(0.0, TimeUnit.SECONDS.toMicros(10).toDouble())
  }

  @Test
  fun textUpdateOnThreadChange() {
    val threadSeries = mutableListOf<SeriesData<ThreadState>>().apply {
      add(SeriesData(TimeUnit.SECONDS.toMicros(1), ThreadState.RUNNING))
      add(SeriesData(TimeUnit.SECONDS.toMicros(8), ThreadState.DEAD))
    }

    cpuThreadsTooltip.setThread("myThread", LazyDataSeries { threadSeries })
    var tooltipTime = TimeUnit.SECONDS.toMicros(1).toDouble()
    timeline.tooltipRange.set(tooltipTime, tooltipTime)
    var labels = TreeWalker(cpuThreadsTooltipView.tooltipPanel).descendants().filterIsInstance<JLabel>()
    assertThat(labels).hasSize(5) // time, name, state, duration, details unavailable
    assertThat(labels[0].text).isEqualTo("00:01.000")
    assertThat(labels[1].text).isEqualTo("Thread: myThread")
    assertThat(labels[2].text).isEqualTo("Running")
    assertThat(labels[3].text).isEqualTo("7 s") // 1 to 8 seconds
    assertThat(labels[4].text).isEqualTo("Details Unavailable")

    tooltipTime = TimeUnit.SECONDS.toMicros(9).toDouble() // Should be the last state. Duration is unavailable.
    timeline.tooltipRange.set(tooltipTime, tooltipTime)
    labels = TreeWalker(cpuThreadsTooltipView.tooltipPanel).descendants().filterIsInstance<JLabel>()
    assertThat(labels).hasSize(4) // time, name, state, details unavailable
    assertThat(labels[0].text).isEqualTo("00:09.000")
    assertThat(labels[1].text).isEqualTo("Thread: myThread")
    assertThat(labels[2].text).isEqualTo("Dead")
    assertThat(labels[3].text).isEqualTo("Details Unavailable")

    // Insert capture thread states.
    threadSeries.add(1, SeriesData(TimeUnit.SECONDS.toMicros(7), ThreadState.SLEEPING_CAPTURED))
    tooltipTime = TimeUnit.SECONDS.toMicros(7).toDouble()
    timeline.tooltipRange.set(tooltipTime, tooltipTime)
    // Switch threads.
    cpuThreadsTooltip.setThread("newThread", LazyDataSeries { threadSeries })
    labels = TreeWalker(cpuThreadsTooltipView.tooltipPanel).descendants().filterIsInstance<JLabel>()
    assertThat(labels).hasSize(4) // time, name, state, duration
    assertThat(labels[0].text).isEqualTo("00:07.000")
    assertThat(labels[1].text).isEqualTo("Thread: newThread")
    assertThat(labels[2].text).isEqualTo("Sleeping")
    assertThat(labels[3].text).isEqualTo("1 s") // 1 second until the capture finishes
  }

  private class FakeCpuThreadsTooltipView(tooltip: CpuThreadsTooltip) : CpuThreadsTooltipView(JPanel(), tooltip) {
    val tooltipPanel = createComponent()
  }
}
