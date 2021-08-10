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

import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Stroke
import java.awt.font.TextLayout
import java.awt.geom.Path2D
import javax.swing.JSlider

/**
 * Graphics elements corresponding to painting the animation curves in [AnimationInspectorPanel].
 */
object CurvePainter {

  class CurveInfo(
    val minX: Int,
    val maxX: Int,
    val y: Int,
    val curve: Path2D,
    val linkedToNextCurve: Boolean = false)

  /** List of colors for graphs. */
  val GRAPH_COLORS = arrayListOf(
    JBColor(0xa6bcc9, 0x8da9ba),
    JBColor(0xaee3fe, 0x86d5fe),
    JBColor(0xf8a981, 0xf68f5b),
    JBColor(0x89e69a, 0x67df7d),
    JBColor(0xb39bde, 0x9c7cd4),
    JBColor(0xea85aa, 0xe46391),
    JBColor(0x6de9d6, 0x49e4cd),
    JBColor(0xe3d2ab, 0xd9c28c),
    JBColor(0x0ab4ff, 0x0095d6),
    JBColor(0x1bb6a2, 0x138173),
    JBColor(0x9363e3, 0x7b40dd),
    JBColor(0xe26b27, 0xc1571a),
    JBColor(0x4070bf, 0x335a99),
    JBColor(0xc6c54e, 0xadac38),
    JBColor(0xcb53a3, 0xb8388e),
    JBColor(0x3d8eff, 0x1477ff))

  /** List of semi-transparent colors for graphs. */
  private val GRAPH_COLORS_WITH_ALPHA = GRAPH_COLORS.map { ColorUtil.withAlpha(it, 0.7) }

