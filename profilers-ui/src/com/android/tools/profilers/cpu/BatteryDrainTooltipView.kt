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
import com.google.common.annotations.VisibleForTesting
import javax.swing.JComponent
import javax.swing.JPanel

class BatteryDrainTooltipView(parent: JComponent, val tooltip: BatteryDrainTooltip) : TooltipView(tooltip.timeline) {
  private val content = JPanel(TabularLayout("*").setVGap(12))

  @VisibleForTesting
  val valueLabel = createTooltipLabel()

  override fun createTooltip(): JComponent {
    return content
  }

  private fun updateView() {
    val batteryDrainValueText = NumberFormatter.formatInteger(tooltip.activeValue)
    valueLabel.text = "${getTitle(tooltip.counterName)}: $batteryDrainValueText"
    valueLabel.text += when (tooltip.unit) {
      "µah", "µa" -> " ${tooltip.unit}"
      else -> tooltip.unit
    }
  }

  private fun getTitle(counterName: String) = counterName

  init {
    content.add(valueLabel, TabularLayout.Constraint(0, 0))
    tooltip.addDependency(this).onChange(BatteryDrainTooltip.Aspect.VALUE_CHANGED) { updateView() }
    updateView()
  }
}