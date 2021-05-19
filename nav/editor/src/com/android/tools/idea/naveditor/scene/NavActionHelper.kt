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

import com.android.tools.adtui.common.SwingLength
import com.android.tools.adtui.common.SwingPoint
import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.adtui.common.SwingX
import com.android.tools.adtui.common.SwingY
import com.android.tools.adtui.common.distance
import com.android.tools.adtui.common.min
import com.android.tools.adtui.common.times
import com.android.tools.idea.common.model.Scale
import com.android.tools.idea.common.model.scaledAndroidLength
import com.android.tools.idea.common.model.times
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.inlineDrawRect
import com.android.tools.idea.common.scene.inlineScale
import com.android.tools.idea.naveditor.model.ActionType
import com.android.tools.idea.naveditor.model.effectiveDestination
import com.android.tools.idea.naveditor.model.getActionType
import com.android.tools.idea.naveditor.model.getEffectiveSource
import kotlin.math.acos
import kotlin.math.sin

val SELF_ACTION_LENGTHS = arrayOf(28f, 26f, 60f, 8f).map { scaledAndroidLength(it) }
val SELF_ACTION_RADII = arrayOf(10f, 10f, 5f).map { scaledAndroidLength(it) }

private val START_DIRECTION = ConnectionDirection.RIGHT
private val CONTROL_POINT_THRESHOLD = scaledAndroidLength(120f)
private val ACTION_PADDING = scaledAndroidLength(8f)

// The radius of the circular image in the pop action icon
val POP_ICON_RADIUS = scaledAndroidLength(7f)

// The distance from the edge of the circular image in the pop action icon to the closest point on the associated action
val POP_ICON_DISTANCE = scaledAndroidLength(7f)

// The maximum distance from the starting point of the action to the closest point to the pop action icon
val POP_ICON_RANGE = scaledAndroidLength(50f)

// Y offset for self action pop icons
// The x offset is SELF_ACTION_LENGTHS[0] + POP_ICON_DISTANCE and the distance is POP_ICON_RANGE
// Calculate y such that y^2 = d^2 - x^2 using y = d * sin(acos(x/d))
val POP_ICON_Y_OFFSET = POP_ICON_RANGE * sin(acos(((SELF_ACTION_LENGTHS[0] + POP_ICON_DISTANCE) / POP_ICON_RANGE).toDouble())).toFloat()

private val POP_ICON_HORIZONTAL_PADDING = scaledAndroidLength(2f)
private val POP_ICON_VERTICAL_PADDING = scaledAndroidLength(5f)

private const val STEP_SIZE = 0.001f
private const val STEP_THRESHOLD = 0.4f

/**
 * Returns an array of five points representing the path of the self action
 * start: middle of right side of component
 * 1: previous point offset 28 to the left
 * 2: previous point offset to 26 below the bottom of component
 * 3: previous point shifted 60 to the right
 * end: previous point shifted up 8
 */
fun selfActionPoints(rectangle: SwingRectangle, scale: Scale): Array<SwingPoint> {
  val p0 = getStartPoint(rectangle)
  val p1 = SwingPoint(p0.x + SELF_ACTION_LENGTHS[0] * scale, p0.y)
  val p2 = SwingPoint(p1.x, p1.y + rectangle.height / 2 + SELF_ACTION_LENGTHS[1] * scale)
  val p3 = SwingPoint(p2.x - SELF_ACTION_LENGTHS[2] * scale, p2.y)
  val p4 = SwingPoint(p3.x, p3.y - SELF_ACTION_LENGTHS[3] * scale)
  return arrayOf(p0, p1, p2, p3, p4)
}

/**
 * Determines which side of the destination the action should be attached to.
 * If the starting point of the action is:
 * Above the top-left to bottom-right diagonal of the destination, and higher than the center point of the destination: TOP
 * Below the top-right to bottom-left diagonal of the destination, and lower than the center point of the destination: BOTTOM
 * Otherwise: LEFT
 */
