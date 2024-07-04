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

import com.android.tools.idea.compose.preview.animation.ComposeAnimationTracker
import com.android.tools.idea.preview.animation.state.FromToState
import com.android.tools.idea.preview.animation.state.SwapAction
import com.android.tools.idea.preview.animation.state.ToolbarLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * [AnimationState] to control comboBoxes for animations like updateTransition.
 *
 * @param tracker The [ComposeAnimationTracker] for tracking animation usage.
 */
open class FromToStateComboBox<T>(
  tracker: ComposeAnimationTracker,
  states: Set<T>,
  initialState: T,
) : FromToState<T> {
  final override val state =
    MutableStateFlow(initialState to (states.firstOrNull { initialState != it } ?: initialState))

  private val fromState =
    EnumStateAction(
      states,
      {
        state.update { (_, target) ->
          tracker.changeStartState()
          it to target
        }
      },
      state.value.first,
    )
  private val toState =
    EnumStateAction(
      states,
      {
        state.update { (initial, _) ->
          tracker.changeEndState()
          initial to it
        }
      },
      state.value.second,
    )

  /**
   * Contains
   * * [SwapAction]
   * * [EnumStateAction] for `from` state
   * * `to` [ToolbarLabel]
   * * [EnumStateAction] for `to` state.
   */
  override val changeStateActions =
    listOf(
      SwapAction(tracker) {
        val start = fromState.currentState
        fromState.currentState = toState.currentState
        toState.currentState = start
      },
      fromState,
      ToolbarLabel("to"),
      toState,
    )
}

class BooleanFromToState(tracker: ComposeAnimationTracker, initialState: Boolean) :
  FromToStateComboBox<Boolean>(tracker, setOf(true, false), initialState)

class EnumFromToState(
  tracker: ComposeAnimationTracker,
  states: Set<Enum<*>>,
  initialState: Enum<*>,
) : FromToStateComboBox<Enum<*>>(tracker, states, initialState) {
  init {
    if (!states.contains(initialState)) {
      throw IllegalArgumentException("Initial state $initialState is not present in $states")
    }
  }
}
