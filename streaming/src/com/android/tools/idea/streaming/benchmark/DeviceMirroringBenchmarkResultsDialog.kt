/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.streaming.benchmark

import com.intellij.CommonBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.charts.ValueIterable
import com.intellij.ui.charts.dataset
import com.intellij.ui.charts.datasets
import com.intellij.ui.charts.grid
import com.intellij.ui.charts.lineChart
import com.intellij.ui.charts.margins
import com.intellij.ui.charts.ranges
import com.intellij.ui.charts.values
import com.intellij.ui.charts.xPainter
import com.intellij.ui.charts.yPainter
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.panel
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.SwingConstants
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Dialog to display results for device mirroring benchmarking. */
class DeviceMirroringBenchmarkResultsDialog(private val deviceName: String, private val results: Benchmarker.Results<Point>) {
  init {
    require(results.percentiles.values.isNotEmpty()) { "Must provide some values!" }
  }

  private fun createTimeSeries() = latencyChart(
    xValues = 0 until results.raw.size,
    yValues = results.raw.values.map { it.inWholeMilliseconds },
    yLineValues = genLines(results.raw.values.maxOrNull()?.inWholeMilliseconds ?: 0L),
    xLabelFormatStr = "%d")

  private fun createHistogram() = latencyChart(
    xValues = results.percentiles.keys,
    yValues = results.percentiles.values,
    yLineValues = genLines(results.percentiles.values.maxOrNull() ?: 0.0),
    xLabelFormatStr = "%d%%")

  private fun createPanel() = panel {
    separator()
    row {
      resizableRow()
      cell(createHistogram().component)
    }
    separator()
    row {
      resizableRow()
      cell(createTimeSeries().component)
    }
  }

  /**
   * Creates the dialog wrapper.
   */
  fun createWrapper(project: Project? = null, parent: Component? = null): DialogWrapper {
    val dialogPanel = createPanel()
    return dialog(
      title = "$deviceName Mirroring Benchmark Results",
      resizable = true,
      panel = dialogPanel,
      project = project,
      parent = parent,
      createActions = { listOf(CloseDialogAction()) })
  }

  private inner class CloseDialogAction : AbstractAction(CommonBundle.getCloseButtonText()) {
    override fun actionPerformed(event: ActionEvent) {
      val wrapper = DialogWrapper.findInstance(event.source as? Component)
      wrapper?.close(DialogWrapper.CLOSE_EXIT_CODE)
    }
  }

  companion object {
    private inline fun <reified T : Number> latencyChart(
      xValues: Iterable<Int>,
      yValues: Iterable<T>,
      yLineValues: Iterable<T>,
      xLabelFormatStr: String,
    ) = lineChart<Int, T> {
      margins {
        top = 50
        right = 50
        left = 100
        bottom = 50
      }
      datasets {
        dataset {
          label = "Latency"
          lineColor = JBColor.BLUE
          smooth = true
          values {
            x = xValues
            y = yValues
          }
        }
      }
      ranges {
        xMin = xValues.first()
        xMax = xValues.last()
        yMin = yLineValues.first()
        yMax = yLineValues.last()
      }
      grid {
        xLines = genLines(xValues.last()).toValueIterable()
        xPainter {
          label = xLabelFormatStr.format(value)
          verticalAlignment = SwingConstants.BOTTOM
          horizontalAlignment = SwingConstants.CENTER
        }
        yLines = yLineValues.toValueIterable()
        yPainter {
          label = "${value.toDouble().roundToLong()} ms"
          verticalAlignment = SwingConstants.CENTER
          horizontalAlignment = SwingConstants.LEFT
        }
      }
    }.apply {
      component.preferredSize = Dimension(800, 400)
    }

    private fun <T : Number> Iterable<T>.toValueIterable() = object : ValueIterable<T>() {
      override fun iterator(): Iterator<T> = this@toValueIterable.iterator()
    }

    private fun genLines(yMax: Double, divisions: Int = 10): List<Double> {
      // Find a convenient max nearby
      val maxHundreds = ceil(yMax / (100 * divisions)).roundToLong() * 100 * divisions
      val maxTens = ceil(yMax / (10 * divisions)).roundToLong() * 10 * divisions
      val newMax = if (maxHundreds <= 1.2 * yMax) maxHundreds else maxTens

      val step = newMax / divisions
      return (0..divisions).map { (it * step).toDouble() }
    }
    private fun genLines(yMax: Long, divisions: Int = 10): Iterable<Long> = genLines(yMax.toDouble(), divisions).map { it.roundToLong() }
    private fun genLines(yMax: Int, divisions: Int = 10): Iterable<Int> = genLines(yMax.toDouble(), divisions).map { it.roundToInt() }
  }
}