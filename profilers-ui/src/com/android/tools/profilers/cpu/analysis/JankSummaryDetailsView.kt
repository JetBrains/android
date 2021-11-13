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

import com.android.tools.adtui.PaginatedTableView
import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.model.formatter.TimeFormatter
import com.android.tools.adtui.ui.HideablePanel
import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.LazyDataSeries
import com.android.tools.profilers.cpu.analysis.TableUtils.setColumnRenderers
import com.android.tools.profilers.cpu.getActiveColor
import com.android.tools.profilers.cpu.systemtrace.getTitle
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JComponent
import javax.swing.table.DefaultTableCellRenderer
import kotlin.math.min

class JankSummaryDetailsView(profilersView: StudioProfilersView, model: JankAnalysisModel.Summary)
      : SummaryDetailsViewBase<JankAnalysisModel.Summary>(profilersView, model) {
  init {
    val capture = model.capture
    fun hideablePanel(title: String, content: JComponent) =
      HideablePanel(HideablePanel.Builder(title, content)
                      .setPanelBorder(JBUI.Borders.empty())
                      .setContentBorder(JBUI.Borders.empty(8, 0, 0, 0)).apply {
          background = primaryContentBackground
        })
    val event = model.event

    addRowToCommonSection("Jank type", JBLabel(event.appJankType.getTitle()).apply {
      foreground = event.getActiveColor()
    })
    addRowToCommonSection("Display timing", JBLabel(event.presentType.getTitle()))
    addRowToCommonSection("Deadline",
                          JBLabel(TimeFormatter.getSemiSimplifiedClockString(capture.offset(event.expectedEndUs))))
    addRowToCommonSection("Actual end",
                          JBLabel(TimeFormatter.getSemiSimplifiedClockString(capture.offset(event.actualEndUs))).apply {
                            foreground = event.getActiveColor()
                          })

    val (expectedPercent, actualPercent) = when {
      event.expectedDurationUs < event.actualDurationUs -> (event.expectedDurationUs * 100 / event.actualDurationUs).toInt() to 100
      event.expectedDurationUs > 0 -> 100 to (event.actualDurationUs * 100 / event.expectedDurationUs).toInt()
      else -> 100 to 100
    }
    val maxDurationBarWidth = 100
    addRowToCommonSection("Expected duration",
                          FilledLabel(TimeFormatter.getSingleUnitDurationString(event.expectedDurationUs),
                                      ProfilerColors.CAPTURE_SPARKLINE, expectedPercent, maxDurationBarWidth))
    addRowToCommonSection("Actual duration",
                          FilledLabel(TimeFormatter.getSingleUnitDurationString(event.actualDurationUs),
                                      event.getActiveColor(), actualPercent, maxDurationBarWidth))
    addSection(hideablePanel("Events associated with frame",
                             EventTable.of(capture,
                                           model.getThreadChildren(model.renderThreadId) to "Render",
                                           model.getThreadChildren(model.gpuThreadId) to "GPU",
                                           model.getThreadChildren(model.mainThreadId) to "Main")))

    addSection(CpuThreadStateTable(profilersView.studioProfilers,
                                   listOf(LazyDataSeries{model.getThreadState(model.mainThreadId)},
                                          LazyDataSeries{model.getThreadState(model.renderThreadId)},
                                          LazyDataSeries{model.getThreadState(model.gpuThreadId)}),
                                   model.eventRange)
                 .component)
  }
}

private class FilledLabel(text: String, private val barColor: Color, private val percent: Int, private val maxBarWidth: Int): JBLabel(text) {
  init { require(percent in 0 .. 100) }
  override fun paintComponent(g: Graphics) {
    g.color = barColor
    g.fillRect(0, 0, min(width, maxBarWidth) * percent / 100, height)
    super.paintComponent(g)
  }
}

private object EventTable {
  fun of(capture: CpuCapture, vararg renderEventLists: Pair<List<CaptureNode>, String>): JComponent =
    renderEventLists
      .map { (events, threadName) -> events.asSequence().map { Row(capture.offset(it.start), it.data.name, threadName) } }
      .reduce(Sequence<Row>::plus)
      .toMutableList()
      .asTableModel(Col::get, Col::type, Col::title).let { model ->
        with(PaginatedTableView(model)) {
          with(table) {
            showVerticalLines = true
            showHorizontalLines = true
            setColumnRenderers<Col> { when (it) {
              Col.START -> TimestampRenderer()
              Col.NAME, Col.THREAD -> DefaultTableCellRenderer()
            } }
          }
          component.apply {
            minimumSize = Dimension(preferredSize.width, 300)
          }
        }
      }

  data class Row(val startUs: Long, val name: String, val threadName: String)
  enum class Col(val title: String, val type: Class<*>, val get: (Row) -> Comparable<*>) {
    START("Start time", Long::class.java, Row::startUs),
    NAME("Name", String::class.java, Row::name),
    THREAD("Thread", String::class.java, Row::threadName)
  }
}

private fun CpuCapture.offset(us: Long) = us - range.min.toLong()