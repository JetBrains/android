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
package com.android.tools.idea.compose.preview.animation.state

import com.android.tools.idea.compose.preview.animation.ComposeAnimationEventTracker
import com.android.tools.idea.compose.preview.animation.actions.PickerButtonAction
import com.android.tools.idea.compose.preview.animation.actions.SwapAction
import com.intellij.openapi.actionSystem.AnAction

/**
 * [AnimationState] for animations where initial and target states should be selected with a picker.
 */
class PickerState(tracker: ComposeAnimationEventTracker, callback: () -> Unit) :
  AnimationState(callback) {

  private val buttonAction = PickerButtonAction(tracker, stateCallback)
  override fun stateHashCode(): Int = buttonAction.stateHashCode()

  override fun getState(index: Int): Any? {
    return buttonAction.getState(index)
  }

  override fun setStartState(state: Any?) {
    state?.let { buttonAction.updateStates(it, it) }
  }

  override fun updateStates(states: Set<Any>) {
    val initial = states.firstOrNull() ?: return
    val target = states.lastOrNull() ?: return
    buttonAction.updateStates(initial, target)
  }

  override val extraActions: List<AnAction> =
    listOf(
      SwapAction(tracker) {
        buttonAction.swapStates()
        stateCallback()
      },
      buttonAction
    )
}
