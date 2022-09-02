/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation.timeline

import com.android.tools.idea.compose.preview.animation.AnimatedProperty
import com.android.tools.idea.compose.preview.animation.InspectorColors
import com.android.tools.idea.compose.preview.animation.InspectorColors.GRAPH_COLORS
import com.android.tools.idea.compose.preview.animation.InspectorColors.GRAPH_COLORS_WITH_ALPHA
import com.android.tools.idea.compose.preview.animation.InspectorLayout
import com.android.tools.idea.compose.preview.animation.InspectorPainter.Diamond
import com.google.common.annotations.VisibleForTesting
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D

/** Curve for one component of [AnimatedProperty]. */
class ComponentCurve(
  state: ElementState,
  val component: AnimatedProperty.AnimatedComponent<Double>,
  minX: Int,
  maxX: Int,
  rowMinY: Int,
  private val curve: Path2D,
  private val colorIndex: Int,
  positionProxy: PositionProxy
) : TimelineElement(state, minX, maxX, positionProxy) {

  companion object {
    /**
     * Create a [ComponentCurve] for the [AnimatedProperty]
     * @param componentId Id of component for the [property]
     * @param rowMinY minimum y position from there row starts
     * @param positionProxy is [PositionProxy] for the slider
     * @param colorIndex index of the color the curve should be painted
     */
    fun create(
      state: ElementState,
      property: AnimatedProperty<Double>,
      componentId: Int,
      rowMinY: Int,
      positionProxy: PositionProxy,
      colorIndex: Int
    ): ComponentCurve =
      property.components[componentId].let { component ->
        val curve: Path2D = Path2D.Double()
        val animationYMin = component.minValue
        val isZeroDuration = property.endMs == property.startMs
        val isZeroHeight = component.minValue == component.maxValue
        val zeroDurationXOffset = if (isZeroDuration) 1 else 0
        val minX = positionProxy.xPositionForValue(property.startMs)
        val maxX = positionProxy.xPositionForValue(property.endMs)
        val minY = rowMinY + InspectorLayout.CURVE_TOP_OFFSET
        val maxY =
          rowMinY + InspectorLayout.timelineLineRowHeightScaled() -
            InspectorLayout.curveBottomOffset()
        curve.moveTo(minX.toDouble() - zeroDurationXOffset, maxY.toDouble())
        when {
          isZeroDuration -> {
            // If animation duration is zero, for example for snap animation - draw a vertical line,
            // It gives a visual feedback what animation is happened at that point and what graph is
            // not missing where.
            curve.lineTo(minX.toDouble() - zeroDurationXOffset, minY.toDouble())
            curve.lineTo(maxX.toDouble() + zeroDurationXOffset, minY.toDouble())
          }
          isZeroHeight -> {
            // Do nothing if curve is flat.
          }
          else -> {
            val stepY = (maxY - minY) / (component.maxValue - animationYMin)
            component.points.forEach { (ms, value) ->
              curve.lineTo(
                positionProxy.xPositionForValue(ms).toDouble(),
                maxY - (value.toDouble() - animationYMin) * stepY
              )
            }
          }
        }
        curve.lineTo(maxX.toDouble() + zeroDurationXOffset, maxY.toDouble())
        curve.lineTo(minX.toDouble() - zeroDurationXOffset, maxY.toDouble())

        return ComponentCurve(
          state = state,
          component = component,
          minX = minX,
          maxX = maxX,
          rowMinY = rowMinY,
          curve = curve,
          colorIndex,
          positionProxy
        )
      }
  }

  @VisibleForTesting
  val curveBaseY =
    rowMinY + InspectorLayout.timelineCurveRowHeightScaled() - InspectorLayout.curveBottomOffset()
  private var startDiamond = Diamond(minX, curveBaseY, colorIndex)
  private var endDiamond = Diamond(maxX, curveBaseY, colorIndex)
  private val startDiamondNoOffset = Diamond(minX, curveBaseY, colorIndex)
  private val endDiamondNoOffset = Diamond(maxX, curveBaseY, colorIndex)

  private val boxedLabelPositionWithoutOffset =
    Point(minX + InspectorLayout.labelOffset, curveBaseY + InspectorLayout.labelOffset)

  /** Position from where [BoxedLabel] should be painted. */
  var boxedLabelPosition = boxedLabelPositionWithoutOffset
    private set

  override var height: Int = InspectorLayout.TIMELINE_CURVE_ROW_HEIGHT

  init {
    moveComponents(offsetPx)
  }

  var curveOffset = 0

  override fun moveComponents(actualDelta: Int) {
    startDiamond = Diamond(minX + offsetPx, curveBaseY, colorIndex)
    endDiamond = Diamond(maxX + offsetPx, curveBaseY, colorIndex)
    boxedLabelPosition =
      Point(
        (boxedLabelPositionWithoutOffset.x + offsetPx).coerceIn(
          positionProxy.minimumXPosition(),
          positionProxy.maximumXPosition()
        ),
        boxedLabelPositionWithoutOffset.y
      )
    curveOffset += actualDelta
    curve.transform(AffineTransform.getTranslateInstance(actualDelta.toDouble(), 0.0))
  }

  override fun reset() {
    super.reset()
    moveComponents(-curveOffset)
  }

  /** If point [x], [y] is hovering the curve. */
  override fun contains(x: Int, y: Int): Boolean {
    return curve.contains(x.toDouble(), y.toDouble()) ||
      startDiamond.contains(x, y) ||
      endDiamond.contains(x, y)
  }

  /**
   * Painting the animation curve
   * * two [Diamond] shapes at the start and the end of the animation
   * * solid line at the bottom of the animation
   * * animation curve itself
   * * (optional) dashed lines - links to the next curve diamonds
   *
   * @params colorIndex index of the color from [GRAPH_COLORS]
   * @rowHeight total row height including all labels, offset, etc
   */
  override fun paint(g: Graphics2D) {
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
    g.stroke = InspectorLayout.simpleStroke
    g.drawLine(minX + offsetPx, curveBaseY, maxX + offsetPx, curveBaseY)
    if (component.linkToNext) {
      g.stroke = InspectorLayout.dashedStroke
      g.drawLine(
        minX + offsetPx,
        curveBaseY,
        minX + offsetPx,
        curveBaseY + heightScaled() - Diamond.diamondSize()
      )
      g.drawLine(
        maxX + offsetPx,
        curveBaseY,
        maxX + offsetPx,
        curveBaseY + heightScaled() - Diamond.diamondSize()
      )
      g.stroke = InspectorLayout.simpleStroke
    }
    g.color = GRAPH_COLORS_WITH_ALPHA[colorIndex % GRAPH_COLORS.size]
    val prevAntiAliasHint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.fill(curve)
    if (status == TimelineElementStatus.Dragged || status == TimelineElementStatus.Hovered) {
      g.color = InspectorColors.LINE_OUTLINE_COLOR_ACTIVE
      g.draw(curve)
    }
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, prevAntiAliasHint)
    startDiamond.paint(
      g,
      status == TimelineElementStatus.Dragged || status == TimelineElementStatus.Hovered
    )
    endDiamond.paint(
      g,
      status == TimelineElementStatus.Dragged || status == TimelineElementStatus.Hovered
    )

    if (offsetPx != 0) {
      g.stroke = InspectorLayout.dashedStroke
      g.color = GRAPH_COLORS_WITH_ALPHA[colorIndex % GRAPH_COLORS.size]
      if (offsetPx > 0) {
        g.drawLine(
          minX + Diamond.diamondSize() + 1,
          curveBaseY,
          minX + offsetPx - Diamond.diamondSize() - 1,
          curveBaseY
        )
        startDiamondNoOffset.paintOutline(g)
      } else if (offsetPx < 0) {
        g.drawLine(
          maxX - Diamond.diamondSize() - 1,
          curveBaseY,
          maxX + offsetPx + Diamond.diamondSize() + 1,
          curveBaseY
        )
        endDiamondNoOffset.paintOutline(g)
      }
    }
  }
}
