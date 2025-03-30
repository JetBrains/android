/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.animation

import com.android.tools.idea.flags.StudioFlags.COMPOSE_ANIMATION_PREVIEW_COORDINATION_DRAG
import com.android.tools.idea.preview.animation.timeline.PositionProxy
import com.android.tools.idea.preview.animation.timeline.TimelineElement
import com.android.tools.idea.preview.animation.timeline.TimelineElementStatus
import com.android.tools.idea.res.clamp
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.plaf.basic.BasicSliderUI
import kotlin.math.max

/**
 * Default max duration (ms) of the animation preview when it's not possible to get it from andoidx.
 */
const val DEFAULT_ANIMATION_PREVIEW_MAX_DURATION_MS = 10000L

/** Timeline slider with auto-resized ticks and labels distance. */
open class TimelinePanel(val tooltip: Tooltip, val tracker: AnimationTracker) :
  JSlider(0, DEFAULT_ANIMATION_PREVIEW_MAX_DURATION_MS.toInt(), 0) {
  private var cachedSliderWidth = 0
  private var cachedMax = 0

  /** Slider thumb. It is in separate component, so it could be painted on top of other layers. */
  inner class Thumb : JPanel() {
    override fun paintComponent(g: Graphics?) {
      super.paintComponent(g)
      InspectorPainter.Thumb.paintThumbForHorizSlider(
        g as Graphics2D,
        x = sliderUI.panelThumbRect().x + sliderUI.panelThumbRect().width / 2,
        y = InspectorLayout.timelineHeaderHeightScaled(),
        height = sliderUI.panelThumbRect().height,
      )
    }

    fun resizeThumb() {
      // Resize to fit the parent.
      size = Dimension(this@TimelinePanel.width, this@TimelinePanel.height)
      location = Point(0, 0)
    }

    val resizeAdapter =
      object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
          resizeThumb()
        }
      }

    init {
      background = UIUtil.TRANSPARENT_COLOR
      border = JBEmptyBorder(0)
      isOpaque = false
    }
  }

  init {
    paintTicks = false
    paintLabels = true
    updateMajorTicks()
    setUI(createSliderUI())
    addComponentListener(
      object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) = updateMajorTicks()
      }
    )

    Thumb().let {
      add(it)
      it.resizeThumb()
      setComponentZOrder(it, 0)
      addComponentListener(it.resizeAdapter)
    }
  }

  val sliderUI: TimelineSliderUI
    get() = ui as TimelineSliderUI

  private var zoomValue = 1

  fun scale(z: Int) {
    zoomValue = z
  }

  val dragEndListeners: MutableList<() -> Unit> = mutableListOf()

  open fun createSliderUI() = TimelineSliderUI(this)

  override fun updateUI() {
    updateLabelUIs()
  }

  override fun setMaximum(maximum: Int) {
    super.setMaximum(maximum)
    updateMajorTicks()
  }

  override fun getPreferredSize(): Dimension {
    return Dimension(
      zoomValue * width - 50,
      InspectorLayout.timelineHeaderHeightScaled() + sliderUI.elements.sumOf { it.heightScaled() },
    )
  }

  override fun getFont(): Font = UIUtil.getFont(UIUtil.FontSize.SMALL, null)

  private fun updateMajorTicks() {
    if (width == cachedSliderWidth && maximum == cachedMax) return
    cachedSliderWidth = width
    cachedMax = maximum
    val tickIncrement = InspectorPainter.Slider.getTickIncrement(this)
    // First, calculate where the labels are going to be painted, based on the maximum. We won't
    // paint the major ticks themselves, as
    // minor ticks will be painted instead. The major ticks spacing is only set so the labels are
    // painted in the right place.
    setMajorTickSpacing(tickIncrement)
    labelTable =
      if (tickIncrement == 0) {
        // Handle the special case where maximum == 0 and we only have the "0ms" label.
        labelTable
      } else {
        createStandardLabels(tickIncrement)
      }
  }
}

/**
 * Modified [JSlider] UI to simulate a timeline-like view. In general lines, the following
 * modifications are made:
 * * The horizontal track is hidden, so only the vertical thumb is shown
 * * The vertical thumb is a vertical line that matches the parent height
 * * The tick lines also match the parent height
 */
open class TimelineSliderUI(val timeline: TimelinePanel) : BasicSliderUI(timeline) {

  private data class VerticalTick(val x: Int, val y1: Int, val y2: Int)

  val positionProxy =
    object : PositionProxy {
      override fun valueForXPosition(value: Int): Int =
        this@TimelineSliderUI.valueForXPosition(value)

      override fun xPositionForValue(value: Int): Int =
        this@TimelineSliderUI.xPositionForValue(value)

      override fun maximumXPosition(): Int = xPositionForValue(slider.maximum)

      override fun minimumXPosition(): Int = xPositionForValue(slider.minimum)

      override fun maximumValue(): Int = slider.maximum

      override fun minimumValue(): Int = slider.minimum
    }

