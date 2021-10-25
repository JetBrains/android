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

import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JSlider
import javax.swing.plaf.basic.BasicSliderUI
import kotlin.math.max

/**
 * Height of the animation inspector timeline header, i.e. Transition Properties panel title and timeline labels.
 */
private const val TIMELINE_HEADER_HEIGHT = 25

/** Number of ticks per label in the timeline. */
private const val TICKS_PER_LABEL = 5

/** Vertical margin for labels.  */
private const val LABEL_VERTICAL_MARGIN = 5

/** Background color for the timeline. */
private val TIMELINE_BACKGROUND_COLOR = JBColor(Gray._235, JBColor.background())

/** Color of the ticks for the timeline. */
private val TIMELINE_TICK_COLOR = JBColor(Gray._223, Gray._50)


/**
 * Modified [JSlider] UI to simulate a timeline-like view. In general lines, the following modifications are made:
 *   * The horizontal track is hidden, so only the vertical thumb is shown
 *   * The vertical thumb is a vertical line that matches the parent height
 *   * The tick lines also match the parent height
 */
internal open class TimelinePanel(slider: JSlider) : BasicSliderUI(slider) {

  override fun getThumbSize(): Dimension {
    val originalSize = super.getThumbSize()
    return if (slider.parent == null) originalSize else Dimension(originalSize.width, slider.parent.height - labelsAndTicksHeight())
  }

  override fun calculateTickRect() {
    // Make the vertical tick lines cover the entire panel.
    tickRect.x = thumbRect.x
    tickRect.y = thumbRect.y
    tickRect.width = thumbRect.width
    tickRect.height = thumbRect.height + labelsAndTicksHeight()
  }

  override fun calculateLabelRect() {
    super.calculateLabelRect()
    labelRect.y = LABEL_VERTICAL_MARGIN
  }

  override fun paintTrack(g: Graphics) {
    g as Graphics2D
    paintMajorTicks(g)
  }

  override fun paintFocus(g: Graphics?) {
    // BasicSliderUI paints a dashed rect around the slider when it's focused. We shouldn't paint anything.
  }

  override fun paintLabels(g: Graphics?) {
    super.paintLabels(g)
    // Draw the line border below the labels.
    g as Graphics2D
    g.color = JBColor.border()
    g.stroke = BasicStroke(1f)
    val borderHeight = TIMELINE_HEADER_HEIGHT - 1 // Subtract the stroke (1)
    g.drawLine(0, borderHeight, slider.width, borderHeight)
  }

  override fun paintThumb(g: Graphics) {
    InspectorPainter.Thumb.paintThumbForHorizSlider(
      g as Graphics2D,
      x = thumbRect.x + thumbRect.width / 2,
      y = thumbRect.y + TIMELINE_HEADER_HEIGHT,
      height = thumbRect.height)
  }

  private fun paintMajorTicks(g: Graphics2D) {
    g.color = TIMELINE_BACKGROUND_COLOR
    g.fillRect(0, TIMELINE_HEADER_HEIGHT, slider.width, slider.height - TIMELINE_HEADER_HEIGHT)
    g.color = TIMELINE_TICK_COLOR
    val tickIncrement = max(1, slider.majorTickSpacing / TICKS_PER_LABEL)
    for (tick in 0..slider.maximum step tickIncrement) {
      val xPos = xPositionForValue(tick)
      g.drawLine(xPos, tickRect.y + TIMELINE_HEADER_HEIGHT, xPos, tickRect.height)
    }
  }

  private fun labelsAndTicksHeight() = tickLength + heightOfTallestLabel
}
