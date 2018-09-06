/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.HQ_RENDERING_HINTS
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.scene.targets.ActionHandleTarget
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.BasicStroke.CAP_BUTT
import java.awt.BasicStroke.JOIN_ROUND
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

private const val DEFAULT_FONT_NAME = "Default"
private val DEFAULT_FONT_SIZE = JBUI.scale(12)

const val DRAW_BACKGROUND_LEVEL = 0
const val DRAW_FRAME_LEVEL = DRAW_BACKGROUND_LEVEL + 1
const val DRAW_ACTION_LEVEL = DRAW_FRAME_LEVEL + 1
const val DRAW_SCREEN_LABEL_LEVEL = DRAW_ACTION_LEVEL + 1
const val DRAW_ICON_LEVEL = DRAW_SCREEN_LABEL_LEVEL + 1
const val DRAW_NAV_SCREEN_LEVEL = DRAW_ICON_LEVEL + 1
const val DRAW_ACTIVITY_BORDER_LEVEL = DRAW_ICON_LEVEL + 1
const val DRAW_ACTION_HANDLE_BACKGROUND_LEVEL = DRAW_ACTIVITY_BORDER_LEVEL + 1
const val DRAW_ACTION_HANDLE_LEVEL = DRAW_ACTION_HANDLE_BACKGROUND_LEVEL + 1
const val DRAW_ACTION_HANDLE_DRAG_LEVEL = DRAW_ACTION_HANDLE_LEVEL + 1

@SwingCoordinate
private val ACTION_STROKE_WIDTH = JBUI.scale(3f)
@SwingCoordinate
private val DASHED_STROKE_CYCLE = JBUI.scale(5f)

@JvmField
@NavCoordinate
val INNER_RADIUS_SMALL = JBUI.scale(5f)
@JvmField
@NavCoordinate
val INNER_RADIUS_LARGE = JBUI.scale(8f)
@JvmField
@NavCoordinate
val OUTER_RADIUS_SMALL = JBUI.scale(7f)
@JvmField
@NavCoordinate
val OUTER_RADIUS_LARGE = JBUI.scale(11f)

@SwingCoordinate
val REGULAR_FRAME_THICKNESS = JBUI.scale(1f)
@SwingCoordinate
val HIGHLIGHTED_FRAME_THICKNESS = JBUI.scale(2f)

@JvmField
@NavCoordinate
val SELF_ACTION_LENGTHS = intArrayOf(JBUI.scale(28), JBUI.scale(26), JBUI.scale(60), JBUI.scale(8))
val SELF_ACTION_RADII = floatArrayOf(JBUI.scale(10f), JBUI.scale(10f), JBUI.scale(5f))

@JvmField
val ACTION_STROKE = BasicStroke(ACTION_STROKE_WIDTH, CAP_BUTT, JOIN_ROUND)
@JvmField
val DASHED_ACTION_STROKE = BasicStroke(ACTION_STROKE_WIDTH, CAP_BUTT, JOIN_ROUND, DASHED_STROKE_CYCLE,
                                       floatArrayOf(DASHED_STROKE_CYCLE), DASHED_STROKE_CYCLE)

@JvmField
@NavCoordinate
val FRAGMENT_BORDER_SPACING = JBUI.scale(2f)
@JvmField
@NavCoordinate
val ACTION_HANDLE_OFFSET = FRAGMENT_BORDER_SPACING.toInt() + JBUI.scale(2)

@NavCoordinate
val HEADER_ICON_SIZE = JBUI.scale(14f)
@NavCoordinate
val HEADER_TEXT_PADDING = JBUI.scale(2f)
@NavCoordinate
val HEADER_PADDING = JBUI.scale(8f)

@JvmField
@NavCoordinate
val HEADER_HEIGHT = HEADER_ICON_SIZE + HEADER_PADDING
@NavCoordinate
val HEADER_TEXT_HEIGHT = HEADER_ICON_SIZE - 2 * HEADER_TEXT_PADDING

