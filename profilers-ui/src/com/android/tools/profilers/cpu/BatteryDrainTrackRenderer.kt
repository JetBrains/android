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

import com.android.tools.adtui.AxisComponent
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.chart.linechart.LineChart
import com.android.tools.adtui.chart.linechart.LineConfig
import com.android.tools.adtui.model.trackgroup.TrackModel
import com.android.tools.adtui.trackgroup.TrackRenderer
import com.android.tools.profilers.DataVisualizationColors
import com.android.tools.profilers.cpu.systemtrace.BatteryDrainTrackModel
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Track renderer for System Trace battery drain counters.
 */
class BatteryDrainTrackRenderer : TrackRenderer<BatteryDrainTrackModel> {
  override fun render(trackModel: TrackModel<BatteryDrainTrackModel, *>): JComponent {
    val lineChart = LineChart(trackModel.dataModel).apply {
      configure(trackModel.dataModel.batteryDrainCounterSeries,
                LineConfig(DataVisualizationColors.paletteManager.getBackgroundColor(trackModel.title.hashCode())).setFilled(true))
      setFillEndGap(true)
    }
    val leftAxis = AxisComponent(trackModel.dataModel.axisComponentModel, AxisComponent.AxisOrientation.RIGHT).apply {
      setShowAxisLine(false)
      setHideTickAtMin(true)
    }
    return JPanel(TabularLayout("*", "*")).apply {
      add(leftAxis, TabularLayout.Constraint(0, 0))
      add(lineChart, TabularLayout.Constraint(0, 0))
    }
  }
}