/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface.interaction

import com.android.tools.adtui.Pannable
import com.android.tools.adtui.common.AdtUiCursorType
import com.android.tools.adtui.common.AdtUiCursorsProvider
import com.android.tools.idea.common.surface.Interaction
import com.android.tools.idea.common.surface.InteractionEvent
import com.android.tools.idea.common.surface.InteractionInputEvent
import com.android.tools.idea.common.surface.InteractionNonInputEvent
import com.android.tools.idea.common.surface.MouseDraggedEvent
import com.android.tools.idea.common.surface.MouseMovedEvent
import com.android.tools.idea.common.surface.MousePressedEvent
import com.android.tools.idea.common.surface.MouseReleasedEvent
import java.awt.Cursor
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent

class PanInteraction(private val pannable: Pannable) : Interaction() {

  private var isGrabbing = false
  private var pannableOriginalPosition: Point? = Point()
  private val startPoint = Point()

  override fun begin(event: InteractionEvent) {
    begin(event.info.x, event.info.y, event.info.modifiersEx)
    if (event is MousePressedEvent) {
      setupOriginalPoint(event)
      isGrabbing =
        event.info.modifiersEx and (InputEvent.BUTTON1_DOWN_MASK or InputEvent.BUTTON2_DOWN_MASK) >
          0
    }
  }

  /** Setup original position as the start point of scrolling. */
  private fun <T : MouseEvent> setupOriginalPoint(event: InteractionInputEvent<T>) {
    val mouseEvent = event.eventObject
    pannableOriginalPosition = pannable.scrollPosition.location
    startPoint.setLocation(mouseEvent.xOnScreen, mouseEvent.yOnScreen)
  }

  override fun update(event: InteractionEvent) {
    when (event) {
      is MousePressedEvent -> {
        if (
          event.info.modifiersEx and
            (InputEvent.BUTTON1_DOWN_MASK or InputEvent.BUTTON2_DOWN_MASK) > 0
        ) {
          setupOriginalPoint(event)
          isGrabbing = true
        }
      }
      // Note: below 3 conditions cannot be merged. Kotlin treats the union type is
      // InteractionEvent, not InteractionInputEvent<MouseEvent>
      is MouseMovedEvent -> updateMouseScrollEvent(event)
      is MouseDraggedEvent -> updateMouseScrollEvent(event)
      is MouseReleasedEvent -> updateMouseScrollEvent(event)
      is InteractionInputEvent<*>,
      is InteractionNonInputEvent -> {}
    }
  }

  /** Scroll by the given Swing [MouseEvent]. */
  private fun <T : MouseEvent> updateMouseScrollEvent(event: InteractionInputEvent<T>) {
    val mouseEvent = event.eventObject
    if (
      mouseEvent.modifiersEx and (InputEvent.BUTTON1_DOWN_MASK or InputEvent.BUTTON2_DOWN_MASK) > 0
    ) {
      // left or middle mouse is pressing.
      isGrabbing = true
      // surface original position can be null in tests
      val newPosition = pannableOriginalPosition?.let { Point(it) } ?: return
      val screenX = mouseEvent.xOnScreen
      val screenY = mouseEvent.yOnScreen
      newPosition.translate(startPoint.x - screenX, startPoint.y - screenY)
      pannable.scrollPosition = newPosition
    } else {
      isGrabbing = false
    }
  }

  override fun commit(event: InteractionEvent) {}

  override fun cancel(event: InteractionEvent) {}

  override fun getCursor(): Cursor? =
    AdtUiCursorsProvider.getInstance()
      .getCursor(if (isGrabbing) AdtUiCursorType.GRABBING else AdtUiCursorType.GRAB)
}
