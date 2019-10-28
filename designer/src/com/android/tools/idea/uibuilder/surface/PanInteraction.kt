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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.adtui.ui.AdtUiCursors
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.Interaction
import com.android.tools.idea.common.surface.InteractionInformation
import java.awt.Cursor
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.util.EventObject

class PanInteraction(private val surface: DesignSurface): Interaction() {

  private var isGrabbing = false

  override fun begin(event: EventObject?, interactionInformation: InteractionInformation) {
    begin(interactionInformation.x, interactionInformation.y, interactionInformation.modifiersEx)
    if (event is MouseEvent) {
      isGrabbing = event.modifiersEx and (InputEvent.BUTTON1_DOWN_MASK or InputEvent.BUTTON2_DOWN_MASK) > 0
    }
  }

  override fun update(event: EventObject, interactionInformation: InteractionInformation) {
    if (event is MouseEvent) {
      if (event.modifiersEx and (InputEvent.BUTTON1_DOWN_MASK or InputEvent.BUTTON2_DOWN_MASK) > 0) {
        // left or middle mouse is pressing.
        isGrabbing = true
        handlePanInteraction(surface, event.x, event.y, interactionInformation)
      }
      else {
        isGrabbing = false;
      }
    }
  }

  override fun commit(event: EventObject?, interactionInformation: InteractionInformation) {
    end(interactionInformation.x, interactionInformation.y, interactionInformation.modifiersEx)
  }

  override fun cancel(event: EventObject?, interactionInformation: InteractionInformation) {
    cancel(interactionInformation.x, interactionInformation.y, interactionInformation.modifiersEx)
  }

  override fun getCursor(): Cursor? = if (isGrabbing) AdtUiCursors.GRABBING else AdtUiCursors.GRAB

  companion object {

    /**
     * Scroll the [DesignSurface] by the same amount as the drag distance.
     *
     * @param x     x position of the cursor for the passed event
     * @param y     y position of the cursor for the passed event
     *
     * TODO: Inline this into [update] after [StudioFlags#NELE_NEW_INTERACTION_INTERFACE] is removed.
     */
    @JvmStatic
    fun handlePanInteraction(surface: DesignSurface,
                             @SwingCoordinate x: Int,
                             @SwingCoordinate y: Int,
                             interactionInformation: InteractionInformation) {
      val position = surface.scrollPosition
      // position can be null in tests
      if (position != null) {
        position.translate(interactionInformation.x - x, interactionInformation.y - y)
        surface.scrollPosition = position
      }
    }
  }
}
