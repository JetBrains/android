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
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.naveditor.model.ActionType
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.model.effectiveDestination
import com.android.tools.idea.naveditor.model.getActionType
import com.android.tools.idea.naveditor.model.getEffectiveSource
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D

@SwingCoordinate
private val ACTION_STROKE_WIDTH = JBUI.scale(3f)

@JvmField
@NavCoordinate
val SELF_ACTION_LENGTHS = intArrayOf(JBUI.scale(28), JBUI.scale(26),
                                     JBUI.scale(60), JBUI.scale(8))
val SELF_ACTION_RADII = floatArrayOf(JBUI.scale(10f), JBUI.scale(10f),
                                     JBUI.scale(5f))
@JvmField
val ACTION_STROKE = BasicStroke(ACTION_STROKE_WIDTH, BasicStroke.CAP_BUTT,
                                BasicStroke.JOIN_ROUND)
private val START_DIRECTION = ConnectionDirection.RIGHT
@NavCoordinate
private val CONTROL_POINT_THRESHOLD = JBUI.scale(120)
@NavCoordinate
private val ACTION_PADDING = JBUI.scale(8)

// The radius of the circular image in the pop action icon
@NavCoordinate
val POP_ICON_RADIUS = JBUI.scale(7)

// The distance from the edge of the circular image in the pop action icon to the closest point on the associated action
@NavCoordinate
val POP_ICON_DISTANCE = JBUI.scale(7)

// The maximum distance from the starting point of the action to the closest point to the pop action icon
@NavCoordinate
val POP_ICON_RANGE = JBUI.scale(50)

@NavCoordinate
private val POP_ICON_HORIZONTAL_PADDING = JBUI.scale(2f)

@NavCoordinate
private val POP_ICON_VERTICAL_PADDING = JBUI.scale(5f)

private const val STEP_SIZE = 0.001
private const val STEP_THRESHOLD = 0.4

/**
 * Returns an array of five points representing the path of the self action
 * start: middle of right side of component
 * 1: previous point offset 28 to the left
 * 2: previous point offset to 26 below the bottom of component
 * 3: previous point shifted 60 to the right
 * end: previous point shifted up 8
 */
@SwingCoordinate
fun selfActionPoints(@SwingCoordinate rectangle: Rectangle2D.Float, scale: Float): Array<Point2D.Float> {
  val p0 = getStartPoint(rectangle)
  val p1 = Point2D.Float(p0.x + SELF_ACTION_LENGTHS[0] * scale, p0.y)
  val p2 = Point2D.Float(p1.x, p1.y + rectangle.height / 2 + SELF_ACTION_LENGTHS[1] * scale)
  val p3 = Point2D.Float(p2.x - SELF_ACTION_LENGTHS[2] * scale, p2.y)
  val p4 = Point2D.Float(p3.x, p3.y - SELF_ACTION_LENGTHS[3] * scale)
  return arrayOf(p0, p1, p2, p3, p4)
}

/**
 * Determines which side of the destination the action should be attached to.
 * If the starting point of the action is:
 * Above the top-left to bottom-right diagonal of the destination, and higher than the center point of the destination: TOP
 * Below the top-right to bottom-left diagonal of the destination, and lower than the center point of the destination: BOTTOM
 * Otherwise: LEFT
 */
