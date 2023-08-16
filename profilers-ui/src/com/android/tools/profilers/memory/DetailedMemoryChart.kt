/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers.memory

import com.android.tools.adtui.AxisComponent
import com.android.tools.adtui.LegendComponent
import com.android.tools.adtui.LegendConfig
import com.android.tools.adtui.RangeSelectionComponent
import com.android.tools.adtui.RangeTooltipComponent
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.chart.linechart.AbstractDurationDataRenderer
import com.android.tools.adtui.chart.linechart.LineChart
import com.android.tools.adtui.chart.linechart.LineConfig
import com.android.tools.adtui.chart.linechart.OverlayComponent
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.model.RangeSelectionModel
import com.android.tools.adtui.model.RangedContinuousSeries
import com.android.tools.adtui.model.Timeline
import com.android.tools.adtui.model.axis.AxisComponentModel
import com.android.tools.adtui.model.axis.ClampedAxisComponentModel
import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.ProfilerLayout
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import java.awt.Color
import java.awt.LayoutManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class DetailedMemoryChart {
  val lineChart: LineChart
  val tooltip: RangeTooltipComponent
  val overlay: OverlayComponent
  val overlayPanel: JBPanel<Nothing>
  val rangeSelectionComponent: RangeSelectionComponent
  val legends: MemoryStageLegends
  val timeline: Timeline
  private val memoryUsage: DetailedMemoryUsage
  private val memoryAxis: ClampedAxisComponentModel
  private val objectsAxis: ClampedAxisComponentModel
  private val rangeSelectionModel: RangeSelectionModel

  constructor(memoryUsage: DetailedMemoryUsage,
              legends: MemoryStageLegends,
              timeline: Timeline,
              memoryAxis: ClampedAxisComponentModel,
              objectsAxis: ClampedAxisComponentModel,
              rangeSelectionModel: RangeSelectionModel,
              tooltipPanel: JPanel,
              viewComponent: JComponent,
              isLiveAllocationTrackingReady: Boolean,
              shouldShowTooltip: () -> Boolean) {
    this.memoryUsage = memoryUsage
    this.legends = legends
    this.timeline = timeline
    this.memoryAxis = memoryAxis
    this.objectsAxis = objectsAxis
    this.rangeSelectionModel = rangeSelectionModel

    this.rangeSelectionComponent = makeRangeSelectionComponent()
    this.lineChart = makeLineChart(memoryUsage, isLiveAllocationTrackingReady);
    this.overlay = OverlayComponent(rangeSelectionComponent)
    this.overlayPanel = transparentPanel().apply {
      border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
      add(overlay, BorderLayout.CENTER)
    }

    this.tooltip = RangeTooltipComponent(this.timeline,
                                         tooltipPanel,
                                         viewComponent) {
      rangeSelectionComponent.shouldShowSeekComponent() && shouldShowTooltip()
    }.apply {
      // TODO: Probably this needs to be refactored.
      //       We register in both of them because mouse events received by overly will not be received by overlyPanel.
      registerListenersOn(overlay)
      registerListenersOn(overlayPanel)
    }
  }

  fun makeMonitorPanel(overlayPanel: JBPanel<*>) =
    transparentPanel(TabularLayout("*", "*")).apply {
      val axisPanel = transparentPanel().apply {
        add(makeAxis(memoryAxis, AxisComponent.AxisOrientation.RIGHT), BorderLayout.WEST)
        add(makeAxis(objectsAxis, AxisComponent.AxisOrientation.LEFT), BorderLayout.EAST)
      }
      val legendPanel = FlexibleLegendPanel(memoryUsage, legends, lineChart).also {
        addComponentListener(object : ComponentAdapter() {
          override fun componentResized(e: ComponentEvent?) = it.adapt(width)
        })
      }
      border = ProfilerLayout.MONITOR_BORDER
      add(legendPanel, TabularLayout.Constraint(0, 0))
      add(overlayPanel, TabularLayout.Constraint(0, 0))
      add(rangeSelectionComponent, TabularLayout.Constraint(0, 0))
      add(axisPanel, TabularLayout.Constraint(0, 0))
      add(transparentPanel().apply { add(lineChart, BorderLayout.CENTER) }, TabularLayout.Constraint(0, 0))
    }

  fun makeRangeSelectionComponent() = RangeSelectionComponent(rangeSelectionModel).apply {
    setCursorSetter(AdtUiUtils::setTooltipCursor)
  }

  fun makeLineChart(memoryUsage: DetailedMemoryUsage, isLiveAllocationTrackingReady: Boolean): LineChart {
    return memoryUsage.let { memUsage ->
      LineChart(memUsage).apply {
        // Always show series in their captured state in live allocation mode.
        if (isLiveAllocationTrackingReady) {
          configureStackedFilledLine(ProfilerColors.MEMORY_JAVA_CAPTURED, memUsage.javaSeries)
          configureStackedFilledLine(ProfilerColors.MEMORY_NATIVE_CAPTURED, memUsage.nativeSeries)
          configureStackedFilledLine(ProfilerColors.MEMORY_GRAPHICS_CAPTURED, memUsage.graphicsSeries)
          configureStackedFilledLine(ProfilerColors.MEMORY_STACK_CAPTURED, memUsage.stackSeries)
          configureStackedFilledLine(ProfilerColors.MEMORY_CODE_CAPTURED, memUsage.codeSeries)
          configureStackedFilledLine(ProfilerColors.MEMORY_OTHERS_CAPTURED, memUsage.otherSeries)
          configure(memUsage.objectsSeries, LineConfig(ProfilerColors.MEMORY_OBJECTS_CAPTURED)
            .setStroke(LineConfig.DEFAULT_DASH_STROKE).setLegendIconType(LegendConfig.IconType.DASHED_LINE))
        }
        else {
          configureStackedFilledLine(ProfilerColors.MEMORY_JAVA, memUsage.javaSeries)
          configureStackedFilledLine(ProfilerColors.MEMORY_NATIVE, memUsage.nativeSeries)
          configureStackedFilledLine(ProfilerColors.MEMORY_GRAPHICS, memUsage.graphicsSeries)
          configureStackedFilledLine(ProfilerColors.MEMORY_STACK, memUsage.stackSeries)
          configureStackedFilledLine(ProfilerColors.MEMORY_CODE, memUsage.codeSeries)
          configureStackedFilledLine(ProfilerColors.MEMORY_OTHERS, memUsage.otherSeries)
          configure(memUsage.objectsSeries, LineConfig(ProfilerColors.MEMORY_OBJECTS)
            .setStroke(LineConfig.DEFAULT_DASH_STROKE).setLegendIconType(LegendConfig.IconType.DASHED_LINE))
        }

        // The "Total" series is only added in the LineChartModel so it can calculate the max Y value across all the series. We don't want to
        // draw it as an extra line so we hide it by setting it to transparent.
        configure(memUsage.totalMemorySeries, LineConfig(JBColor.BLACK))
        setRenderOffset(0, LineConfig.DEFAULT_DASH_STROKE.lineWidth.toInt() / 2)
        setTopPadding(ProfilerLayout.Y_AXIS_TOP_MARGIN)
        setFillEndGap(true)
      }
    }
  }

  fun registerRenderer(renderer: AbstractDurationDataRenderer) {
    lineChart.addCustomRenderer(renderer)
    overlay.addDurationDataRenderer(renderer)
  }
}

