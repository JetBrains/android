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
package com.android.tools.idea.compose.preview.animation.state

import com.android.tools.adtui.actions.locationFromEvent
import com.android.tools.idea.compose.pickers.PsiPickerManager
import com.android.tools.idea.compose.preview.animation.ComposeAnimationTracker
import com.android.tools.idea.compose.preview.animation.ComposeUnit
import com.android.tools.idea.compose.preview.animation.picker.AnimatedPropertiesModel
import com.android.tools.idea.preview.animation.AnimationUnit
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.ui.popup.Balloon
import java.awt.Component
import javax.swing.JButton
import javax.swing.JComponent
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A button displaying the "initial to target" state. It opens a picker to select these states.
 *
 * @see [AnimatedPropertiesModel] for more details about the picker.
 */
class PickerButtonAction(val tracker: ComposeAnimationTracker) : CustomComponentAction, AnAction() {

  val state: MutableStateFlow<Pair<AnimationUnit.Unit<*>, AnimationUnit.Unit<*>>> =
    MutableStateFlow(AnimationUnit.UnitUnknown(null) to AnimationUnit.UnitUnknown(null))

  private val stateListeners: MutableList<() -> Unit> = mutableListOf()

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object : JButton(stateText, AllIcons.Actions.Edit) {
      init {
        addActionListener {
          val ctx = DataManager.getInstance().getDataContext(it.source as Component)
          val event =
            AnActionEvent.createFromAnAction(
              this@PickerButtonAction,
              null,
              ActionPlaces.TOOLBAR,
              ctx,
            )
          ActionUtil.performDumbAwareWithCallbacks(this@PickerButtonAction, event) {
            this@PickerButtonAction.actionPerformed(event)
          }
        }
        stateListeners.add { text = stateText }
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    PsiPickerManager.show(
      location = e.locationFromEvent(),
      displayTitle = state.value.first.getPickerTitle(),
      balloonPosition = Balloon.Position.above,
      model =
        AnimatedPropertiesModel(state.value.first, state.value.second) { initial, target ->
          updateState(initial, target)
        },
    )
    tracker.openPicker()
  }

  private val stateText: String
    get() = "${state.value.first} to ${state.value.second}"

  fun swapStates() {
    state.value = state.value.second to state.value.first
  }

  fun updateInitialState(initial: Any?) {
    updateState(ComposeUnit.parseStateUnit(initial), state.value.second)
  }

  fun updateTargetState(target: Any?) {
    updateState(state.value.first, ComposeUnit.parseStateUnit(target))
  }

  // Private helper function to update the state and notify observers
  private fun updateState(initial: AnimationUnit.Unit<*>, target: AnimationUnit.Unit<*>) {
    state.value = initial to target
    stateListeners.forEach { it() } // Notify listeners (needed for backward compatibility)
  }
}
