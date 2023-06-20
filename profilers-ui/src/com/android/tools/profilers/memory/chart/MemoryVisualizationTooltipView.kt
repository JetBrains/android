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
package com.android.tools.profilers.memory.chart

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.TooltipView
import com.android.tools.adtui.chart.hchart.HTreeChart
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.profilers.ChartTooltipViewBase
import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.memory.adapters.classifiers.NativeCallStackSet
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * When the user mouses over an element in the {@link MemoryVisualizationView} this class represents the tooltip to be displayed.
 */
class MemoryVisualizationTooltipView(chart: HTreeChart<ClassifierSetHNode>,
                                     tooltipRoot: JComponent,
                                     private val model: VisualizationTooltipModel) : ChartTooltipViewBase<ClassifierSetHNode>(
  chart, tooltipRoot) {

  public override fun showTooltip(node: ClassifierSetHNode) {
    tooltipContainer.removeAll()
    val nameLabel = JLabel(node.name)
    nameLabel.font = TooltipView.TOOLTIP_BODY_FONT
    nameLabel.foreground = ProfilerColors.TOOLTIP_TEXT
    val formattedNumber = model.visualizationModel.formatter().getFormattedString(model.captureRange.length,
                                                                                  node.duration.toDouble(),
                                                                                  model.visualizationModel.isSizeAxis())
    val totalLabel = JLabel(String.format("%s: %s", if (model.visualizationModel.isSizeAxis()) "Size" else "Count", formattedNumber))
    tooltipContainer.add(nameLabel, TabularLayout.Constraint(0, 0))
    if (node.data is NativeCallStackSet) {
      val native = node.data as NativeCallStackSet
      tooltipContainer.add(JLabel(String.format("Module: %s", native.moduleName)),
                           TabularLayout.Constraint(tooltipContainer.componentCount, 0))
      if (native.fileName.isNotEmpty()) {
        tooltipContainer.add(JLabel(String.format("File: %s", native.fileName)),
                             TabularLayout.Constraint(tooltipContainer.componentCount, 0))
      }
    }
    tooltipContainer.add(AdtUiUtils.createHorizontalSeparator(), TabularLayout.Constraint(tooltipContainer.componentCount, 0))
    tooltipContainer.add(totalLabel, TabularLayout.Constraint(tooltipContainer.componentCount, 0))
  }
}