fun getDestinationDirection(source: SwingRectangle, destination: SwingRectangle): ConnectionDirection {
  val start = getStartPoint(source)
  val end = destination.center

  val slope = if (destination.width.value == 0f) 1f else destination.height / destination.width
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

fun getStartPoint(rectangle: SwingRectangle): SwingPoint {
  return getConnectionPoint(rectangle, START_DIRECTION)
}

enum class ConnectionDirection(val deltaX: Int, val deltaY: Int) {
  LEFT(-1, 0), RIGHT(1, 0), TOP(0, -1), BOTTOM(0, 1);
}


private fun getConnectionPoint(rectangle: SwingRectangle,
                               direction: ConnectionDirection): SwingPoint {
  return shiftPoint(rectangle.center, direction, (rectangle.width / 2), rectangle.height / 2)
}

fun getCurvePoints(source: SwingRectangle, dest: SwingRectangle, scale: Scale): CurvePoints {
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

private fun getControlPoint(scale: Scale, p1: SwingPoint, p2: SwingPoint, direction: ConnectionDirection): SwingPoint {
  val shift = min(distance(p1, p2) / 2, CONTROL_POINT_THRESHOLD * scale)
  return shiftPoint(p1, direction, shift)
}

fun getEndPoint(scale: Scale, rectangle: SwingRectangle, direction: ConnectionDirection): SwingPoint {
  return shiftPoint(
    getArrowPoint(scale, rectangle, direction),
    direction,
    ACTION_ARROW_PARALLEL * scale - SwingLength(1f))
}

/**
 * Gets a point somewhere on the given action, or null if there was a problem.
 */
fun getAnyPoint(action: SceneComponent, context: SceneContext): SwingPoint? {
  val scene = action.scene
  val rootNlComponent = scene.root?.nlComponent ?: return null
  val actionNlComponent = action.nlComponent
  val sourceNlComponent = actionNlComponent.getEffectiveSource(rootNlComponent) ?: return null
  val sourceSceneComponent = scene.getSceneComponent(sourceNlComponent) ?: return null
  val sourceRect = sourceSceneComponent.inlineDrawRect(context)

  when (actionNlComponent.getActionType(rootNlComponent)) {
    ActionType.SELF -> {
      val points = selfActionPoints(sourceRect, context.inlineScale)
      return SwingPoint(points[1].x, (points[1].y + (points[2].y - points[1].y) / 2))
    }
    ActionType.REGULAR, ActionType.EXIT_DESTINATION -> {
      val targetNlComponent = actionNlComponent.effectiveDestination ?: return null
      val destinationSceneComponent = scene.getSceneComponent(targetNlComponent) ?: return null
      val destRect = destinationSceneComponent.inlineDrawRect(context)
      val curvePoints = getCurvePoints(sourceRect, destRect, context.inlineScale)
      return curvePoints.curvePoint(0.5f)
    }
    ActionType.EXIT, ActionType.GLOBAL ->
      return action.inlineDrawRect(context).center
    else -> return null
  }
}

fun getArrowPoint(scale: Scale, rectangle: SwingRectangle, direction: ConnectionDirection): SwingPoint {
  var shiftY = ACTION_PADDING
  if (direction === ConnectionDirection.TOP) {
    shiftY += HEADER_HEIGHT
  }
  return shiftPoint(getConnectionPoint(rectangle, direction),
                    direction, shiftY * scale)
}

/**
 * Returns the drawing rectangle for the pop icon for a regular action
 */
fun getRegularActionIconRect(source: SwingRectangle, dest: SwingRectangle, scale: Scale): SwingRectangle {
  val startPoint = getStartPoint(source)
  val points = getCurvePoints(source, dest, scale)

  var t = 0f
  var previous = SwingPoint(SwingX(0f), SwingY(0f))
  var current = points.curvePoint(t)
  val range = POP_ICON_RANGE * scale
  val separation = (POP_ICON_RADIUS + POP_ICON_DISTANCE) * scale

  // Search for the best point to attach the pop icon to.
  // Four conditions are:
  //   Don't go past the end of the curve.
  //   Don't go farther away from the starting point than POP_ICON_RANGE
  //   Don't stop while the source would obscure the pop icon (if possible)
  //   Don't go more than STEP_THRESHOLD away from the starting point
  while (t < 1
         && distance(current, startPoint) < range
         && (current.x - startPoint.x < separation || t < STEP_THRESHOLD)) {
    t += STEP_SIZE
    previous = current
    current = points.curvePoint(t)
  }

  val dx = current.x - previous.x
  val dy = current.y - previous.y
  val ds = distance(previous, current)

  var deltaX = dy * (separation / ds)
  var deltaY = -dx * (separation / ds)

  // Choose the counterclockwise normal to the tangent vector unless dx and dy are both negative
  if (dx.value < 0 && dy.value < 0) {
    deltaX *= -1
    deltaY *= -1
  }

  val radius = POP_ICON_RADIUS * scale
  return SwingRectangle(current.x + deltaX - radius, current.y + deltaY - radius, 2 * radius, 2 * radius)
}

/**
 * Returns the drawing rectangle for the pop icon for a self action
 */
fun getSelfActionIconRect(start: SwingPoint, scale: Scale): SwingRectangle {
  val x = start.x + (SELF_ACTION_LENGTHS[0] + POP_ICON_DISTANCE) * scale
  val y = start.y + POP_ICON_Y_OFFSET * scale
  val radius = POP_ICON_RADIUS * scale

  return SwingRectangle(x, y, radius * 2, radius * 2)
}

/**
 * Returns the drawing rectangle for the pop icon for a horizontal action (i.e. global or exit)
 */
fun getHorizontalActionIconRect(rectangle: SwingRectangle, scale: Scale): SwingRectangle {
  val x = rectangle.x + POP_ICON_HORIZONTAL_PADDING * scale
  val size = 2 * POP_ICON_RADIUS * scale
  val y = rectangle.y + rectangle.height / 2 - size - POP_ICON_VERTICAL_PADDING * scale

  return SwingRectangle(x, y, size, size)
}

private fun shiftPoint(p: SwingPoint, direction: ConnectionDirection, shift: SwingLength): SwingPoint {
  return shiftPoint(p, direction, shift, shift)
}

private fun shiftPoint(p: SwingPoint, direction: ConnectionDirection, shiftX: SwingLength, shiftY: SwingLength): SwingPoint {
  return SwingPoint(p.x + shiftX * direction.deltaX, p.y + shiftY * direction.deltaY)
}

