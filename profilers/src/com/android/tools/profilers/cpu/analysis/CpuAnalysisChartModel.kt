/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.model.AspectModel
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.axis.ClampedAxisComponentModel
import com.android.tools.adtui.model.filter.Filter
import com.android.tools.adtui.model.filter.FilterResult
import com.android.tools.adtui.model.formatter.PercentAxisFormatter
import com.android.tools.perflib.vmtrace.ClockType
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.capturedetails.CaptureDetails
import com.google.common.annotations.VisibleForTesting

/**
 * This class is the base model for any [CpuAnalysisTabModel]'s that are backed by a [CaptureDetails].
 * The title is used as both the tab title, and the tab tool tip.
 */
class CpuAnalysisChartModel<T>(tabType: Type,
                               private val selectionRange: Range,
                               private val capture: CpuCapture,
                               private val extractNodes: (T) -> Collection<CaptureNode>,
                               private val runModelUpdate: (Runnable) -> Unit) : CpuAnalysisTabModel<T>(tabType) {
  private val observer = AspectObserver()
  val aspectModel = AspectModel<Aspect>()
  val detailsType = when (tabType) {
    Type.FLAME_CHART -> CaptureDetails.Type.FLAME_CHART
    Type.TOP_DOWN -> CaptureDetails.Type.TOP_DOWN
    Type.BOTTOM_UP -> CaptureDetails.Type.BOTTOM_UP
    else -> throw IllegalArgumentException()
  }

  @get:VisibleForTesting
  val captureConvertedRange = Range()
  private val clampedSelectionRange = Range(selectionRange)
  val axisComponentModel = ClampedAxisComponentModel.Builder(clampedSelectionRange, PercentAxisFormatter(5, 10)).build()

  val isCaptureDualClock: Boolean get() = capture.isDualClock
  val dualClockDisabledMessage: String? get() = capture.dualClockDisabledMessage
  val clockTypes: List<ClockType> get() = listOf(ClockType.GLOBAL, ClockType.THREAD)

  var clockType: ClockType = ClockType.GLOBAL
    set(clockType) {
      if (field != clockType && (isCaptureDualClock || clockType != ClockType.THREAD)) {
        field = clockType
        onClockTypeUpdated(clockType)
      }
    }

  init {
    // Need to clone the selection range since the ClampedAxisComponent modifies the range.
    // Without this it will cause weird selection behavior in the SelectionComponent / Minimap.
    selectionRange.addDependency(observer).onChange(Range.Aspect.RANGE, ::selectionRangeSync)
    clampedSelectionRange.addDependency(observer).onChange(Range.Aspect.RANGE, ::updateCaptureConvertedRange)
    captureConvertedRange.addDependency(observer).onChange(Range.Aspect.RANGE, ::updateSelectionRange)
    onClockTypeUpdated(ClockType.GLOBAL)
  }

  private fun selectionRangeSync() = clampedSelectionRange.set(selectionRange)
  fun createDetails() = applyFilterAndCreateDetails(Filter.EMPTY_FILTER).captureDetails

  private fun onClockTypeUpdated(clockType: ClockType) {
    capture.updateClockType(clockType)
    updateCaptureConvertedRange()
    aspectModel.changed(Aspect.CLOCK_TYPE)
  }

  /**
   * Create capture details from the chart model with a filter applied.
   *
   * @return capture details and filter result.
   */
  fun applyFilterAndCreateDetails(filter: Filter): CaptureDetailsWithFilterResult = collectCaptureNodes().let { nodes ->
    val combinedResult = nodes.fold(FilterResult.EMPTY_RESULT) { res, node -> res.combine(node.applyFilter(filter)) }
    CaptureDetailsWithFilterResult(detailsType.build(clockType, captureConvertedRange, nodes, capture, runModelUpdate),
                                   combinedResult)
  }

  /**
   * When using ClockType.THREAD, we need to scale the selection to actually select a relevant range in the capture.
   * That happens because selection is based on wall-clock time, which is usually way greater than thread time.
   * As the two types of clock are synced at start time, making a selection starting at a time
   * greater than (start + thread time length) will result in no feedback for the user, which is wrong.
   * Therefore, we scale the selection so we can provide relevant thread time data as the user changes selection.
   */
  private fun updateCaptureConvertedRange() = captureConvertedRange.update(
    clampedSelectionRange, { it.threadGlobalRatio() }, { it.startThread }, { it.startGlobal })

  /**
   * Updates the selection range based on the converted range in case THREAD clock is being used.
   */
  private fun updateSelectionRange() = clampedSelectionRange.update(
    captureConvertedRange, { 1 / it.threadGlobalRatio() }, { it.startGlobal }, { it.startThread })

  private inline fun Range.update(that: Range,
                                  ratio: (CaptureNode) -> Double,
                                  myStart: (CaptureNode) -> Long, theirStart: (CaptureNode) -> Long) =
    // TODO: improve performance of range conversion.
    setUnlessSameAs(when {
                      clockType == ClockType.GLOBAL || dataSeries.isEmpty() -> that
                      else -> findScalingNode().let { node ->
                        val r = ratio(node)
                        val convertedMin = myStart(node) + r * (that.min - theirStart(node))
                        val convertedMax = convertedMin + r * that.min
                        Range(convertedMin, convertedMax)
                      }
                    })

  /**
   * Each thread has its own ClockType.GLOBAL to ClockType.THREAD mapping, so it's flawed to one fixed ratio when
   * there are multiple threads involved. As a best effort, we choose the one with the maximum threadGlobalRatio
   * to cover every thread.
   *
   * For example, if the GLOBAL range is 0 to 10 seconds. Thread 1 runs for 2 seconds, implying a threadGlobalRatio
   * of 0.2; Threads 2 runs for 5 seconds and its threadGlobalRatio is 0.5. updateCaptureConvertedRange() is
   * being called for the entire GLOBAL range. If choosing Thread 1's ratio 0.2, the converted range would be
   * 2 seconds in the context of thread time. It's shorter than Thread 2's 5 seconds, so some of Thread 2's data would
   * NOT be considered selected even though the entire GLOBAL range is selected. If choosing Thread 2's ratio 0.5,
   * the converted range would be 5 seconds of thread time and can fully cover data of the two threads.
   *
   * TODO: make the range translation respect each thread's nature.
   *
   * @return the Capture node that represents the thread should be used for scaling.
   */
  private fun findScalingNode(): CaptureNode = extractNodes(dataSeries[0]).maxByOrNull(CaptureNode::threadGlobalRatio)!!
  private fun collectCaptureNodes(): List<CaptureNode> = dataSeries.flatMap(extractNodes)

  /**
   * Helper class to hold both a [CaptureDetails] and [FilterResult].
   */
  class CaptureDetailsWithFilterResult(val captureDetails: CaptureDetails, val filterResult: FilterResult)

  enum class Aspect {
    CLOCK_TYPE
  }
}

private fun Range.setUnlessSameAs(that: Range) {
  if (!isSameAs(that)) set(that)
}