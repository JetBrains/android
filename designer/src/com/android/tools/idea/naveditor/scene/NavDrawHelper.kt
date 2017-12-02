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
import com.android.tools.idea.common.model.AndroidCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawCommand
import java.awt.Color
import java.awt.Font

private const val DEFAULT_FONT_NAME = "Default"
private const val DEFAULT_FONT_SIZE = 12

const val DRAW_BACKGROUND_LEVEL = 0
const val DRAW_FRAME_LEVEL = DRAW_BACKGROUND_LEVEL + 1
const val DRAW_ACTION_LEVEL = DRAW_FRAME_LEVEL + 1
const val DRAW_SCREEN_LABEL_LEVEL = DRAW_ACTION_LEVEL + 1
const val DRAW_ICON_LEVEL = DRAW_SCREEN_LABEL_LEVEL + 1
const val DRAW_NAV_SCREEN_LEVEL = DRAW_ICON_LEVEL + 1
const val DRAW_ACTION_HANDLE_BACKGROUND_LEVEL = DRAW_NAV_SCREEN_LEVEL + 1
const val DRAW_ACTION_HANDLE_LEVEL = DRAW_ACTION_HANDLE_BACKGROUND_LEVEL + 1
const val DRAW_ACTION_HANDLE_DRAG_LEVEL = DRAW_ACTION_HANDLE_LEVEL + 1


fun frameColor(context: SceneContext, component: SceneComponent): Color {
  val colorSet = context.colorSet

  return when (component.drawState) {
    SceneComponent.DrawState.SELECTED -> colorSet.selectedFrames
    SceneComponent.DrawState.HOVER, SceneComponent.DrawState.DRAG -> colorSet.highlightedFrames
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

@SwingCoordinate
fun strokeThickness(context: SceneContext, component: SceneComponent, @AndroidCoordinate borderThickness: Int): Int {
  return when (component.drawState) {
    SceneComponent.DrawState.SELECTED, SceneComponent.DrawState.HOVER, SceneComponent.DrawState.DRAG
    -> Coordinates.getSwingDimension(context, borderThickness)
    else -> 1
  }
}

fun scaledFont(context: SceneContext, style: Int): Font {
  val scale = context.scale
  val size = (scale * (2.0 - scale)) * DEFAULT_FONT_SIZE // keep font size slightly larger at smaller scales

  return Font(DEFAULT_FONT_NAME, style, size.toInt())
}

fun createDrawCommand(list: DisplayList, component: SceneComponent): DrawCommand {
  var level = DrawCommand.COMPONENT_LEVEL

  if (component.isDragging) {
    level = DrawCommand.TOP_LEVEL
  }
  else if (component.isSelected) {
    level = DrawCommand.COMPONENT_SELECTED_LEVEL
  }

  return list.getCommand(level)
}