fun getDestinationDirection(@SwingCoordinate source: Rectangle2D.Float,
                            @SwingCoordinate destination: Rectangle2D.Float): ConnectionDirection {
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

@SwingCoordinate
fun getStartPoint(@SwingCoordinate rectangle: Rectangle2D.Float): Point2D.Float {
  return getConnectionPoint(rectangle, START_DIRECTION)
}

enum class ConnectionDirection(val deltaX: Int, val deltaY: Int) {
  LEFT(-1, 0), RIGHT(1, 0), TOP(0, -1), BOTTOM(0, 1);
}

data class CurvePoints(@SwingCoordinate @JvmField val p1: Point2D.Float,
                       @SwingCoordinate @JvmField val p2: Point2D.Float,
                       @SwingCoordinate @JvmField val p3: Point2D.Float,
                       @SwingCoordinate @JvmField val p4: Point2D.Float,
                       @JvmField val dir: ConnectionDirection)

private fun getConnectionPoint(rectangle: Rectangle2D.Float,
                               direction: ConnectionDirection): Point2D.Float {
  return shiftPoint(getCenterPoint(rectangle), direction, rectangle.width / 2, rectangle.height / 2)
}

@SwingCoordinate
fun getCurvePoints(@SwingCoordinate source: Rectangle2D.Float,
                   @SwingCoordinate dest: Rectangle2D.Float,
                   scale: Float): CurvePoints {
  val destDirection = getDestinationDirection(source, dest)
  val startPoint = getStartPoint(source)
  val endPoint = getEndPoint(scale, dest, destDirection)
  return CurvePoints(startPoint,
                     getControlPoint(scale, startPoint,
                                     endPoint,
                                     START_DIRECTION),
                     getControlPoint(scale, endPoint,
                                     startPoint,
                                     destDirection), endPoint,
                     destDirection)
}

@SwingCoordinate
private fun getControlPoint(scale: Float,
                            @SwingCoordinate p1: Point2D.Float,
                            @SwingCoordinate p2: Point2D.Float,
                            direction: ConnectionDirection): Point2D.Float {
  val shift = Math.min(Math.hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble()) / 2,
                       (CONTROL_POINT_THRESHOLD * scale).toDouble()).toFloat()
  return shiftPoint(p1, direction, shift)
}

fun getEndPoint(scale: Float, rectangle: Rectangle2D.Float, direction: ConnectionDirection): Point2D.Float {
  return shiftPoint(
    getArrowPoint(scale, rectangle, direction),
    direction,
    NavSceneManager.ACTION_ARROW_PARALLEL * scale - 1f)
}

/**
 * Gets a point somewhere on the given action, or null if there was a problem.
 */
@SwingCoordinate
fun getAnyPoint(action: SceneComponent, context: SceneContext): Point2D.Float? {
  val scene = action.scene
  val rootNlComponent = scene.root?.nlComponent ?: return null
  val actionNlComponent = action.nlComponent
  val sourceNlComponent = actionNlComponent.getEffectiveSource(rootNlComponent) ?: return null
  val sourceSceneComponent = scene.getSceneComponent(sourceNlComponent) ?: return null
  val sourceRect = Coordinates.getSwingRectDip(context, sourceSceneComponent.fillDrawRect2D(0, null))

  when (actionNlComponent.getActionType(rootNlComponent)) {
    ActionType.SELF -> {
      val points = selfActionPoints(sourceRect, context.scale.toFloat())
      return Point2D.Float(points[1].x, (points[1].y + points[2].y)/2)
    }
    ActionType.REGULAR, ActionType.EXIT_DESTINATION -> {
      val targetNlComponent = actionNlComponent.effectiveDestination ?: return null
      val destinationSceneComponent = scene.getSceneComponent(targetNlComponent) ?: return null
      val destRect = Coordinates.getSwingRectDip(context, destinationSceneComponent.fillDrawRect2D(0, null))
      val curvePoints = getCurvePoints(sourceRect, destRect, context.scale.toFloat())
      return Point2D.Float(getCurveX(curvePoints, 0.5).toFloat(), getCurveY(curvePoints, 0.5).toFloat())
    }
    ActionType.EXIT, ActionType.GLOBAL ->
      return getCenterPoint(Coordinates.getSwingRectDip(context, action.fillDrawRect2D(0, null)))
    else -> return null
  }
}

@SwingCoordinate
fun getArrowPoint(scale: Float,
                  @SwingCoordinate rectangle: Rectangle2D.Float,
                  direction: ConnectionDirection): Point2D.Float {
  @NavCoordinate var shiftY = ACTION_PADDING
  if (direction === ConnectionDirection.TOP) {
    shiftY += JBUI.scale(HEADER_HEIGHT).toInt()
  }
  return shiftPoint(getConnectionPoint(rectangle, direction),
                    direction, shiftY * scale)
}

/**
 * Returns the drawing rectangle for the pop icon for a regular action
 */
