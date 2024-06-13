/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.stdui

import com.android.tools.adtui.RangeScrollBarUI
import com.android.tools.adtui.common.AdtUiUtils.isActionKeyDown
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.Range.Aspect.RANGE
import com.android.tools.adtui.model.StreamingTimeline
import com.android.tools.adtui.model.Timeline
import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.components.JBScrollBar
import java.awt.Graphics
import java.awt.Rectangle
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import kotlin.math.max
import kotlin.math.min

/**
 * Work in ms to keep things compatible with scrollbar's integer api. This should cover a long
 * enough time period for us in terms of profiling.
 */
private val MS_TO_US = TimeUnit.MILLISECONDS.toMicros(1)

/** Pixel threshold to switch [.myTimeline] to streaming mode. */
private const val STREAMING_POSITION_THRESHOLD_PX = 10f

/**
 * A custom toolbar that synchronizes with the data+view ranges from the [Timeline].
 *
 * If timeline is a StreamingTimeline, this control sets it into streaming mode if users drags the
 * thumb all the way to the right.
 */
class TimelineScrollbar(val timeline: Timeline, zoomPanComponent: JComponent) :
  JBScrollBar(HORIZONTAL) {

  private val aspectObserver = AspectObserver()
  private var updating = false
  private var checkStream = false

  init {
    val onChange = { modelChanged() }
    timeline.viewRange.addDependency(aspectObserver).onChange(RANGE, onChange)
    timeline.dataRange.addDependency(aspectObserver).onChange(RANGE, onChange)
    val scrollbarUi = StreamingScrollbarUi()
    setUI(scrollbarUi)
    addPropertyChangeListener {
      // preserve RangeScrollbarUI always, otherwise it reverts back to the default UI when
      // switching themes.
      if (it.propertyName == "UI" && it.newValue !is RangeScrollBarUI) {
        setUI(scrollbarUi)
      }
    }
    addAdjustmentListener {
      if (!updating) {
        updateModel()
        if (!it.valueIsAdjusting) {
          checkStream = true
        }
      }
    }
    zoomPanComponent.addMouseWheelListener {
      val count = it.preciseWheelRotation
      val isMenuKeyDown = isActionKeyDown(it)
      if (isMenuKeyDown) {
        val anchor = (it.x.toDouble() / it.component.width)
        timeline.handleMouseWheelZoom(count, anchor)
      } else {
        timeline.handleMouseWheelPan(count)
      }
      checkStream = count > 0
    }

    // Ensure the scrollbar is set to the correct initial state.
    modelChanged()
  }

  private fun modelChanged() {
    try {
      updating = true
      val dataRangeUs = timeline.dataRange
      if (dataRangeUs.min < dataRangeUs.max) {
        val viewRangeUs = timeline.viewRange
        val dataExtentMs = (dataRangeUs.length / MS_TO_US).toInt()
        val viewExtentMs = min(dataExtentMs, (viewRangeUs.length / MS_TO_US).toInt())
        val viewRelativeMinMs = max(0, ((viewRangeUs.min - dataRangeUs.min) / MS_TO_US).toInt())
        setValues(viewRelativeMinMs, viewExtentMs, 0, dataExtentMs)
        setBlockIncrement(viewExtentMs)
      } else {
        setValues(0, 0, 0, 0)
      }
    } finally {
      updating = false
    }
  }

  private fun updateModel() {
    (timeline as? StreamingTimeline)?.setStreaming(false)

    val dataRangeUs = timeline.dataRange
    val viewRangeUs = timeline.viewRange
    val valueMs = value
    val viewRelativeMinMs = max(0, ((viewRangeUs.min - dataRangeUs.min) / MS_TO_US).toInt())
    val deltaUs = ((valueMs - viewRelativeMinMs) * MS_TO_US).toDouble()
    viewRangeUs.shift(deltaUs)
  }

  public override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    // Change back to streaming mode as needed
    // Note: isCloseToMax() checks for pixel proximity which relies on the scrollbar's dimension,
    // which is why this code snippet
    // is here instead of animate/postAnimate - we wouldn't get the most current size in those
    // places.
    if (
      checkStream &&
        timeline is StreamingTimeline &&
        !timeline.isStreaming &&
        isCloseToMax() &&
        timeline.canStream()
    ) {
      timeline.setStreaming(true)
    }
    checkStream = false
  }

  @VisibleForTesting
  fun isScrollable(): Boolean {
    val viewRange = timeline.viewRange
    val dataRange = timeline.dataRange
    return viewRange.min >= dataRange.min && viewRange.max <= dataRange.max
  }

  private fun isCloseToMax(): Boolean {
    val model = getModel()
    val snapPercentage = 1 - STREAMING_POSITION_THRESHOLD_PX / width.toFloat()
    return (model.value + model.extent) / model.maximum.toFloat() >= snapPercentage
  }

  private inner class StreamingScrollbarUi : RangeScrollBarUI() {
    override fun doPaintTrack(g: Graphics, c: JComponent, bounds: Rectangle) {
      g.color = StandardColors.DEFAULT_CONTENT_BACKGROUND_COLOR
      g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
    }
  }
}