  val panelThumbRect: () -> Rectangle = { super.thumbRect }

  /** List of elements to display. */
  var elements: List<TimelineElement> = listOf()

  /** Element currently hovered or dragged. */
  var activeElement: TimelineElement? = null
    private set(value) {
      field = if (COMPOSE_ANIMATION_PREVIEW_COORDINATION_DRAG.get()) value else null
    }

  private fun moreThanOneTimelineElementInPanel() = elements.size > 1

  open fun paintElements(g: Graphics2D) {
    elements.forEach { it.paint(g) }
  }

  final override fun getThumbSize(): Dimension {
    val originalSize = super.getThumbSize()
    return if (slider.parent == null) originalSize
    else
      Dimension(
        originalSize.width,
        slider.parent.height - InspectorLayout.timelineHeaderHeightScaled(),
      )
  }

  final override fun calculateTickRect() {
    // Make the vertical tick lines cover the entire panel.
    tickRect.x = thumbRect.x
    tickRect.y = thumbRect.y
    tickRect.width = thumbRect.width
    tickRect.height = thumbRect.height + InspectorLayout.timelineHeaderHeightScaled()
  }

  final override fun calculateLabelRect() {
    super.calculateLabelRect()
    labelRect.x = 0
    labelRect.y = 0
    labelRect.y = InspectorLayout.timelineLabelVerticalMarginScaled()
  }

  final override fun paintTrack(g: Graphics) {
    g as Graphics2D
    val frozenLines = getFrozenLines()
    paintMajorTicks(g)
    paintVerticalFrozenTicks(g, frozenLines)
    paintElements(g)
    paintFreezeLines(g, frozenLines)
  }

  final override fun paintFocus(g: Graphics?) {
    // BasicSliderUI paints a dashed rect around the slider when it's focused. We shouldn't paint
    // anything.
  }

  final override fun paintLabels(g: Graphics?) {
    super.paintLabels(g)
    // Draw the line border below the labels.
    g as Graphics2D
    g.color = JBColor.border()
    g.stroke = BasicStroke(1f)
    val borderHeight = InspectorLayout.timelineHeaderHeightScaled()
    g.drawLine(-5, borderHeight, slider.width + 5, borderHeight)
  }

  final override fun paintThumb(g: Graphics) {
    // Don't paint thumb here as it will be on a wrong layer.
  }

  final override fun createTrackListener(slider: JSlider) = TimelineTrackListener()

  private fun paintMajorTicks(g: Graphics2D) {
    // Set background color
    g.color =
      if (
        !moreThanOneTimelineElementInPanel() &&
          elements.firstOrNull()?.frozenState?.isFrozen == true
      ) {
        InspectorColors.TIMELINE_FROZEN_BACKGROUND_COLOR
      } else {
        InspectorColors.TIMELINE_BACKGROUND_COLOR
      }
    g.fillRect(
      0,
      InspectorLayout.timelineHeaderHeightScaled(),
      slider.width,
      slider.height - InspectorLayout.timelineHeaderHeightScaled(),
    )

    if (moreThanOneTimelineElementInPanel()) {
      var totalHeight = InspectorLayout.timelineHeaderHeightScaled()
      elements.forEach { element ->
        if (element.frozenState.isFrozen) {
          g.color = InspectorColors.TIMELINE_FROZEN_BACKGROUND_COLOR
          g.fillRect(0, totalHeight, slider.width, element.heightScaled())
        }
        totalHeight += element.heightScaled()
      }
    }
    // Add vertical ticks.
    g.color = InspectorColors.TIMELINE_TICK_COLOR
    getMajorTicksSpacing().forEach { tick ->
      val xPos = xPositionForValue(tick)
      g.drawLine(xPos, InspectorLayout.timelineHeaderHeightScaled(), xPos, tickRect.height)
    }
  }

  private fun getMajorTicksSpacing(): List<Int> {
    val tickIncrement = max(1, slider.majorTickSpacing / InspectorLayout.TIMELINE_TICKS_PER_LABEL)
    return (0..slider.maximum step tickIncrement).toList()
  }

  /**
   * Get vertical freeze lines for all frozen elements. If [moreThanOneTimelineElementInPanel] is
   * not true, the line will have the height of the panel.
   */
  private fun getFrozenLines(): List<VerticalTick> {
    val frozenTicks = mutableListOf<VerticalTick>()
    var totalHeight = InspectorLayout.timelineHeaderHeightScaled()
    elements.forEach { element ->
      if (element.frozenState.isFrozen) {
        val x = xPositionForValue(element.frozenState.frozenAt)
        val y1 = totalHeight + 2
        val y2 = totalHeight + element.heightScaled() - 2
        frozenTicks.add(VerticalTick(x, y1, y2))
      }
      totalHeight += element.heightScaled()
    }
    return frozenTicks
  }

