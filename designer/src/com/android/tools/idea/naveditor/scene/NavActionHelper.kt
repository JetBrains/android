/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D

@SwingCoordinate
private val ACTION_STROKE_WIDTH = JBUI.scale(3f)
@SwingCoordinate
private val DASHED_STROKE_CYCLE = JBUI.scale(5f)

@JvmField
@NavCoordinate
val SELF_ACTION_LENGTHS = intArrayOf(JBUI.scale(28), JBUI.scale(26),
                                     JBUI.scale(60), JBUI.scale(8))
val SELF_ACTION_RADII = floatArrayOf(JBUI.scale(10f), JBUI.scale(10f),
                                     JBUI.scale(5f))
@JvmField
val ACTION_STROKE = BasicStroke(ACTION_STROKE_WIDTH, BasicStroke.CAP_BUTT,
                                BasicStroke.JOIN_ROUND)
@JvmField
val DASHED_ACTION_STROKE = BasicStroke(ACTION_STROKE_WIDTH, BasicStroke.CAP_BUTT,
                                       BasicStroke.JOIN_ROUND, DASHED_STROKE_CYCLE,
                                       floatArrayOf(DASHED_STROKE_CYCLE),
                                       DASHED_STROKE_CYCLE)
private val START_DIRECTION = ConnectionDirection.RIGHT
@NavCoordinate
private val CONTROL_POINT_THRESHOLD = JBUI.scale(120)
@NavCoordinate
private val ACTION_PADDING = JBUI.scale(8)

/**
 * Returns an array of five points representing the path of the self action
 * start: middle of right side of component
 * 1: previous point offset 28 to the left
 * 2: previous point offset to 26 below the bottom of component
 * 3: previous point shifted 60 to the right
 * end: previous point shifted up 8
 */
fun selfActionPoints(@SwingCoordinate start: Point2D.Float, @SwingCoordinate end: Point2D.Float, context: SceneContext): Array<Point2D.Float> {
  val p1 = Point2D.Float(start.x + context.getSwingDimension(SELF_ACTION_LENGTHS[0]),
                         start.y)
  val p2 = Point2D.Float(p1.x,
                                       end.y + context.getSwingDimension(SELF_ACTION_LENGTHS[3]))
  val p3 = Point2D.Float(end.x, p2.y)
  return arrayOf(start, p1, p2, p3, end)
}

/**
 * Determines which side of the destination the action should be attached to.
 * If the starting point of the action is:
 * Above the top-left to bottom-right diagonal of the destination, and higher than the center point of the destination: TOP
 * Below the top-right to bottom-left diagonal of the destination, and lower than the center point of the destination: BOTTOM
 * Otherwise: LEFT
 */
fun getDestinationDirection(source: Rectangle2D.Float,
                            destination: Rectangle2D.Float): ConnectionDirection {
  val start = getStartPoint(source)
  val end = getCenterPoint(destination)

  val slope = if (destination.width == 0f) 1f else destination.height / destination.width
  val rise = (start.x - end.x) * slope
  val higher = start.y < end.y

  if (higher && start.y < end.y + rise) {
    return ConnectionDirection.TOP
  }

  return if (!higher && start.y > end.y - rise) {
    ConnectionDirection.BOTTOM
  }
  else ConnectionDirection.LEFT

}

fun getStartPoint(rectangle: Rectangle2D.Float): Point2D.Float {
  return getConnectionPoint(rectangle, START_DIRECTION)
}

fun getArrowRectangle(view: SceneView,
                      p: Point2D.Float,
                      direction: ConnectionDirection): Rectangle2D.Float {
  val rectangle = Rectangle2D.Float()
  val parallel = Coordinates.getSwingDimension(view, NavSceneManager.ACTION_ARROW_PARALLEL)
  val perpendicular = Coordinates.getSwingDimension(view, NavSceneManager.ACTION_ARROW_PERPENDICULAR)
  val deltaX = direction.deltaX.toFloat()
  val deltaY = direction.deltaY.toFloat()

  rectangle.x = p.x + (if (deltaX == 0f) -perpendicular else parallel * (deltaX - 1)) / 2
  rectangle.y = p.y + (if (deltaY == 0f) -perpendicular else parallel * (deltaY - 1)) / 2
  rectangle.width = Math.abs(deltaX * parallel) + Math.abs(deltaY * perpendicular)
  rectangle.height = Math.abs(deltaX * perpendicular) + Math.abs(deltaY * parallel)

  return rectangle
}

