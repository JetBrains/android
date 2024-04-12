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

import com.android.tools.adtui.RangeSelectionComponent
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.chart.linechart.AbstractDurationDataRenderer
import com.android.tools.adtui.chart.linechart.DurationDataRenderer
import com.android.tools.adtui.chart.linechart.LineChart
import com.android.tools.adtui.chart.linechart.OverlayComponent
import com.android.tools.adtui.model.DurationDataModel
import com.android.tools.adtui.model.formatter.TimeAxisFormatter
import com.android.tools.adtui.stdui.TimelineScrollbar
import com.android.tools.profilers.ProfilerColors
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
import org.jetbrains.annotations.VisibleForTesting
import java.util.function.DoubleSupplier
import javax.swing.Icon
import javax.swing.JComponent

abstract class BaseMemoryTimelineComponent<T: BaseStreamingMemoryProfilerStage>(stageView: StageView<T>, timeAxis: JComponent)
  : JBPanel<BaseMemoryTimelineComponent<T>>(TabularLayout("*")) {
  protected val stage = stageView.stage
  protected val lineChart: LineChart
  private val overlay: OverlayComponent
  private val detailedMemoryChart: DetailedMemoryChart
  val rangeSelectionComponent: RangeSelectionComponent
  private val garbageCollectionComponent: GarbageCollectionComponent

  init {
    detailedMemoryChart = DetailedMemoryChart(stage.detailedMemoryUsage,
                                              stage.legends,
                                              stage.timeline,
                                              stage.memoryAxis,
                                              stage.objectsAxis,
                                              stage.rangeSelectionModel,
                                              stageView.tooltipPanel,
                                              stageView.profilersView.component,
                                              stage.isLiveAllocationTrackingReady,
                                              ::shouldShowTooltip,
                                              fillEndSupplier())
    garbageCollectionComponent = GarbageCollectionComponent()
    lineChart = detailedMemoryChart.lineChart
    rangeSelectionComponent = detailedMemoryChart.rangeSelectionComponent
    overlay = detailedMemoryChart.overlay
    background = ProfilerColors.DEFAULT_STAGE_BACKGROUND

    val overlayPanel = detailedMemoryChart.overlayPanel
    val tooltip = detailedMemoryChart.tooltip
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

  /**
   * The supplier to determine how to fill the end area in the line chart.
   */
  @VisibleForTesting
  open fun fillEndSupplier(): DoubleSupplier = LineChart.ALWAYS_1

  protected open fun makeScrollbar(): JComponent? =
    TimelineScrollbar(stage.timeline, this)

  protected fun registerRenderer(renderer: AbstractDurationDataRenderer) = detailedMemoryChart.registerRenderer(renderer)

  protected open fun makeMonitorPanel(overlayPanel: JBPanel<*>) = detailedMemoryChart.makeMonitorPanel(overlayPanel)

  protected fun makeGcDurationDataRenderer() = garbageCollectionComponent.makeGcDurationDataRenderer(stage.detailedMemoryUsage,
                                                                                                     stage.tooltipLegends)

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

  private fun makeRangeSelectionComponent() = detailedMemoryChart.makeRangeSelectionComponent()

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