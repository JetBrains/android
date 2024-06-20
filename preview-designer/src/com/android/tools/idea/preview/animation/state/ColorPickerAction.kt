/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.idea.preview.animation.state

import com.android.tools.adtui.actions.componentToRestoreFocusTo
import com.android.tools.idea.preview.animation.AnimationTracker
import com.android.tools.idea.preview.animation.InspectorLayout
import com.android.tools.idea.ui.resourcechooser.util.createAndShowColorPickerPopup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.JComponent
import kotlinx.coroutines.flow.MutableStateFlow

interface ColorPicker {
  fun show(initialColor: Color, restoreFocusComponent: Component?, onColorPicked: (Color) -> Unit)
}

// Real implementation using createAndShowColorPickerPopup
private object ColorPickerImpl : ColorPicker {

  override fun show(
    initialColor: Color,
    restoreFocusComponent: Component?,
    onColorPicked: (Color) -> Unit,
  ) {
    createAndShowColorPickerPopup(
      initialColor,
      initialColorResource = null,
      configuration = null,
      resourcePickerSources = listOf(),
      restoreFocusComponent,
      locationToShow = null,
      colorPickedCallback = onColorPicked,
      colorResourcePickedCallback = {},
    )
  }
}

/** [AnAction] displaying the color state. It opens a color picker to select it. */
class ColorPickerAction(
  val tracker: AnimationTracker,
  private val flow: MutableStateFlow<Color>,
  private val colorPicker: ColorPicker = ColorPickerImpl,
) : CustomComponentAction, AnAction() {

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {

    return object :
      ActionButton(
        this,
        PresentationFactory().getPresentation(this),
        ActionPlaces.TOOLBAR,
        ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE,
      ) {

      override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        g.color = flow.value
        g.fillRect(
          InspectorLayout.colorButtonOffset,
          InspectorLayout.colorButtonOffset,
          width - 2 * InspectorLayout.colorButtonOffset,
          height - 2 * InspectorLayout.colorButtonOffset,
        )
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    colorPicker.show(flow.value, e.componentToRestoreFocusTo()) { pickedColor ->
      flow.value = pickedColor
      tracker.openPicker()
    }
  }

  fun swapWith(other: ColorPickerAction) {
    val saveState = flow.value
    flow.value = other.flow.value
    other.flow.value = saveState
  }
}