internal class FlexibleLegendPanel(usage: DetailedMemoryUsage,
                                   legends: MemoryStageLegends,
                                   lineChart: LineChart) : JBPanel<FlexibleLegendPanel>(
  BorderLayout()) {
  private val fullLegend = makeLegendComponent(usage, legends, lineChart, true)
  private val compactLegend = makeLegendComponent(usage, legends, lineChart, false)

  init {
    val label = JLabel("MEMORY").apply {
      border = ProfilerLayout.MONITOR_LABEL_PADDING
      verticalAlignment = SwingConstants.TOP
    }
    isOpaque = false
    add(label, BorderLayout.WEST)
    add(fullLegend, BorderLayout.EAST)
  }

  fun adapt(width: Int) {
    remove(fullLegend)
    remove(compactLegend)
    add(if (fullLegend.preferredSize.width + 60 < width) fullLegend else compactLegend, BorderLayout.EAST)
  }

  private companion object {
    private fun makeLegendComponent(memoryUsage: DetailedMemoryUsage,
                                    legends: MemoryStageLegends,
                                    lineChart: LineChart, full: Boolean): LegendComponent {
      return LegendComponent.Builder(legends)
        .setRightPadding(ProfilerLayout.PROFILER_LEGEND_RIGHT_PADDING)
        .setShowValues(full)
        .setExcludedLegends(*(if (full) emptyArray() else arrayOf("Total")))
        .build().apply {
          configure(legends.javaLegend, LegendConfig(lineChart.getLineConfig(memoryUsage.javaSeries)))
          configure(legends.nativeLegend, LegendConfig(lineChart.getLineConfig(memoryUsage.nativeSeries)))
          configure(legends.graphicsLegend, LegendConfig(lineChart.getLineConfig(memoryUsage.graphicsSeries)))
          configure(legends.stackLegend, LegendConfig(lineChart.getLineConfig(memoryUsage.stackSeries)))
          configure(legends.codeLegend, LegendConfig(lineChart.getLineConfig(memoryUsage.codeSeries)))
          configure(legends.otherLegend, LegendConfig(lineChart.getLineConfig(memoryUsage.otherSeries)))
          configure(legends.totalLegend, LegendConfig(lineChart.getLineConfig(memoryUsage.totalMemorySeries)))
          configure(legends.objectsLegend, LegendConfig(lineChart.getLineConfig(memoryUsage.objectsSeries)))
        }
    }
  }
}

internal fun transparentPanel(layout: LayoutManager = BorderLayout()) = JBPanel<Nothing>(layout).apply {
  isOpaque = false
}

internal fun makeAxis(model: AxisComponentModel, orientation: AxisComponent.AxisOrientation) =
  AxisComponent(model, orientation, true).apply {
    setShowAxisLine(false)
    setShowMax(true)
    setOnlyShowUnitAtMax(false)
    setHideTickAtMin(true)
    setMarkerLengths(ProfilerLayout.MARKER_LENGTH, ProfilerLayout.MARKER_LENGTH)
    setMargins(0, ProfilerLayout.Y_AXIS_TOP_MARGIN)
  }

internal fun LineChart.configureStackedFilledLine(color: Color, series: RangedContinuousSeries) =
  configure(series, LineConfig(color).setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX))