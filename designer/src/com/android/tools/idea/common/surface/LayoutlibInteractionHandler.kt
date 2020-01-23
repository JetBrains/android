/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.common.surface

import com.android.tools.idea.uibuilder.surface.ScreenView
import java.awt.Cursor
import java.awt.dnd.DropTargetDragEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent

/**
 * [InteractionHandler] used during interactive mode in the layout/compose previews.
 */
class LayoutlibInteractionHandler(private val surface: DesignSurface) : InteractionHandler {
  override fun createInteractionOnPressed(mouseX: Int, mouseY: Int, modifiersEx: Int): Interaction? {
    val view = surface.getSceneView(mouseX, mouseY) ?: return null
    val screenView = view as ScreenView
    return LayoutlibInteraction(screenView)
  }

  override fun createInteractionOnDrag(mouseX: Int, mouseY: Int, modifiersEx: Int): Interaction? = null

  override fun createInteractionOnDragEnter(dragEvent: DropTargetDragEvent): Interaction? = null

  override fun createInteractionOnMouseWheelMoved(mouseWheelEvent: MouseWheelEvent): Interaction? = null

  override fun mouseReleaseWhenNoInteraction(x: Int, y: Int, modifiersEx: Int) { }

  override fun singleClick(x: Int, y: Int, modifiersEx: Int) { }

  override fun doubleClick(x: Int, y: Int, modifiersEx: Int) { }

  override fun hoverWhenNoInteraction(mouseX: Int, mouseY: Int, modifiersEx: Int) { }

  override fun popupMenuTrigger(mouseEvent: MouseEvent) { }

  override fun getCursorWhenNoInteraction(mouseX: Int, mouseY: Int, modifiersEx: Int): Cursor? = surface.scene?.mouseCursor
}