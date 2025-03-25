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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.model.DefaultTimeline
import com.android.tools.adtui.model.MultiSelectionModel
import com.android.tools.adtui.model.Range
import com.android.tools.profilers.cpu.analysis.CpuAnalyzable
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineEvent
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineModel
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineTooltip
import com.android.tools.profilers.cpu.systemtrace.SystemTraceCpuCapture
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import perfetto.protos.PerfettoTrace
import javax.swing.JPanel

class AndroidFrameTimelineTooltipViewTest {
  @Test
  fun `tooltip updates on change`() {
    val timeline = DefaultTimeline().apply {
      dataRange.set(0.0, 4000.0)
      viewRange.set(0.0, 4000.0)
    }
    val model = AndroidFrameTimelineModel(listOf(FAKE_EVENT_0, FAKE_EVENT_1), listOf(), timeline.viewRange,
                                          FAKE_SELECTION_MODEL as MultiSelectionModel<CpuAnalyzable<*>>, FAKE_CAPTURE)
    val tooltip = AndroidFrameTimelineTooltip(timeline, model)
    val tooltipView = AndroidFrameTimelineTooltipView(JPanel(), tooltip)
    run {
      model.activeSeriesIndex = 0

      timeline.tooltipRange.set(1.0, 1.0)
      assertThat(tooltipView.headingText).isEqualTo("00:00.000")
      assertThat(tooltipView.container.isVisible).isFalse()

      timeline.tooltipRange.set(1001.0, 1200.0)
      assertThat(tooltipView.headingText).isEqualTo("00:00.001")
      assertThat(tooltipView.container.isVisible).isTrue()
      assertThat(tooltipView.frameLabel.text).isEqualTo("Frame: 42")
      assertThat(tooltipView.typeLabel.text).isEqualTo("Deadline missed")
      assertThat(tooltipView.startLabel.text).isEqualTo("Start: 00:00.000")
      assertThat(tooltipView.expectedLabel.text).isEqualTo("Expected end: 00:00.001")
      assertThat(tooltipView.actualLabel.text).isEqualTo("Actual end: 00:00.002")
    }

    run {
      model.activeSeriesIndex = 1

      assertThat(tooltipView.headingText).isEqualTo("00:00.001")
      assertThat(tooltipView.container.isVisible).isFalse()

      timeline.tooltipRange.set(1501.0, 2000.0)
      assertThat(tooltipView.headingText).isEqualTo("00:00.001")
      assertThat(tooltipView.container.isVisible).isTrue()
      assertThat(tooltipView.frameLabel.text).isEqualTo("Frame: 43")
      assertThat(tooltipView.typeLabel.text).isEqualTo("Buffer stuffing")
      assertThat(tooltipView.startLabel.text).isEqualTo("Start: 00:00.000")
      assertThat(tooltipView.expectedLabel.text).isEqualTo("Expected end: 00:00.002")
      assertThat(tooltipView.actualLabel.text).isEqualTo("Actual end: 00:00.002")
    }
  }
}

private val FAKE_EVENT_0 =
  AndroidFrameTimelineEvent(42L, 42L,
                            1000L, 2000L, 3000L, "",
                            PerfettoTrace.FrameTimelineEvent.PresentType.PRESENT_LATE,
                            PerfettoTrace.FrameTimelineEvent.JankType.JANK_APP_DEADLINE_MISSED,
                            onTimeFinish = false, gpuComposition = false, 1)

private val FAKE_EVENT_1 =
  AndroidFrameTimelineEvent(43L, 43L,
                            1500L, 3000L, 3500L, "",
                            PerfettoTrace.FrameTimelineEvent.PresentType.PRESENT_LATE,
                            PerfettoTrace.FrameTimelineEvent.JankType.JANK_BUFFER_STUFFING,
                            onTimeFinish = false, gpuComposition = false, 0)


private val FAKE_SELECTION_MODEL = Mockito.mock(MultiSelectionModel::class.java)
private val CAPTURE_RANGE = Range(1000.0, 10000.0)
private val FAKE_CAPTURE = Mockito.mock(SystemTraceCpuCapture::class.java).apply {
  whenever(range).thenReturn(CAPTURE_RANGE)
}
