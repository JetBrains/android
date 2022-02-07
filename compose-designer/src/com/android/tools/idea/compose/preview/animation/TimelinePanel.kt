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
package com.android.tools.idea.compose.preview.animation

import com.android.tools.idea.compose.preview.animation.timeline.PositionProxy
import com.android.tools.idea.compose.preview.animation.timeline.TimelineElement
import com.android.tools.idea.compose.preview.animation.timeline.TimelineElementStatus
import com.google.wireless.android.sdk.stats.ComposeAnimationToolingEvent
import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import javax.swing.JSlider
import javax.swing.plaf.basic.BasicSliderUI
import kotlin.math.max

/**
 * Default max duration (ms) of the animation preview when it's not possible to get it from Compose.
 */
private const val DEFAULT_MAX_DURATION_MS = 10000L


/**
 * Timeline slider with auto-resized ticks and labels distance.
 */
open class TimelinePanel(private val tracker: ComposeAnimationEventTracker)
  : JSlider(0, DEFAULT_MAX_DURATION_MS.toInt(), 0) {
  private var cachedSliderWidth = 0
  private var cachedMax = 0

  init {
    paintTicks = false
    paintLabels = true
    updateMajorTicks()
    setUI(createSliderUI())
    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) = updateMajorTicks()
    })
  }

  val sliderUI: TimelineSliderUI
    get() = ui as TimelineSliderUI

  var zoomValue = 1
  fun scale(z: Int) {
    zoomValue = z
  }

  open fun createSliderUI() = TimelineSliderUI(this@TimelinePanel, tracker)

  override fun updateUI() {
    setUI(createSliderUI())
    updateLabelUIs()
  }

  override fun setMaximum(maximum: Int) {
    super.setMaximum(maximum)
    updateMajorTicks()
  }

  override fun getPreferredSize(): Dimension {
    return Dimension(zoomValue * width - 50,
                     InspectorLayout.TIMELINE_TOP_OFFSET + sliderUI.elements.sumOf { it.height })
  }

  private fun updateMajorTicks() {
    if (width == cachedSliderWidth && maximum == cachedMax) return
    cachedSliderWidth = width
    cachedMax = maximum
    val tickIncrement = InspectorPainter.Slider.getTickIncrement(this)
    // First, calculate where the labels are going to be painted, based on the maximum. We won't paint the major ticks themselves, as
    // minor ticks will be painted instead. The major ticks spacing is only set so the labels are painted in the right place.
    setMajorTickSpacing(tickIncrement)
    labelTable = if (tickIncrement == 0) {
      // Handle the special case where maximum == 0 and we only have the "0ms" label.
      labelTable
    }
    else {
      createStandardLabels(tickIncrement)
    }
  }
}


/**
 * Modified [JSlider] UI to simulate a timeline-like view. In general lines, the following modifications are made:
 *   * The horizontal track is hidden, so only the vertical thumb is shown
 *   * The vertical thumb is a vertical line that matches the parent height
 *   * The tick lines also match the parent height
 */
open class TimelineSliderUI(slider: JSlider, private val tracker: ComposeAnimationEventTracker?) : BasicSliderUI(slider) {

  val positionProxy = object : PositionProxy {
    override fun valueForXPosition(value: Int): Int =
      this@TimelineSliderUI.valueForXPosition(value)

    override fun xPositionForValue(value: Int): Int =
      this@TimelineSliderUI.xPositionForValue(value)

    override fun maximumXPosition(): Int = xPositionForValue(slider.maximum)

    override fun minimumXPosition(): Int = xPositionForValue(slider.minimum)

    override fun maximumValue(): Int = slider.maximum

    override fun minimumValue(): Int = slider.minimum
  }

  /** List of elements to display. */
  var elements: MutableList<TimelineElement> = mutableListOf()

  /** Element currently hovered or dragged. */
  var activeElement: TimelineElement? = null
    private set

  /** Separate elements with a line. */
  private fun separateElements() = elements.size > 1

  open fun paintElements(g: Graphics2D) {
    elements.forEach {
      it.paint(g)
    }
  }

  final override fun getThumbSize(): Dimension {
    val originalSize = super.getThumbSize()
    return if (slider.parent == null) originalSize
    else Dimension(originalSize.width, slider.parent.height - InspectorLayout.TIMELINE_HEADER_HEIGHT)
  }

  final override fun calculateTickRect() {
    // Make the vertical tick lines cover the entire panel.
    tickRect.x = thumbRect.x
    tickRect.y = thumbRect.y
    tickRect.width = thumbRect.width
    tickRect.height = thumbRect.height + InspectorLayout.TIMELINE_HEADER_HEIGHT
  }

  final override fun calculateLabelRect() {
    super.calculateLabelRect()
    labelRect.x = 0
    labelRect.y = 0
    labelRect.y = InspectorLayout.TIMELINE_LABEL_VERTICAL_MARGIN
  }

  final override fun paintTrack(g: Graphics) {
    g as Graphics2D
    paintMajorTicks(g)
    paintElements(g)
    paintLocks(g)
  }

  final override fun paintFocus(g: Graphics?) {
    // BasicSliderUI paints a dashed rect around the slider when it's focused. We shouldn't paint anything.
  }

  final override fun paintLabels(g: Graphics?) {
    super.paintLabels(g)
    // Draw the line border below the labels.
    g as Graphics2D
    g.color = JBColor.border()
    g.stroke = BasicStroke(1f)
    val borderHeight = InspectorLayout.TIMELINE_HEADER_HEIGHT
    g.drawLine(-5, borderHeight, slider.width + 5, borderHeight)
  }

