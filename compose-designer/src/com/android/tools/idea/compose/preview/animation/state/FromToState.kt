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
import com.android.tools.idea.compose.preview.animation.actions.EnumStateAction
import com.android.tools.idea.compose.preview.animation.actions.SwapAction
import com.android.tools.idea.compose.preview.animation.actions.ToolbarLabel

/**
 * [AnimationState] to control comboBoxes for animations like updateTransition.
 * @param tracker usage tracker for animation tooling
 * @param callback when state has changed
 */
class FromToState(tracker: ComposeAnimationEventTracker, callback: () -> Unit) :
  AnimationState(callback) {

  private val fromState = EnumStateAction(stateCallback)
  private val toState = EnumStateAction(stateCallback)

  /**
   * Contains
   * * [SwapAction]
   * * [EnumStateAction] for `from` state
   * * `to` [ToolbarLabel]
   * * [EnumStateAction] for `to` state.
   */
  override val extraActions =
    listOf(
      SwapAction(tracker) {
        val start = fromState.currentState
        fromState.currentState = toState.currentState
        toState.currentState = start
        stateCallback()
      },
      fromState,
      ToolbarLabel("to"),
      toState
    )

  override fun stateHashCode() = Pair(fromState.stateHashCode, toState.stateHashCode).hashCode()

  override fun updateStates(states: Set<Any>) {
    fromState.states = states
    toState.states = states
    setStartState(states.firstOrNull())
  }

  override fun getState(index: Int): Any? {
    return when (index) {
      0 -> fromState.currentState
      1 -> toState.currentState
      else -> null
    }
  }

  override fun setStartState(state: Any?) {
    fromState.currentState = state
    toState.currentState = toState.states.firstOrNull { it != state } ?: state
    stateCallback()
  }
}
