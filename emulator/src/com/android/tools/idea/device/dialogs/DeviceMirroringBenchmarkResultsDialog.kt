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
package com.android.tools.idea.device.dialogs

import com.android.tools.idea.device.benchmark.Benchmarker
import com.intellij.CommonBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.charts.dataset
import com.intellij.ui.charts.datasets
import com.intellij.ui.charts.generator
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

/** Dialog to display results for device mirroring benchmarking. */
class DeviceMirroringBenchmarkResultsDialog(private val deviceName: String, private val results: Benchmarker.Results<Point>) {
  init {
    require(results.percentiles.values.isNotEmpty()) { "Must provide some values!" }
  }

  private fun createPanel() = panel {
    row {
      resizableRow()
      val chart = lineChart<Int, Double> {
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
              results.percentiles.toList().unzip().let {
                x = it.first
                y = it.second
              }
            }
          }
        }
        ranges {
          xMin = 0
          xMax = 100
          yMin = 0.0
        }
        grid {
          xLines = generator(10)
          xPainter {
            label = "%d%%".format(value)
            verticalAlignment = SwingConstants.BOTTOM
            horizontalAlignment = SwingConstants.CENTER
          }
          results.percentiles[100]?.let {
            yLines = generator(it / 10)
          }
          yPainter {
            label = "%.0f ms".format(value)
            verticalAlignment = SwingConstants.CENTER
            horizontalAlignment = SwingConstants.LEFT
          }
        }
      }
      chart.component.preferredSize = Dimension(800, 400)
      cell(chart.component)
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
}
