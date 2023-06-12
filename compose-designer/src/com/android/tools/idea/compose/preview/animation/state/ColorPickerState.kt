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
import com.android.tools.idea.compose.preview.animation.ComposeUnit
import com.android.tools.idea.compose.preview.animation.actions.ColorStateAction
import com.android.tools.idea.compose.preview.animation.actions.SwapAction
import com.android.tools.idea.compose.preview.animation.actions.ToolbarLabel
import com.intellij.openapi.actionSystem.AnAction

/**
 * [AnimationState] for animations where initial and target states should be selected with a color
 * picker.
 */
class ColorPickerState(tracker: ComposeAnimationEventTracker, callback: () -> Unit) :
  AnimationState(callback) {

  private val initialState =
    ColorStateAction(tracker = tracker, onPropertiesUpdated = stateCallback)
  private val targetState = ColorStateAction(tracker = tracker, onPropertiesUpdated = stateCallback)

  override fun stateHashCode(): Int =
    (initialState.stateHashCode() to targetState.stateHashCode()).hashCode()

  override fun getState(index: Int): Any? {
    return if (index == 0) initialState.getStateAsComponents()
    else targetState.getStateAsComponents()
  }

  override fun setStartState(state: Any?) {
    (ComposeUnit.parseStateUnit(state) as? ComposeUnit.Color).let { setStates(it, it) }
  }

  override fun updateStates(states: Set<Any>) {
    setStates(
      states.firstOrNull().let { ComposeUnit.parseStateUnit(it) as? ComposeUnit.Color },
      states.lastOrNull().let { ComposeUnit.parseStateUnit(it) as? ComposeUnit.Color }
    )
  }

  fun setStates(initialColor: ComposeUnit.Color?, targetColor: ComposeUnit.Color?) {
    if (initialColor != null && targetColor != null) {
      initialState.state = initialColor
      targetState.state = targetColor
    }
  }

  override val extraActions: List<AnAction> =
    listOf(
      SwapAction(tracker) {
        initialState.swapWith(targetState)
        stateCallback()
      },
      initialState,
      ToolbarLabel("to"),
      targetState
    )
}