  /**
   * Paint vertical freeze lines for all frozen elements. If [moreThanOneTimelineElementInPanel] is
   * not true, the line will have the height of the panel.
   */
  private fun paintFreezeLines(g: Graphics2D, lines: List<VerticalTick>) {
    g.color = InspectorColors.FREEZE_LINE_COLOR
    g.stroke = InspectorLayout.freezeLineStroke
    lines.forEach { g.drawLine(it.x, it.y1, it.x, it.y2) }
  }

  private fun paintVerticalFrozenTicks(g: Graphics2D, ticks: List<VerticalTick>) {
    g.color = InspectorColors.TIMELINE_FROZEN_TICK_COLOR
    g.stroke = InspectorLayout.simpleStroke
    getMajorTicksSpacing().forEach { tick ->
      val xPos = xPositionForValue(tick)
      ticks.forEach { g.drawLine(xPos, it.y1, xPos, it.y2) }
    }
  }

  /** [TrackListener] to allow setting [slider] value when clicking and scrubbing the timeline. */
  inner class TimelineTrackListener : TrackListener() {

    private val tooltipAdapter = timeline.tooltip.adapter

    private var isDragging = false

    private var dragStartXPoint = 0

    override fun mousePressed(e: MouseEvent) {
      // We override the parent class behavior completely because it executes more operations than
      // we need, being less performant than
      // this method. Since it recalculates the geometry of all components, the resulting UI on
      // mouse press is not what we aim for.
      currentMouseX = e.x
      slider.parent
        ?.requestFocus() // Request focus to the timeline, so the selected tab actually gets the
      // focus
      dragStartXPoint = e.x
      activeElement?.status = TimelineElementStatus.Dragged
      if (activeElement == null) updateThumbLocationAndSliderValue()
    }

    override fun mouseDragged(e: MouseEvent) {
      super.mouseDragged(e)
      tooltipAdapter.mouseDragged(e)
      val draggedElement = activeElement
      if (draggedElement?.status == TimelineElementStatus.Dragged) {
        val deltaPx = e.x - dragStartXPoint
        /**
         * Forces the final newOffsetPx value to respect at least one of the boundaries of timeline,
         * preventing the element from moving entirely outside the allowed range.
         */
        val newOffsetPx =
          clamp(
            draggedElement.offsetPx + deltaPx,
            timeline.sliderUI.positionProxy.minimumXPosition() - draggedElement.maxX,
            timeline.sliderUI.positionProxy.maximumXPosition() - draggedElement.minX,
          )
        activeElement?.setNewOffset(newOffsetPx)
        dragStartXPoint = e.x
      } else {
        updateThumbLocationAndSliderValue()
      }
      isDragging = true
      updateTooltip(e)
      slider.repaint()
    }

    override fun mouseReleased(e: MouseEvent) {
      super.mouseReleased(e)
      if (activeElement == null) {
        if (isDragging) timeline.tracker.dragAnimationInspectorTimeline()
        else timeline.tracker.clickAnimationInspectorTimeline()
      } else {
        if (isDragging) timeline.tracker.dragTimelineLine()
        // TODO Add click event for timeline element.
        else timeline.tracker.clickAnimationInspectorTimeline()
      }

      isDragging = false
      if (activeElement?.status == TimelineElementStatus.Dragged) {
        timeline.dragEndListeners.forEach { it() }
      }
      activeElement?.status = TimelineElementStatus.Inactive
      activeElement = elements.firstOrNull { it.contains(e.point) }
      activeElement?.status = TimelineElementStatus.Hovered
    }

    override fun mouseMoved(e: MouseEvent) {
      super.mouseMoved(e)
      tooltipAdapter.mouseMoved(e)
      slider.repaint()
      if (isDragging) return
      activeElement?.status = TimelineElementStatus.Inactive
      activeElement = elements.firstOrNull { it.contains(e.point) }
      activeElement?.status = TimelineElementStatus.Hovered
      updateTooltip(e)
    }

    override fun mouseExited(e: MouseEvent?) {
      super.mouseExited(e)
      tooltipAdapter.mouseExited(e)
    }

    private fun updateTooltip(e: MouseEvent) {
      val tooltipInfo = elements.firstNotNullOfOrNull { it.getTooltip(e.point) }
      if (tooltipInfo != null) {
        if (timeline.tooltip.tooltipInfo == null) tooltipAdapter.mouseEntered(e)
        timeline.tooltip.tooltipInfo = tooltipInfo
      } else {
        if (timeline.tooltip.tooltipInfo != null) tooltipAdapter.mouseExited(e)
        timeline.tooltip.tooltipInfo = null
      }
    }

    private fun updateThumbLocationAndSliderValue() {
      val halfWidth = thumbRect.width / 2
      // Make sure the thumb X coordinate is within the slider's min and max. Also, subtract half of
      // the width so the center is aligned.
      val thumbX =
        currentMouseX.coerceIn(
          xPositionForValue(slider.minimum),
          xPositionForValue(slider.maximum),
        ) - halfWidth
      setThumbLocation(thumbX, thumbRect.y)
      slider.value = valueForXPosition(currentMouseX)
    }
  }
}