@SwingCoordinate
fun getRegularActionIconRect(@SwingCoordinate source: Rectangle2D.Float,
                             @SwingCoordinate dest: Rectangle2D.Float,
                             scale: Float): Rectangle2D.Float {
  val startPoint = getStartPoint(source)
  val points = getCurvePoints(source, dest, scale)

  var t = 0.0
  var previousX = 0.0
  var previousY = 0.0
  var currentX = getCurveX(points, t)
  var currentY = getCurveY(points, t)
  val range = POP_ICON_RANGE * scale
  val distance = (POP_ICON_RADIUS + POP_ICON_DISTANCE) * scale

  // Search for the best point to attach the pop icon to.
  // Four conditions are:
  //   Don't go past the end of the curve.
  //   Don't go farther away from the starting point than POP_ICON_RANGE
  //   Don't stop while the source would obscure the pop icon (if possible)
  //   Don't go more than STEP_THRESHOLD away from the starting point
  while (t < 1
         && Math.hypot(currentX - startPoint.x, currentY - startPoint.y) < range
         && (currentX - startPoint.x < distance || t < STEP_THRESHOLD)) {
    t += STEP_SIZE
    previousX = currentX
    previousY = currentY
    currentX = getCurveX(points, t)
    currentY = getCurveY(points, t)
  }

  val dx = currentX - previousX
  val dy = currentY - previousY
  val ds = Math.hypot(dx, dy)

  var deltaX = dy * distance / ds
  var deltaY = -dx * distance / ds

  // Choose the counterclockwise normal to the tangent vector unless dx and dy are both negative
  if (dx < 0 && dy < 0) {
    deltaX *= -1
    deltaY *= -1
  }

  val radius = POP_ICON_RADIUS * scale
  return Rectangle2D.Float((currentX + deltaX).toFloat() - radius,
                           (currentY + deltaY).toFloat() - radius,
                           2 * radius, 2 * radius)
}

/**
 * Returns the drawing rectangle for the pop icon for a self action
 */
@SwingCoordinate
fun getSelfActionIconRect(@SwingCoordinate start: Point2D.Float, scale: Float): Rectangle2D.Float {
  val distance = POP_ICON_DISTANCE * scale
  val offsetX = SELF_ACTION_LENGTHS[0] * scale + distance
  val x = start.x + offsetX

  val range = (POP_ICON_RANGE * scale).toDouble()
  val y = start.y + Math.sqrt(Math.max(range * range - offsetX * offsetX, 0.0)).toFloat()

  val radius = POP_ICON_RADIUS  * scale

  return Rectangle2D.Float(x, y, 2 * radius, 2 * radius)
}

/**
 * Returns the drawing rectangle for the pop icon for a horizontal action (i.e. global or exit)
 */
@SwingCoordinate
fun getHorizontalActionIconRect(@SwingCoordinate rectangle: Rectangle2D.Float): Rectangle2D.Float {
  val iconRect = Rectangle2D.Float()
  val scale = rectangle.height / NavSceneManager.ACTION_ARROW_PERPENDICULAR

  iconRect.x = rectangle.x + POP_ICON_HORIZONTAL_PADDING * scale
  iconRect.width = 2 * POP_ICON_RADIUS * scale
  iconRect.height = iconRect.width
  iconRect.y = rectangle.y + (rectangle.height / 2 - iconRect.height - POP_ICON_VERTICAL_PADDING * scale)

  return iconRect
}

private fun getCenterPoint(rectangle: Rectangle2D.Float): Point2D.Float {
  return Point2D.Float(rectangle.centerX.toFloat(), rectangle.centerY.toFloat())
}

private fun shiftPoint(@SwingCoordinate p: Point2D.Float, direction: ConnectionDirection,
                       @SwingCoordinate shift: Float): Point2D.Float {
  return shiftPoint(p, direction, shift, shift)
}

@SwingCoordinate
private fun shiftPoint(@SwingCoordinate p: Point2D.Float,
                       direction: ConnectionDirection,
                       @SwingCoordinate shiftX: Float,
                       @SwingCoordinate shiftY: Float): Point2D.Float {
  return Point2D.Float(p.x + shiftX * direction.deltaX, p.y + shiftY * direction.deltaY)
}

@SwingCoordinate
private fun getCurveX(points: CurvePoints, t: Double): Double {
  return (Math.pow(1 - t, 3.0) * points.p1.x
          + 3 * Math.pow(1 - t, 2.0) * t * points.p2.x
          + 3 * (1 - t) * Math.pow(t, 2.0) * points.p3.x
          + Math.pow(t, 3.0) * points.p4.x)
}

@SwingCoordinate
private fun getCurveY(points: CurvePoints, t: Double): Double {
  return (Math.pow(1 - t, 3.0) * points.p1.y
          + 3 * Math.pow(1 - t, 2.0) * t * points.p2.y
          + 3 * (1 - t) * Math.pow(t, 2.0) * points.p3.y
          + Math.pow(t, 3.0) * points.p4.y)
}
