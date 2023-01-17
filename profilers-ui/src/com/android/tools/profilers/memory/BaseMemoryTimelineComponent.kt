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
package com.android.tools.profilers.memory

import com.android.tools.adtui.AxisComponent
import com.android.tools.adtui.LegendComponent
import com.android.tools.adtui.LegendConfig
import com.android.tools.adtui.RangeSelectionComponent
import com.android.tools.adtui.RangeTooltipComponent
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.chart.linechart.AbstractDurationDataRenderer
import com.android.tools.adtui.chart.linechart.DurationDataRenderer
import com.android.tools.adtui.chart.linechart.LineChart
import com.android.tools.adtui.chart.linechart.LineConfig
import com.android.tools.adtui.chart.linechart.OverlayComponent
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.model.DurationDataModel
import com.android.tools.adtui.model.RangedContinuousSeries
import com.android.tools.adtui.model.axis.AxisComponentModel
import com.android.tools.adtui.model.formatter.TimeAxisFormatter
import com.android.tools.adtui.stdui.StreamingScrollbar
import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.ProfilerLayout.MARKER_LENGTH
import com.android.tools.profilers.ProfilerLayout.MONITOR_BORDER
import com.android.tools.profilers.ProfilerLayout.MONITOR_LABEL_PADDING
import com.android.tools.profilers.ProfilerLayout.PROFILER_LEGEND_RIGHT_PADDING
import com.android.tools.profilers.ProfilerLayout.Y_AXIS_TOP_MARGIN
import com.android.tools.profilers.StageView
import com.android.tools.profilers.SupportLevel
import com.android.tools.profilers.event.EventMonitorView
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.Companion.getModeFromFrequency
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.FULL
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.NONE
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.LayoutManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants

abstract class BaseMemoryTimelineComponent<T: BaseStreamingMemoryProfilerStage>(stageView: StageView<T>, timeAxis: JComponent)
      : JBPanel<BaseMemoryTimelineComponent<T>>(TabularLayout("*")) {
  protected val stage = stageView.stage
  val rangeSelectionComponent = makeRangeSelectionComponent()
  protected val lineChart = makeLineChart()
  private val overlay = OverlayComponent(rangeSelectionComponent)

  init {
    background = ProfilerColors.DEFAULT_STAGE_BACKGROUND

    val overlayPanel = transparentPanel().apply {
      border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
      add(overlay, BorderLayout.CENTER)
    }

    val tooltip = RangeTooltipComponent(stage.timeline, stageView.tooltipPanel, stageView.profilersView.component) {
      rangeSelectionComponent.shouldShowSeekComponent() && shouldShowTooltip()
    }.apply {
      // TODO: Probably this needs to be refactored.
      //       We register in both of them because mouse events received by overly will not be received by overlyPanel.
      registerListenersOn(overlay)
      registerListenersOn(overlayPanel)
    }

    (layout as TabularLayout).setRowSizing(1, "*") // Give monitor as much space as possible

    // Order matters, as such we want to put the tooltip component first so we draw the tooltip line on top of all other
    // components.
    add(tooltip, TabularLayout.Constraint(0, 0, 2, 1))
    if (stage.studioProfilers.selectedSessionSupportLevel == SupportLevel.DEBUGGABLE) {
      val eventsView = EventMonitorView(stageView.profilersView, stage.eventMonitor).apply { registerTooltip(tooltip, stage) }
      add(eventsView.component, TabularLayout.Constraint(0, 0))
    }
    // The scrollbar can modify the view range - so it should be registered to the Choreographer before all other Animatables
    // that attempts to read the same range instance.
    makeScrollbar()?.let { add(it, TabularLayout.Constraint(3, 0)) }
    add(timeAxis, TabularLayout.Constraint(2, 0))
    add(makeMonitorPanel(overlayPanel), TabularLayout.Constraint(1, 0))
  }

  protected open fun shouldShowTooltip() = true

  protected open fun makeScrollbar(): JComponent? =
    StreamingScrollbar(stage.timeline, this)

  protected fun registerRenderer(renderer: AbstractDurationDataRenderer) {
    lineChart.addCustomRenderer(renderer)
    overlay.addDurationDataRenderer(renderer)
  }

  open protected fun makeMonitorPanel(overlayPanel: JBPanel<*>) = transparentPanel(TabularLayout("*", "*")).apply {
    val axisPanel = transparentPanel().apply {
      add(makeAxis(stage.memoryAxis, AxisComponent.AxisOrientation.RIGHT), BorderLayout.WEST)
      add(makeAxis(stage.objectsAxis, AxisComponent.AxisOrientation.LEFT), BorderLayout.EAST)
    }
    val legendPanel = FlexibleLegendPanel(stage, lineChart).also {
      addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) = it.adapt(width)
      })
    }
    border = MONITOR_BORDER
    add(legendPanel, TabularLayout.Constraint(0, 0))
    add(overlayPanel, TabularLayout.Constraint(0, 0))
    add(rangeSelectionComponent, TabularLayout.Constraint(0, 0))
    add(axisPanel, TabularLayout.Constraint(0, 0))
    add(transparentPanel().apply { add(lineChart, BorderLayout.CENTER) }, TabularLayout.Constraint(0, 0))
  }

  protected fun makeGcDurationDataRenderer() =
    DurationDataRenderer.Builder(stage.detailedMemoryUsage.gcDurations, JBColor.BLACK)
      .setIcon(StudioIcons.Profiler.Events.GARBAGE_EVENT)
      // Need to offset the GcDurationData by the margin difference between the overlay component and the
      // line chart. This ensures we are able to render the Gc events in the proper locations on the line.
      .setLabelOffsets(-StudioIcons.Profiler.Events.GARBAGE_EVENT.iconWidth / 2f,
                       StudioIcons.Profiler.Events.GARBAGE_EVENT.iconHeight / 2f)
      .setHostInsets(JBUI.insets(Y_AXIS_TOP_MARGIN, 0, 0, 0))
      .setHoverHandler { stage.tooltipLegends.gcDurationLegend.setPickData(it) }
      .setClickRegionPadding(0, 0)
      .build()

  protected fun makeAllocationSamplingRateRenderer() =
    DurationDataRenderer.Builder(stage.allocationSamplingRateDurations, JBColor.BLACK)
      .setDurationBg(ProfilerColors.DEFAULT_STAGE_BACKGROUND)
      .setIconMapper { getIconForSamplingMode(getModeFromFrequency(it.currentRate.samplingNumInterval)) }
      .setLabelOffsets(-StudioIcons.Profiler.Events.ALLOCATION_TRACKING_NONE.iconWidth / 2f,
                       StudioIcons.Profiler.Events.ALLOCATION_TRACKING_NONE.iconHeight / 2f)
      .setHostInsets(JBUI.insets(Y_AXIS_TOP_MARGIN, 0, 0, 0))
      .setClickRegionPadding(0, 0)
      .setHoverHandler { stage.tooltipLegends.samplingRateDurationLegend.setPickData(it) }
      .build()

  protected open fun makeLineChart() = stage.detailedMemoryUsage.let { memUsage ->
    LineChart(memUsage).apply {
      // Always show series in their captured state in live allocation mode.
      if (stage.isLiveAllocationTrackingReady) {
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
      setTopPadding(Y_AXIS_TOP_MARGIN)
      setFillEndGap(true)
    }
  }

  protected fun makeAllocationRenderer(model: DurationDataModel<CaptureDurationData<out CaptureObject>>, tag: String) =
    DurationDataRenderer.Builder(model, JBColor.LIGHT_GRAY)
      .setDurationBg(ProfilerColors.MEMORY_ALLOC_BG)
      .setLabelColors(JBColor.DARK_GRAY, JBColor.GRAY, JBColor.LIGHT_GRAY, JBColor.WHITE)
      .setLabelProvider {
        val duration =
          if (it.durationUs == Long.MAX_VALUE) "in progress"
          else TimeAxisFormatter.DEFAULT.getFormattedString(stage.timeline.viewRange.length, it.durationUs.toDouble(), true)
        "$tag record ($duration)"
      }.build()

  private fun makeRangeSelectionComponent() = RangeSelectionComponent(stage.rangeSelectionModel).apply {
    setCursorSetter(AdtUiUtils::setTooltipCursor)
  }

  companion object {
    // TODO(b/116430034): use real icons when they're done.
    @JvmStatic
    fun getIconForSamplingMode(mode: LiveAllocationSamplingMode): Icon = when (mode) {
      FULL -> StudioIcons.Profiler.Events.ALLOCATION_TRACKING_FULL
      SAMPLED -> StudioIcons.Profiler.Events.ALLOCATION_TRACKING_SAMPLED
      NONE -> StudioIcons.Profiler.Events.ALLOCATION_TRACKING_NONE
    }
  }
}

private class FlexibleLegendPanel(stage: BaseStreamingMemoryProfilerStage, lineChart: LineChart): JBPanel<FlexibleLegendPanel>(BorderLayout()) {
  private val fullLegend = makeLegendComponent(stage, lineChart, true)
  private val compactLegend = makeLegendComponent(stage, lineChart, false)
  init {
    val label = JLabel("MEMORY").apply {
      border = MONITOR_LABEL_PADDING
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
    private fun makeLegendComponent(stage: BaseStreamingMemoryProfilerStage, lineChart: LineChart, full: Boolean): LegendComponent {
      val legends = stage.legends
      val memoryUsage = stage.detailedMemoryUsage
      return LegendComponent.Builder(legends)
        .setRightPadding(PROFILER_LEGEND_RIGHT_PADDING)
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

private fun transparentPanel(layout: LayoutManager = BorderLayout()) = JBPanel<Nothing>(layout).apply {
  isOpaque = false
}

private fun LineChart.configureStackedFilledLine(color: Color, series: RangedContinuousSeries) =
  configure(series, LineConfig(color).setFilled(true).setStacked(true).setLegendIconType(LegendConfig.IconType.BOX))

private fun makeAxis(model: AxisComponentModel, orientation: AxisComponent.AxisOrientation) =
  AxisComponent(model, orientation, true).apply {
    setShowAxisLine(false)
    setShowMax(true)
    setOnlyShowUnitAtMax(false)
    setHideTickAtMin(true)
    setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH)
    setMargins(0, Y_AXIS_TOP_MARGIN)
  }