  private var DASHED_STROKE: Stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f,
                                                  floatArrayOf(JBUI.scale(3).toFloat()), 0f)

  private var SIMPLE_STROKE = BasicStroke(1f)

  /**
   * Painting the animation curve
   *  * two [Diamond] shapes at the start and the end of the animation
   *  * solid line at the bottom of the animation
   *  * animation curve itself
   *  * (optional) dashed lines - links to the next curve diamonds
   *
   * @params colorIndex index of the color from [GRAPH_COLORS]
   * @rowHeight total row height including all labels, offset, etc
   */
  fun paintCurve(g: Graphics2D, curveInfo: CurveInfo, colorIndex: Int, rowHeight: Int) {
    //                 ___        ___         ___
    //                /   \      /   \       /   \ (curve)
    //               /     \    /     \     /     \
    //              /       \__/       \___/       \
    //   (diamond) /\_______________________________/\ (diamond)
    //             \/           (solid line)        \/
    //             .                                .
    //             .                                .
    //             .                                .
    //             .                                . (optional dashed lines)
    //             .                                .
    //
    g.color = GRAPH_COLORS[colorIndex % GRAPH_COLORS.size]
    g.stroke = SIMPLE_STROKE
    g.drawLine(curveInfo.minX, curveInfo.y, curveInfo.maxX, curveInfo.y)
    if (curveInfo.linkedToNextCurve) {
      g.stroke = DASHED_STROKE
      g.drawLine(curveInfo.minX, curveInfo.y, curveInfo.minX, curveInfo.y + rowHeight - Diamond.DIAMOND_SIZE)
      g.drawLine(curveInfo.maxX, curveInfo.y, curveInfo.maxX, curveInfo.y + rowHeight - Diamond.DIAMOND_SIZE)
      g.stroke = SIMPLE_STROKE
    }
    g.color = GRAPH_COLORS_WITH_ALPHA[colorIndex % GRAPH_COLORS.size]
    val prevAntiAliasHint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.fill(curveInfo.curve)
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, prevAntiAliasHint)
    Diamond.paintDiamond(g, curveInfo.minX, curveInfo.y, colorIndex)
    Diamond.paintDiamond(g, curveInfo.maxX, curveInfo.y, colorIndex)
  }

  /** Label displayed below each animation curve. */
  object BoxedLabel {
    /** Text offset within the background box for a label. */
    private const val TEXT_OFFSET_FOR_LABEL = 6

    private val BOX_COLOR = JBColor(Gray._225, UIUtil.getToolTipActionBackground())

    /** Paint a label with a box on background. */
    fun paintBoxedLabel(g: Graphics2D, label: String, x: Int, y: Int) {
      g.font = UIUtil.getFont(UIUtil.FontSize.NORMAL, null)
      val textLayout = TextLayout(label, g.font, g.fontRenderContext)
      val textBoxHeight = (textLayout.bounds.height + TEXT_OFFSET_FOR_LABEL * 2).toInt()
      val textBoxWidth = (textLayout.bounds.width + TEXT_OFFSET_FOR_LABEL * 2).toInt()
      g.color = BOX_COLOR
      g.fillRoundRect(x, y, textBoxWidth, textBoxHeight, TEXT_OFFSET_FOR_LABEL, TEXT_OFFSET_FOR_LABEL)
      g.color = UIUtil.getLabelDisabledForeground()
      g.drawString(label, x + TEXT_OFFSET_FOR_LABEL, y - TEXT_OFFSET_FOR_LABEL + textBoxHeight)
    }
  }

  object Slider {

    /** Minimum distance between major ticks in the timeline. */
    private const val MINIMUM_TICK_DISTANCE = 150

    private val TICK_INCREMENTS = arrayOf(
      1_000_000_000, 100_000_000, 10_000_000, 1_000_000, 100_000, 10_000, 10_000, 1_000, 200, 50, 10, 5, 2)

    /**
     * Get the dynamic tick increment for horizontal slider:
     * * its width should be bigger than [MINIMUM_TICK_DISTANCE]
     * * tick increment is rounded to nearest [TICK_INCREMENTS]x
     */
    fun getTickIncrement(slider: JSlider, minimumTickSize: Int = MINIMUM_TICK_DISTANCE): Int {
      if (slider.maximum == 0 || slider.width == 0) return slider.maximum
      val increment = (minimumTickSize.toFloat() / slider.width * (slider.maximum - slider.minimum)).toInt()
      TICK_INCREMENTS.forEach { if (increment >= it) return@getTickIncrement (increment / (it - 1)) * it }
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
     * @param x bottom position of the scrubber
     * @param y bottom position of the scrubber
     */
    fun paintThumbForHorizSlider(g: Graphics2D, x: Int, y: Int, height: Int) {
      g.color = THUMB_COLOR
      g.stroke = SIMPLE_STROKE
      g.drawLine(x, y, x, y + height);
      // The scrubber handle should have the following shape:
      //         ___
      //        |   |
      //         \ /
      // We add 5 points with the following coordinates:
      // (x, y): bottom of the scrubber handle
      // (x - halfWidth, y - halfHeight): where the scrubber angled part meets the vertical one (left side)
      // (x - halfWidth, y - Height): top-left point of the scrubber, where there is a right angle
      // (x + halfWidth, y - Height): top-right point of the scrubber, where there is a right angle
      // (x + halfWidth, y - halfHeight): where the scrubber angled part meets the vertical one (right side)
      val handleHeight = HANDLE_HALF_HEIGHT * 2
      val xPoints = intArrayOf(
        x,
        x - HANDLE_HALF_WIDTH,
        x - HANDLE_HALF_WIDTH,
        x + HANDLE_HALF_WIDTH,
        x + HANDLE_HALF_WIDTH
      )
      val yPoints = intArrayOf(
        y,
        y - HANDLE_HALF_HEIGHT,
        y - handleHeight,
        y - handleHeight,
        y - HANDLE_HALF_HEIGHT)
      g.fillPolygon(xPoints, yPoints, xPoints.size)
    }
  }

  /** Diamond shape displayed at the start and the end of an animation for each animation curve. */
  private object Diamond {

    /** Size of the diamond shape used as the graph size limiter. */
    const val DIAMOND_SIZE = 6

    /**
     * Paint diamond shape.
     * @param x coordinate of the center of the diamond
     * @param y coordinate of the center of the diamond
     * @param colorIndex index of the color from [GRAPH_COLORS]
     */
    fun paintDiamond(g: Graphics2D, x: Int, y: Int, colorIndex: Int) {
      // The diamond should have the following shape:
      //         /\
      //         \/
      // We add 4 points with the following coordinates:
      // (x, y - size): top point
      // (x + size, y): right point
      // (x, y + size): bottom point
      // (x - size, y): left point
      // where (x, y) is the center of the diamond
      fun xArray(size: Int) = intArrayOf(x, x + size, x, x - size)
      fun yArray(size: Int) = intArrayOf(y - size, y, y + size, y)
      g.color = JBColor(Color.white, JBColor.border().darker())
      g.fillPolygon(xArray(DIAMOND_SIZE + 1), yArray(DIAMOND_SIZE + 1), 4)
      g.color = GRAPH_COLORS[colorIndex % GRAPH_COLORS.size]
      g.fillPolygon(xArray(DIAMOND_SIZE), yArray(DIAMOND_SIZE), 4)
    }
  }
}