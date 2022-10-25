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

/**
 * [AnimationState] to control animations like AnimatedVisibility.
 * @param tracker usage tracker for animation tooling
 * @param callback when state has changed
 */
class SingleState(private val tracker: ComposeAnimationEventTracker, callback: () -> Unit) :
  AnimationState(callback) {

  private val enumState = EnumStateAction(stateCallback)

  /** Contains [SwapAction] and [EnumStateAction] for Enter/Exit state. */
  override val extraActions =
    listOf(
      SwapAction(tracker) {
        enumState.states.firstOrNull { it != enumState.currentState }?.let {
          enumState.currentState = it
        }
        stateCallback()
      },
      enumState
    )

  override fun stateHashCode() = enumState.stateHashCode

  override fun updateStates(states: Set<Any>) {
    enumState.states = states.toTypedArray().toSet()
    setStartState(states.firstOrNull())
  }

  override fun getState(index: Int): Any? = enumState.currentState

  override fun setStartState(state: Any?) {
    enumState.currentState = state
    stateCallback()
  }
}