fun frameColor(context: SceneContext, component: SceneComponent): Color {
  val colorSet = context.colorSet

  return when (component.drawState) {
    SceneComponent.DrawState.SELECTED -> colorSet.selectedFrames
    SceneComponent.DrawState.HOVER ->
      if (ActionHandleTarget.isDragCreateInProgress(component.nlComponent) && !component.id.isNullOrEmpty()) colorSet.selectedFrames
      else colorSet.highlightedFrames
    SceneComponent.DrawState.DRAG -> colorSet.highlightedFrames
    else -> colorSet.frames
  }
}

fun textColor(context: SceneContext, component: SceneComponent): Color {
  val colorSet = context.colorSet

  return if (component.isSelected) {
    colorSet.selectedText
  }
  else {
    colorSet.text
  }
}

fun actionColor(context: SceneContext, component: SceneComponent): Color {
  val colorSet = context.colorSet as NavColorSet

  return when {
    component.isSelected -> colorSet.selectedActions
    component.drawState == SceneComponent.DrawState.HOVER -> colorSet.highlightedActions
    else -> colorSet.actions
  }
}

fun scaledFont(context: SceneContext, style: Int): Font {
  val scale = context.scale
  val size = (scale * (2.0 - Math.min(scale, 1.0))) * DEFAULT_FONT_SIZE // keep font size slightly larger at smaller scales

  return Font(DEFAULT_FONT_NAME, style, size.toInt())
}

fun createDrawCommand(list: DisplayList, component: SceneComponent): DrawCommand {
  var level = DrawCommand.COMPONENT_LEVEL

  if (component.isDragging) {
    level = DrawCommand.TOP_LEVEL
  }
  else if (component.flatten().anyMatch { it.isSelected }) {
    level = DrawCommand.COMPONENT_SELECTED_LEVEL
  }

  return list.getCommand(level)
}

fun setRenderingHints(g: Graphics2D) {
  g.setRenderingHints(HQ_RENDERING_HINTS)
}

fun frameThickness(component: SceneComponent): Float {
  return if (isHighlighted(component)) HIGHLIGHTED_FRAME_THICKNESS else REGULAR_FRAME_THICKNESS
}

fun isHighlighted(component: SceneComponent): Boolean {
  return when (component.drawState) {
    SceneComponent.DrawState.SELECTED, SceneComponent.DrawState.HOVER, SceneComponent.DrawState.DRAG -> true
    else -> false
  }
}

/**
 * Returns an array of five points representing the path of the self action
 * start: middle of right side of component
 * 1: previous point offset 28 to the left
 * 2: previous point offset to 26 below the bottom of component
 * 3: previous point shifted 60 to the right
 * end: previous point shifted up 8
 */
fun selfActionPoints(@SwingCoordinate start: Point2D.Float, @SwingCoordinate end: Point2D.Float, context: SceneContext): Array<Point2D.Float> {
  val p1 = Point2D.Float(start.x + context.getSwingDimension(SELF_ACTION_LENGTHS[0]), start.y)
  val p2 = Point2D.Float(p1.x, end.y + context.getSwingDimension(SELF_ACTION_LENGTHS[3]))
  val p3 = Point2D.Float(end.x, p2.y)
  return arrayOf(start, p1, p2, p3, end)
}

fun convertToRoundRect(view: SceneView, @SwingCoordinate rectangle: Rectangle2D.Float, @NavCoordinate arcSize: Float)
  : RoundRectangle2D.Float {
  var roundRect = RoundRectangle2D.Float(rectangle.x, rectangle.y, rectangle.width, rectangle.height, 0f, 0f)

  Coordinates.getSwingDimension(view, arcSize).let {
    roundRect.arcwidth = it
    roundRect.archeight = it
  }

  return roundRect
}

fun growRectangle(rectangle: Rectangle2D.Float, growX: Float, growY: Float) {
  rectangle.x -= growX
  rectangle.y -= growY
  rectangle.width += 2 * growX
  rectangle.height += 2 * growY
}

fun growRectangle(rectangle: RoundRectangle2D.Float, growX: Float, growY: Float) {
  rectangle.x -= growX
  rectangle.y -= growY
  rectangle.width += 2 * growX
  rectangle.height += 2 * growY
}

