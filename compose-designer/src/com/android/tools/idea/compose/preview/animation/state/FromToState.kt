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
import com.android.tools.idea.preview.animation.AnimationTracker
import com.android.tools.idea.preview.animation.state.SwapAction
import com.android.tools.idea.preview.animation.state.ToolbarLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * [ComposeAnimationState] to control comboBoxes for animations like updateTransition.
 *
 * @param tracker The [AnimationTracker] for tracking animation usage.
 * @param scope The [CoroutineScope] associated with the AnimationManager, used for managing
 *   coroutine operations within the state.
 */
class FromToState(tracker: ComposeAnimationTracker, scope: CoroutineScope) : ComposeAnimationState {

  private val fromState = EnumStateAction()
  private val toState = EnumStateAction()

  init {
    scope.launch { fromState.currentState.collect { tracker.changeStartState() } }
    scope.launch { fromState.currentState.collect { tracker.changeEndState() } }
  }

  override val stateHashCode: StateFlow<Int> =
    combine(fromState.currentState, toState.currentState) { from, to -> Pair(from, to).hashCode() }
      .stateIn(
        scope,
        SharingStarted.Eagerly,
        Pair(fromState.currentState.value, toState.currentState.value).hashCode(),
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
        val start = fromState.currentState.value
        fromState.currentState.value = toState.currentState.value
        toState.currentState.value = start
      },
      fromState,
      ToolbarLabel("to"),
      toState,
    )

  override fun updateStates(states: Set<Any>) {
    fromState.states = states
    toState.states = states
    setStartState(states.firstOrNull())
  }

  override fun getState(index: Int): Any? {
    return when (index) {
      0 -> fromState.currentState.value
      1 -> toState.currentState.value
      else -> null
    }
  }

  override fun setStartState(state: Any?) {
    fromState.currentState.value = state
    toState.currentState.value = toState.states.firstOrNull { it != state } ?: state
  }
}
