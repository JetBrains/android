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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.TooltipView
import com.android.tools.adtui.model.formatter.NumberFormatter
import com.android.tools.idea.flags.enums.PowerProfilerDisplayMode
import com.android.tools.profilers.cpu.systemtrace.PowerRailTooltip
import com.android.tools.profilers.cpu.systemtrace.PowerRailTrackModel.Companion.POWER_RAIL_UNIT
import com.google.common.annotations.VisibleForTesting
import kotlinx.html.body
import kotlinx.html.classes
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.stream.appendHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.tr
import kotlinx.html.unsafe
import javax.swing.JComponent
import javax.swing.JPanel

class PowerRailTooltipView(parent: JComponent, val tooltip: PowerRailTooltip) : TooltipView(tooltip.timeline) {
  private val content = JPanel(TabularLayout("*").setVGap(12))

  @VisibleForTesting
  val valueLabel = createTooltipLabel()

  override fun createTooltip(): JComponent {
    return content
  }

  private fun updateView() {
    val primaryPowerRailValueText = NumberFormatter.formatInteger(tooltip.activeValueUws.primaryValue)
    val secondaryPowerRailValueText = NumberFormatter.formatInteger(tooltip.activeValueUws.secondaryValue)

    valueLabel.text = getFormattedTooltipValueText(primaryPowerRailValueText, secondaryPowerRailValueText)
  }

  /**
   * Returns HTML string for a table formatting the primary and secondary values right justified,
   * with their respective value title left justified on the same line.
   *
   * Returned table formatting:
   *
   * power.rail.sample - value-title-1 (unit)           value-1
   * power.rail.sample - value-title-2 (unit)           value-2
   */
  @VisibleForTesting
  fun getFormattedTooltipValueText(primaryValueText: String, secondaryValueText: String): String {
    val spaceBetweenTitleAndValuePx = 50

    val cumulativeValueTitle = getFormattedValueTitle(trackName = tooltip.counterName, valueType = "Cumulative", unit = POWER_RAIL_UNIT)
    val deltaValueTitle = getFormattedValueTitle(trackName = tooltip.counterName, valueType = "Delta", unit = POWER_RAIL_UNIT)

    // Note: The else conditions cover the PowerProfilerDisplayMode.HIDE case, but
    // the HIDE mode will not even be displayed, so this selection does not matter.
    val primaryValueTitle = if (tooltip.displayMode == PowerProfilerDisplayMode.CUMULATIVE) cumulativeValueTitle else deltaValueTitle
    val secondaryValueTitle = if (tooltip.displayMode == PowerProfilerDisplayMode.CUMULATIVE) deltaValueTitle else cumulativeValueTitle

    return buildString {
      appendHTML().html {
        head {
          style {
            unsafe {
              +"""
                  table {
                      border-collapse: collapse;
                      width: auto;
                      border-spacing: 0;
                  }
                  td {
                      padding: 0;
                      margin: 0;
                  }
                  tr.primary-line td {
                      padding-bottom: 5px;
                  }
                  .secondary-line {}
                  .key-column {
                      padding-right: ${spaceBetweenTitleAndValuePx}px;
                  }
              """.trimIndent()
            }
          }
        }
        body {
          table {
            attributes["border"] = "1"
            tr {
              classes = setOf("primary-line")
              td {
                classes = setOf("key-column")
                attributes["align"] = "left"
                +primaryValueTitle
              }
              td {
                attributes["align"] = "right"
                +primaryValueText
              }
            }
            tr {
              classes = setOf("secondary-line")
              td {
                classes = setOf("key-column")
                attributes["align"] = "left"
                +secondaryValueTitle
              }
              td {
                attributes["align"] = "right"
                +secondaryValueText
              }
            }
          }
        }
      }
    }
}

  /**
   * Helper method to format the value title (which includes the track name, value type, and the unit).
   */
  private fun getFormattedValueTitle(trackName: String, valueType: String, unit: String): String {
    return "$trackName - $valueType${if (unit.isBlank()) "" else " ($unit)"}"
  }

  init {
    content.add(valueLabel, TabularLayout.Constraint(0, 0))
    tooltip.addDependency(this).onChange(PowerRailTooltip.Aspect.VALUE_CHANGED) { updateView() }
    updateView()
  }
}