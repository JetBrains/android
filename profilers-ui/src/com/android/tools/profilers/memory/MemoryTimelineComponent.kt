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

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.chart.linechart.AbstractDurationDataRenderer
import com.android.tools.adtui.chart.linechart.DurationDataRenderer
import com.android.tools.adtui.chart.linechart.LineConfig
import com.android.tools.adtui.common.DataVisualizationColors.toGrayscale
import com.android.tools.adtui.instructions.IconInstruction
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.instructions.NewRowInstruction
import com.android.tools.adtui.instructions.TextInstruction
import com.android.tools.adtui.model.RangedContinuousSeries
import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.ProfilerFonts
import com.android.tools.profilers.ProfilerLayout.PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER
import com.android.tools.profilers.ProfilerLayout.PROFILING_INSTRUCTIONS_ICON_PADDING
import com.intellij.ui.components.JBPanel
import com.intellij.util.IconUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtilities
import icons.StudioIcons
import java.awt.Color
import java.util.function.Consumer
import javax.swing.JComponent

class MemoryTimelineComponent(stageView: MainMemoryProfilerStageView, timeAxis: JComponent)
      : BaseMemoryTimelineComponent<MainMemoryProfilerStage>(stageView, timeAxis) {
  val gcDurationDataRenderer = makeGcDurationDataRenderer()
  private val heapDumpRenderer = makeHeapDumpRenderer()

  init {
    // Add all renderers. Order matters.
    listOfNotNull<AbstractDurationDataRenderer>(
      gcDurationDataRenderer,
      // Only shows allocation tracking visuals in pre-O, since we are always tracking in O+.
      when {
        !stage.isLiveAllocationTrackingReady -> makeLegacyAllocationRenderer()
        stage.studioProfilers.ideServices.featureConfig.isLiveAllocationsSamplingEnabled -> makePastAllocationRenderer()
        else -> null
      },
      // Order matters so native allocation tracking goes to the top of the stack. This means when a native allocation recording is captured
      // the capture appears on top of the other renderers.
      if (stage.isNativeAllocationSamplingEnabled) makeNativeAllocationRenderer() else null,
      heapDumpRenderer
    ).forEach(::registerRenderer)
  }

  override fun shouldShowTooltip() = !heapDumpRenderer.isMouseOverHeapDump

  override fun makeLineChart() = super.makeLineChart().apply {
    configure(stage.detailedMemoryUsage.objectsSeries, LineConfig(Color(0, 0, 0, 0)))
  }

  private fun makeHeapDumpRenderer() = HeapDumpRenderer(stage.heapDumpSampleDurations, stage.timeline.viewRange).apply {
    addHeapDumpHoverListener(Consumer { hovered ->
      when {
        hovered -> stage.tooltip = null
        stage.tooltip == null -> stage.tooltip = MemoryUsageTooltip(stage)
      }
    })
    rangeSelectionComponent.setRangeOcclusionTest(::isMouseOverHeapDump)
  }

  private fun makeNativeAllocationRenderer() =
    makeAllocationRenderer(stage.nativeAllocationInfosDurations, "Native Allocation").grayOut()

  private fun makeLegacyAllocationRenderer() = makeAllocationRenderer(stage.allocationInfosDurations, "Allocation").apply {
    fun add(series: RangedContinuousSeries, color: Color) =
      addCustomLineConfig(series, LineConfig.copyOf(lineChart.getLineConfig(series)).setColor(color))
    add(stage.detailedMemoryUsage.javaSeries, ProfilerColors.MEMORY_JAVA_CAPTURED)
    add(stage.detailedMemoryUsage.nativeSeries, ProfilerColors.MEMORY_NATIVE_CAPTURED)
    add(stage.detailedMemoryUsage.graphicsSeries, ProfilerColors.MEMORY_GRAPHICS_CAPTURED)
    add(stage.detailedMemoryUsage.stackSeries, ProfilerColors.MEMORY_STACK_CAPTURED)
    add(stage.detailedMemoryUsage.codeSeries, ProfilerColors.MEMORY_CODE_CAPTURED)
    add(stage.detailedMemoryUsage.otherSeries, ProfilerColors.MEMORY_OTHERS_CAPTURED)
  }

  private fun makePastAllocationRenderer() =
    makeAllocationRenderer(stage.allocationInfosDurations, "Allocation").grayOut()

  private fun DurationDataRenderer<*>.grayOut() = apply {
    for (series in stage.detailedMemoryUsage.series.filterNot { it.name == "Allocated"}) {
      val config = lineChart.getLineConfig(series)
      val newConfig = LineConfig.copyOf(config).setColor(toGrayscale(config.color))
      addCustomLineConfig(series!!, newConfig)
    }
  }
}