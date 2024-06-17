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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.ui.HideablePanel
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.CpuThreadInfo
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineEvent
import com.android.tools.profilers.cpu.systemtrace.RenderSequence
import com.android.tools.profilers.cpu.systemtrace.SystemTraceCpuCapture
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import perfetto.protos.PerfettoTrace
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTable

class JankSummaryDetailsViewTest {
  @Test
  fun `jank summary view gets basic content right`() {
    fun isCollapsibleTable(title: String): (Component) -> Boolean = { panel ->
      panel is HideablePanel &&
      panel.any { it is JLabel && title in it.text } &&
      panel.any { it is JTable }
    }
    val v = JankSummaryDetailsView(PROFILERS_VIEW, MODEL)
    listOf("Jank type",
           "Layer name",
           "Display timing",
           "Expected duration",
           "Actual duration").forEach { title ->
      assertThat(v.any { it is JLabel && title in it.text }).isTrue()
    }
    assertThat(v.any(isCollapsibleTable("Events associated with frame"))).isTrue()
    assertThat(v.any(isCollapsibleTable("states"))).isTrue()
  }
}

private fun Component.any(satisfies: (Component) -> Boolean) = TreeWalker(this).descendantStream().anyMatch(satisfies)
private val FAKE_MAIN_THREAD = CpuThreadInfo(0, "Main", true)
private val FAKE_GPU_THREAD = CpuThreadInfo(1, "GPU completion", false)
private val FAKE_RENDER_THREAD = CpuThreadInfo(2, CpuThreadInfo.RENDER_THREAD_NAME, false)
private val CAPTURE_RANGE = Range(0.0, 5000.0)
private val PROFILERS = Mockito.mock(StudioProfilers::class.java)
private val PROFILERS_VIEW = Mockito.mock(StudioProfilersView::class.java).apply {
  whenever(studioProfilers).thenReturn(PROFILERS)
}
private val EVENT_NODE = Mockito.mock(CaptureNode::class.java)
private val CAPTURE = Mockito.mock(SystemTraceCpuCapture::class.java).apply {
  whenever(range).thenReturn(CAPTURE_RANGE)
  whenever(systemTraceData).thenReturn(this)
  whenever(getThreads()).thenReturn(setOf(FAKE_MAIN_THREAD, FAKE_GPU_THREAD, FAKE_RENDER_THREAD))
  whenever(getThreadStatesForThread(anyInt())).thenReturn(listOf())
  whenever(frameRenderSequence).thenReturn { RenderSequence(EVENT_NODE, EVENT_NODE, EVENT_NODE) }
}
private val MODEL = JankAnalysisModel.Summary(
  AndroidFrameTimelineEvent(42, 42,
                            1000L, 2000L, 3000L, "",
                            PerfettoTrace.FrameTimelineEvent.PresentType.PRESENT_LATE,
                            PerfettoTrace.FrameTimelineEvent.JankType.JANK_APP_DEADLINE_MISSED,
                            false, false, 0),
  CAPTURE)