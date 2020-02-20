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

import java.awt.Cursor
import java.awt.dnd.DropTargetDragEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.util.EnumMap

/**
 * [InteractionHandler] that allows to dynamically switch between different aggregated [InteractionHandler] implementations, therefore
 * providing dynamic behavior without a need to reassign/recreate [InteractionHandler].
 */
class SwitchingInteractionHandler<T : Enum<T>>(private val handlers: EnumMap<T, InteractionHandler>, default: T) : InteractionHandler  {
  var selected = default
    set(value) {
      if (handlers[value] == null) {
        throw IllegalArgumentException("No handler $value found among delegated handlers.")
      }
      field = value
    }

  init {
    if (handlers[default] == null) {
      throw IllegalArgumentException("No default handler ($default) found among delegated handlers.")
    }
  }

  override fun createInteractionOnPressed(mouseX: Int, mouseY: Int, modifiersEx: Int): Interaction? =
    handlers[selected]!!.createInteractionOnPressed(mouseX, mouseY, modifiersEx)

  override fun createInteractionOnDrag(mouseX: Int, mouseY: Int, modifiersEx: Int): Interaction? =
    handlers[selected]!!.createInteractionOnDrag(mouseX, mouseY, modifiersEx)

  override fun createInteractionOnDragEnter(dragEvent: DropTargetDragEvent): Interaction? =
    handlers[selected]!!.createInteractionOnDragEnter(dragEvent)

  override fun createInteractionOnMouseWheelMoved(mouseWheelEvent: MouseWheelEvent): Interaction? =
    handlers[selected]!!.createInteractionOnMouseWheelMoved(mouseWheelEvent)

  override fun mouseReleaseWhenNoInteraction(x: Int, y: Int, modifiersEx: Int) =
    handlers[selected]!!.mouseReleaseWhenNoInteraction(x, y, modifiersEx)

  override fun singleClick(x: Int, y: Int, modifiersEx: Int) = handlers[selected]!!.singleClick(x, y, modifiersEx)

  override fun doubleClick(x: Int, y: Int, modifiersEx: Int) = handlers[selected]!!.doubleClick(x, y, modifiersEx)

  override fun hoverWhenNoInteraction(mouseX: Int, mouseY: Int, modifiersEx: Int) =
    handlers[selected]!!.hoverWhenNoInteraction(mouseX, mouseY, modifiersEx)

  override fun popupMenuTrigger(mouseEvent: MouseEvent) = handlers[selected]!!.popupMenuTrigger(mouseEvent)

  override fun getCursorWhenNoInteraction(mouseX: Int, mouseY: Int, modifiersEx: Int): Cursor? =
    handlers[selected]!!.getCursorWhenNoInteraction(mouseX, mouseY, modifiersEx)

  override fun keyPressedWithoutInteraction(keyEvent: KeyEvent) = handlers[selected]!!.keyPressedWithoutInteraction(keyEvent)

  override fun keyReleasedWithoutInteraction(keyEvent: KeyEvent) = handlers[selected]!!.keyReleasedWithoutInteraction(keyEvent)
}