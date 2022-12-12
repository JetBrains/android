/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation.actions

import com.android.tools.adtui.actions.componentToRestoreFocusTo
import com.android.tools.adtui.actions.locationFromEvent
import com.android.tools.idea.compose.preview.animation.ComposeAnimationEventTracker
import com.android.tools.idea.compose.preview.animation.ComposeUnit
import com.android.tools.idea.compose.preview.animation.InspectorLayout.colorButtonOffset
import com.android.tools.idea.ui.resourcechooser.util.createAndShowColorPickerPopup
import com.google.wireless.android.sdk.stats.ComposeAnimationToolingEvent
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import java.awt.Color
import java.awt.Graphics
import javax.swing.JComponent

private val DEFAULT_COLOR: Color = Color.white

/** [AnAction] displaying the color state. It opens a color picker to select it. */
class ColorStateAction(
  defaultState: ComposeUnit.Color = ComposeUnit.Color.create(DEFAULT_COLOR),
  val tracker: ComposeAnimationEventTracker,
  private val onPropertiesUpdated: () -> Unit
) : CustomComponentAction, AnAction() {

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {

    return object :
      ActionButton(
        this,
        PresentationFactory().getPresentation(this),
        ActionPlaces.TOOLBAR,
        ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
      ) {

      override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        g.color = state.color ?: DEFAULT_COLOR
        g.fillRect(
          colorButtonOffset,
          colorButtonOffset,
          width - 2 * colorButtonOffset,
          height - 2 * colorButtonOffset
        )
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    createAndShowColorPickerPopup(
      initialColor = state.color ?: DEFAULT_COLOR,
      initialColorResource = null,
      configuration = null,
      resourcePickerSources = listOf(),
      restoreFocusComponent = e.componentToRestoreFocusTo(),
      locationToShow = e.locationFromEvent(),
      colorPickedCallback = {
        state = ComposeUnit.Color.create(it)
        onPropertiesUpdated()
      },
      colorResourcePickedCallback = {}
    )
    tracker(ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.OPEN_PICKER)
  }

  var state: ComposeUnit.Color = defaultState

  fun getStateAsComponents(): Any {
    return state.components.toList()
  }

  fun stateHashCode(): Int {
    return state.hashCode()
  }

  fun swapWith(other: ColorStateAction) {
    val saveState = state
    state = other.state
    other.state = saveState
  }
}
