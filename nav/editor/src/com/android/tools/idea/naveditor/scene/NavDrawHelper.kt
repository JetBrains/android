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

import com.android.tools.adtui.common.SwingEllipse
import com.android.tools.adtui.common.SwingFont
import com.android.tools.adtui.common.SwingLength
import com.android.tools.adtui.common.SwingPoint
import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.adtui.common.SwingStroke
import com.android.tools.adtui.common.scaledSwingLength
import com.android.tools.adtui.common.times
import com.android.tools.idea.common.model.Scale
import com.android.tools.idea.common.model.scaledAndroidLength
import com.android.tools.idea.common.model.times
import com.android.tools.idea.common.scene.LerpEllipse
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.inlineScale
import com.android.tools.idea.naveditor.scene.draw.DrawNavScreen
import com.android.tools.idea.naveditor.scene.draw.DrawPlaceholder
import com.google.common.annotations.VisibleForTesting
import kotlin.math.min

@VisibleForTesting
const val DEFAULT_FONT_NAME = "Default"
private val DEFAULT_FONT_SIZE = scaledAndroidLength(12f)

val INNER_RADIUS_SMALL = scaledAndroidLength(5f)
val INNER_RADIUS_LARGE = scaledAndroidLength(8f)
val OUTER_RADIUS_SMALL = scaledAndroidLength(7f)
val OUTER_RADIUS_LARGE = scaledAndroidLength(11f)

val HANDLE_STROKE = SwingStroke(scaledSwingLength(2f))

val FRAGMENT_BORDER_SPACING = scaledAndroidLength(2f)
val ACTION_HANDLE_OFFSET = FRAGMENT_BORDER_SPACING + scaledAndroidLength(2f)

val HEADER_ICON_SIZE = scaledAndroidLength(14f)
val HEADER_TEXT_PADDING = scaledAndroidLength(2f)
val HEADER_PADDING = scaledAndroidLength(8f)

val HEADER_HEIGHT = HEADER_ICON_SIZE + HEADER_PADDING
val HEADER_TEXT_HEIGHT = HEADER_ICON_SIZE - 2 * HEADER_TEXT_PADDING

val ACTION_ARROW_PARALLEL = scaledAndroidLength(10f)
val ACTION_ARROW_PERPENDICULAR = scaledAndroidLength(12f)

fun regularFont(scale: Scale, style: Int): SwingFont {
  return SwingFont(DEFAULT_FONT_NAME, style, scale * DEFAULT_FONT_SIZE)
}

fun scaledFont(scale: Scale, style: Int): SwingFont {
  val newScale = scale.value.let { Scale(it * (2.0 - min(it, 1.0))) }  // keep font size slightly larger at smaller scales
  return regularFont(newScale, style)
}

fun createDrawImageCommand(rectangle: SwingRectangle, image: RefinableImage?): DrawCommand {
  return if (image == null) {
    DrawPlaceholder(rectangle)
  }
  else {
    DrawNavScreen(rectangle, image)
  }
}

fun makeCircle(center: SwingPoint, radius: SwingLength): SwingEllipse {
  val x = center.x - radius
  val y = center.y - radius
  return SwingEllipse(x, y, 2 * radius, 2 * radius)
}

fun makeCircleLerp(center: SwingPoint, initialRadius: SwingLength, finalRadius: SwingLength, duration: Int): LerpEllipse {
  val initialCircle = makeCircle(center, initialRadius)
  val finalCircle = makeCircle(center, finalRadius)
  return LerpEllipse(initialCircle, finalCircle, duration)
}

fun getHeaderRect(context: SceneContext, rectangle: SwingRectangle): SwingRectangle {
  val height = context.inlineScale * HEADER_HEIGHT
  return SwingRectangle(rectangle.x, rectangle.y - height, rectangle.width, height)
}

enum class ArrowDirection {
  LEFT,
  UP,
  RIGHT,
  DOWN
}
