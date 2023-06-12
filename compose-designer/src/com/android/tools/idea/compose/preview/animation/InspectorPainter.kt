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

import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.common.surface.DesignSurface
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Polygon
import javax.swing.JSlider

/** [ActionToolbarImpl] with enabled navigation. */
open class DefaultToolbarImpl(surface: DesignSurface<*>, place: String, actions: List<AnAction>) :
  ActionToolbarImpl(place, DefaultActionGroup(actions), true) {
  init {
    targetComponent = surface
    ActionToolbarUtil.makeToolbarNavigable(this)
    layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    setMinimumButtonSize(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
  }
}

internal class SingleButtonToolbar(surface: DesignSurface<*>, place: String, action: AnAction) :
  DefaultToolbarImpl(surface, place, listOf(action)) {
  // From ActionToolbar#setMinimumButtonSize, all the toolbar buttons have 25x25 pixels by default.
  // Set the preferred size of the
  // toolbar to be 5 pixels more in both height and width, so it fits exactly one button plus a
  // margin
  override fun getPreferredSize() = JBUI.size(30, 30)
}

/** Graphics elements corresponding to painting the inspector in [AnimationPreview]. */
object InspectorPainter {

  object Slider {

    /** Minimum distance between major ticks in the timeline. */
    private const val MINIMUM_TICK_DISTANCE = 150

    private val TICK_INCREMENTS =
      arrayOf(
        1_000_000_000,
        100_000_000,
        10_000_000,
        1_000_000,
        100_000,
        10_000,
        10_000,
        1_000,
        200,
        50,
        10,
        5,
        2
      )

    /**
     * Get the dynamic tick increment for horizontal slider:
     * * its width should be bigger than [MINIMUM_TICK_DISTANCE]
     * * tick increment is rounded to nearest [TICK_INCREMENTS]x
     */
    fun getTickIncrement(slider: JSlider, minimumTickSize: Int = MINIMUM_TICK_DISTANCE): Int {
      if (slider.maximum == 0 || slider.width == 0) return slider.maximum
      val increment =
        (minimumTickSize.toFloat() / slider.width * (slider.maximum - slider.minimum)).toInt()
      TICK_INCREMENTS.forEach {
        if (increment >= it) return@getTickIncrement (increment / (it - 1)) * it
      }
      return 1
    }
  }

  /** Thumb displayed in animation timeline. */
  object Thumb {
    private val THUMB_COLOR = JBColor(0x4A81FF, 0xB4D7FF)

    /** Half width of the shape used as the handle of the timeline scrubber. */
    private const val HANDLE_HALF_WIDTH = 5

    /** Half height of the shape used as the handle of the timeline scrubber. */
    private const val HANDLE_HALF_HEIGHT = 5

    /**
     * Paint a thumb for horizontal slider.
     *
     * @param x bottom position of the scrubber
     * @param y bottom position of the scrubber
     */
    fun paintThumbForHorizSlider(g: Graphics2D, x: Int, y: Int, height: Int) {
      g.color = THUMB_COLOR
      g.stroke = InspectorLayout.simpleStroke
      g.drawLine(x, y, x, y + height)
      // The scrubber handle should have the following shape:
      //         ___
      //        |   |
      //         \ /
      // We add 5 points with the following coordinates:
      // (x, y): bottom of the scrubber handle
      // (x - halfWidth, y - halfHeight): where the scrubber angled part meets the vertical one
      // (left side)
      // (x - halfWidth, y - Height): top-left point of the scrubber, where there is a right angle
      // (x + halfWidth, y - Height): top-right point of the scrubber, where there is a right angle
      // (x + halfWidth, y - halfHeight): where the scrubber angled part meets the vertical one
      // (right side)
      val handleHeight = HANDLE_HALF_HEIGHT * 2
      val xPoints =
        intArrayOf(
          x,
          x - HANDLE_HALF_WIDTH,
          x - HANDLE_HALF_WIDTH,
          x + HANDLE_HALF_WIDTH,
          x + HANDLE_HALF_WIDTH
        )
      val yPoints =
        intArrayOf(
          y,
          y - HANDLE_HALF_HEIGHT,
          y - handleHeight,
          y - handleHeight,
          y - HANDLE_HALF_HEIGHT
        )
      g.fillPolygon(xPoints, yPoints, xPoints.size)
    }
  }

  /**
   * Diamond shape displayed at the start and the end of an animation for each animation curve.
   *
   * @param x coordinate of the center of the diamond
   * @param y coordinate of the center of the diamond
   * @param colorIndex index of the color from [GRAPH_COLORS]
   */
  class Diamond(val x: Int, val y: Int, private val colorIndex: Int) {
    // The diamond should have the following shape:
    //         /\
    //         \/
    // We add 4 points with the following coordinates:
    // (x, y - size): top point
    // (x + size, y): right point
    // (x, y + size): bottom point
    // (x - size, y): left point
    // where (x, y) is the center of the diamond
    private fun xArray(size: Int) = intArrayOf(x, x + size, x, x - size)
    private fun yArray(size: Int) = intArrayOf(y - size, y, y + size, y)
    private val diamond = Polygon(xArray(diamondSize()), yArray(diamondSize()), 4)
    private val diamondOutline = Polygon(xArray(diamondSize() + 1), yArray(diamondSize() + 1), 4)

    companion object {
      /** Size of the diamond shape used as the graph size limiter. */
      fun diamondSize() = JBUI.scale(6)
    }

    /** Paint diamond shape. */
    fun paint(g: Graphics2D, hover: Boolean) {
      g.color =
        if (hover) InspectorColors.LINE_OUTLINE_COLOR_ACTIVE
        else JBColor(Color.white, JBColor.border().darker())
      g.fillPolygon(diamondOutline)
      g.color = InspectorColors.GRAPH_COLORS[colorIndex % InspectorColors.GRAPH_COLORS.size]
      g.fillPolygon(diamond)
    }

    fun paintOutline(g: Graphics2D) {
      g.color = InspectorColors.GRAPH_COLORS[colorIndex % InspectorColors.GRAPH_COLORS.size]
      g.stroke = InspectorLayout.simpleStroke
      g.drawPolygon(diamondOutline)
    }

    fun contains(x: Int, y: Int) = diamondOutline.contains(x, y)
  }
}