  final override fun paintThumb(g: Graphics) {
    InspectorPainter.Thumb.paintThumbForHorizSlider(
      g as Graphics2D,
      x = thumbRect.x + thumbRect.width / 2,
      y = thumbRect.y + InspectorLayout.TIMELINE_HEADER_HEIGHT,
      height = thumbRect.height)
  }

  final override fun createTrackListener(slider: JSlider) = TimelineTrackListener()

  private fun paintMajorTicks(g: Graphics2D) {
    // Set background color
    g.color = if (!separateElements() && elements.firstOrNull()?.locked == true) {
      InspectorColors.TIMELINE_FROZEN_BACKGROUND_COLOR
    }
    else {
      InspectorColors.TIMELINE_BACKGROUND_COLOR
    }
    g.fillRect(0, InspectorLayout.TIMELINE_HEADER_HEIGHT, slider.width, slider.height - InspectorLayout.TIMELINE_HEADER_HEIGHT)

    var totalHeight = InspectorLayout.TIMELINE_HEADER_HEIGHT
    if (separateElements()) elements.forEach { element ->
      if (element.locked) {
        g.color = InspectorColors.TIMELINE_FROZEN_BACKGROUND_COLOR
        g.fillRect(0, totalHeight, slider.width, element.height)
      }
      totalHeight += element.height
    }
    // Add vertical ticks.
    g.color = InspectorColors.TIMELINE_TICK_COLOR
    val tickIncrement = max(1, slider.majorTickSpacing / InspectorLayout.TIMELINE_TICKS_PER_LABEL)
    for (tick in 0..slider.maximum step tickIncrement) {
      val xPos = xPositionForValue(tick)
      g.drawLine(xPos, InspectorLayout.TIMELINE_HEADER_HEIGHT, xPos, tickRect.height)
    }
    totalHeight = InspectorLayout.TIMELINE_HEADER_HEIGHT
    if (separateElements()) elements.forEach { element ->
      totalHeight += element.height
      g.drawLine(0, totalHeight, slider.width, totalHeight)
    }
  }

  /**
   * Paint vertical lock lines for all locked elements.
   * If [separateElements] is not true, the lock will have the height of the panel.
   * */
  private fun paintLocks(g: Graphics2D) {
    g.color = InspectorColors.LOCK_COLOR
    g.stroke = InspectorLayout.LOCK_STROKE
    var totalHeight = InspectorLayout.TIMELINE_HEADER_HEIGHT
    if (separateElements()) elements.forEach { element ->
      if (element.locked) {
        val lockedValue = element.state.lockedValue
        g.drawLine(xPositionForValue(lockedValue), totalHeight + 2,
                   xPositionForValue(lockedValue), totalHeight + element.height - 2)
      }
      totalHeight += element.height
    }
    else elements.firstOrNull()?.also {
      if (it.locked) {
        val lockedValue = it.state.lockedValue
        g.drawLine(xPositionForValue(lockedValue), InspectorLayout.TIMELINE_HEADER_HEIGHT + 2,
                   xPositionForValue(lockedValue), slider.height - 2)
      }
    }
  }

  /**
   * [TrackListener] to allow setting [slider] value when clicking and scrubbing the timeline.
   */
  inner class TimelineTrackListener : TrackListener() {

    private var isDragging = false

    private var dragStartXPoint = 0

    override fun mousePressed(e: MouseEvent) {
      // We override the parent class behavior completely because it executes more operations than we need, being less performant than
      // this method. Since it recalculates the geometry of all components, the resulting UI on mouse press is not what we aim for.
      currentMouseX = e.x
      slider.parent?.requestFocus() // Request focus to the timeline, so the selected tab actually gets the focus
      dragStartXPoint = e.x
      activeElement?.status = TimelineElementStatus.Dragged
      if (activeElement == null) updateThumbLocationAndSliderValue()
    }

    override fun mouseDragged(e: MouseEvent) {
      super.mouseDragged(e)
      if (activeElement?.status == TimelineElementStatus.Dragged) {
        activeElement?.move(e.x - dragStartXPoint)
        dragStartXPoint = e.x
      }
      else {
        updateThumbLocationAndSliderValue()
      }
      isDragging = true
      slider.repaint()
    }

    override fun mouseReleased(e: MouseEvent) {
      super.mouseReleased(e)
      tracker?.invoke(
        if (activeElement == null) {
          if (isDragging) ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.DRAG_ANIMATION_INSPECTOR_TIMELINE
          else ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.CLICK_ANIMATION_INSPECTOR_TIMELINE
        }
        else {
          if (isDragging) ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.DRAG_TIMELINE_LINE
          //TODO Add click event for timeline element.
          else ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.CLICK_ANIMATION_INSPECTOR_TIMELINE
        }
      )
      isDragging = false
      activeElement?.status = TimelineElementStatus.Inactive
      activeElement = elements.firstOrNull { it.contains(e.point) }
      activeElement?.status = TimelineElementStatus.Hovered
    }

    override fun mouseMoved(e: MouseEvent) {
      super.mouseMoved(e)
      slider.repaint()
      if (isDragging) return
      activeElement?.status = TimelineElementStatus.Inactive
      activeElement = elements.firstOrNull { it.contains(e.point) }
      activeElement?.status = TimelineElementStatus.Hovered
    }

    private fun updateThumbLocationAndSliderValue() {
      val halfWidth = thumbRect.width / 2
      // Make sure the thumb X coordinate is within the slider's min and max. Also, subtract half of the width so the center is aligned.
      val thumbX = currentMouseX.coerceIn(xPositionForValue(slider.minimum), xPositionForValue(slider.maximum)) - halfWidth
      setThumbLocation(thumbX, thumbRect.y)
      slider.value = valueForXPosition(currentMouseX)
    }
  }
}
