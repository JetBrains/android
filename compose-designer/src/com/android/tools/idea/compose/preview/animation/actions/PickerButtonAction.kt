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

import com.android.tools.adtui.actions.locationFromEvent
import com.android.tools.idea.compose.pickers.PsiPickerManager
import com.android.tools.idea.compose.preview.animation.ComposeAnimationEventTracker
import com.android.tools.idea.compose.preview.animation.ComposeUnit
import com.android.tools.idea.compose.preview.animation.picker.AnimatedPropertiesModel
import com.google.wireless.android.sdk.stats.ComposeAnimationToolingEvent
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

/**
 * A button displaying the "initial to target" state. It opens a picker to select these states.
 * @see [AnimatedPropertiesModel] for more details about the picker.
 */
class PickerButtonAction(
  val tracker: ComposeAnimationEventTracker,
  private val onClick: () -> Unit
) : CustomComponentAction, AnAction() {

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
              ctx
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
      displayTitle = initialState.getPickerTitle(),
      balloonPosition = Balloon.Position.above,
      model =
        AnimatedPropertiesModel(initialState, targetState) { initial, target ->
          setUnitStates(initial, target)
          onClick()
        }
    )
    tracker(ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.OPEN_PICKER)
  }

  private lateinit var initialState: ComposeUnit.Unit<*>
  private lateinit var targetState: ComposeUnit.Unit<*>
  private val stateText: String
    get() = "$initialState to $targetState"

  fun swapStates() {
    setUnitStates(targetState, initialState)
  }

  fun getState(index: Int): Any {
    return if (index == 0) initialState.components.toList() else targetState.components.toList()
  }

  fun stateHashCode(): Int {
    return Pair(initialState.hashCode(), targetState.hashCode()).hashCode()
  }

  fun updateStates(initial: Any, target: Any) {
    setUnitStates(ComposeUnit.parseStateUnit(initial), ComposeUnit.parseStateUnit(target))
  }

  private fun setUnitStates(initial: ComposeUnit.Unit<*>?, target: ComposeUnit.Unit<*>?) {
    if (initial == null || target == null) return
    initialState = initial
    targetState = target
    stateListeners.forEach { it() }
  }
}