enum class ConnectionDirection(val deltaX: Int, val deltaY: Int) {
  LEFT(-1, 0), RIGHT(1, 0), TOP(0, -1), BOTTOM(0, 1);

  var opposite: ConnectionDirection? = null
    private set

  companion object {

    init {
      LEFT.opposite = RIGHT
      RIGHT.opposite = LEFT
      TOP.opposite = BOTTOM
      BOTTOM.opposite = TOP
    }
  }
}

data class CurvePoints(@SwingCoordinate @JvmField val p1: Point2D.Float,
                       @SwingCoordinate @JvmField val p2: Point2D.Float,
                       @SwingCoordinate @JvmField val p3: Point2D.Float,
                       @SwingCoordinate @JvmField val p4: Point2D.Float,
                       @JvmField val dir: ConnectionDirection)

private fun getConnectionPoint(rectangle: Rectangle2D.Float,
                               direction: ConnectionDirection): Point2D.Float {
  return shiftPoint(getCenterPoint(rectangle), direction,
                                                           rectangle.width / 2, rectangle.height / 2)
}

fun getCurvePoints(@SwingCoordinate source: Rectangle2D.Float,
                   @SwingCoordinate dest: Rectangle2D.Float,
                   sceneContext: SceneContext): CurvePoints {
  val destDirection = getDestinationDirection(source, dest)
  val startPoint = getStartPoint(source)
  val endPoint = getEndPoint(sceneContext, dest, destDirection)
  return CurvePoints(startPoint,
                     getControlPoint(sceneContext, startPoint,
                                                                                                                   endPoint,
                                                                                                                   START_DIRECTION),
                     getControlPoint(sceneContext, endPoint,
                                                                                                                   startPoint,
                                                                                                                   destDirection), endPoint,
                     destDirection)
}

private fun getControlPoint(context: SceneContext,
                            p1: Point2D.Float,
                            p2: Point2D.Float,
                            direction: ConnectionDirection): Point2D.Float {
  val shift = Math.min(Math.hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble()) / 2,
                       context.getSwingDimension(CONTROL_POINT_THRESHOLD).toDouble()).toFloat()
  return shiftPoint(p1, direction, shift)
}

fun getEndPoint(context: SceneContext, rectangle: Rectangle2D.Float, direction: ConnectionDirection): Point2D.Float {
  return shiftPoint(
    getArrowPoint(context, rectangle, direction),
    direction,
    context.getSwingDimensionDip(NavSceneManager.ACTION_ARROW_PARALLEL) - 1f)
}

fun getArrowPoint(context: SceneContext,
                  rectangle: Rectangle2D.Float,
                  direction: ConnectionDirection): Point2D.Float {
  @NavCoordinate var shiftY = ACTION_PADDING
  if (direction === ConnectionDirection.TOP) {
    shiftY += JBUI.scale(HEADER_HEIGHT).toInt()
  }
  return shiftPoint(getConnectionPoint(rectangle, direction),
                    direction, context.getSwingDimension(shiftY).toFloat())
}

private fun getCenterPoint(rectangle: Rectangle2D.Float): Point2D.Float {
  return Point2D.Float(rectangle.centerX.toFloat(), rectangle.centerY.toFloat())
}

private fun shiftPoint(p: Point2D.Float, direction: ConnectionDirection, shift: Float): Point2D.Float {
  return shiftPoint(p, direction, shift, shift)
}

private fun shiftPoint(p: Point2D.Float,
                       direction: ConnectionDirection,
                       shiftX: Float,
                       shiftY: Float): Point2D.Float {
  return Point2D.Float(p.x + shiftX * direction.deltaX, p.y + shiftY * direction.deltaY)
}
