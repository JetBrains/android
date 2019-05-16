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

import com.google.common.annotations.VisibleForTesting
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.HQ_RENDERING_HINTS
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.scene.draw.DrawNavScreen
import com.android.tools.idea.naveditor.scene.draw.DrawPlaceholder
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

@VisibleForTesting
const val DEFAULT_FONT_NAME = "Default"
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
val HANDLE_STROKE = BasicStroke(JBUI.scale(2).toFloat())

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

fun regularFont(scale: Float, style: Int): Font {
  val size = scale * DEFAULT_FONT_SIZE
  return Font(DEFAULT_FONT_NAME, style, size.toInt())
}

fun scaledFont(context: SceneContext, style: Int): Font {
  val scale = context.scale
  val size = (scale * (2.0 - Math.min(scale, 1.0))) * DEFAULT_FONT_SIZE // keep font size slightly larger at smaller scales

  return Font(DEFAULT_FONT_NAME, style, size.toInt())
}

fun setRenderingHints(g: Graphics2D) {
  g.setRenderingHints(HQ_RENDERING_HINTS)
}

fun convertToRoundRect(view: SceneView, @SwingCoordinate rectangle: Rectangle2D.Float, @NavCoordinate arcSize: Float)
  : RoundRectangle2D.Float {
  val roundRect = RoundRectangle2D.Float(rectangle.x, rectangle.y, rectangle.width, rectangle.height, 0f, 0f)

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

fun createDrawImageCommand(rectangle: Rectangle2D.Float, image: RefinableImage?): DrawCommand {
  return if (image == null) {
    DrawPlaceholder(DRAW_NAV_SCREEN_LEVEL, rectangle)
  }
  else {
    DrawNavScreen(rectangle, image)
  }
}
