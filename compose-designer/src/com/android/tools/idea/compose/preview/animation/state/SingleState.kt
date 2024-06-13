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

import com.android.tools.idea.preview.animation.AnimationTracker
import com.android.tools.idea.preview.animation.state.SwapAction
import com.intellij.platform.util.coroutines.flow.mapStateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * [ComposeAnimationState] to control animations like AnimatedVisibility.
 *
 * @param tracker The [AnimationTracker] for tracking animation usage.
 * @param scope The [CoroutineScope] associated with the AnimationManager, used for managing
 *   coroutine operations within the state.
 */
class SingleState(private val tracker: AnimationTracker, scope: CoroutineScope) :
  ComposeAnimationState {

  private val enumState = EnumStateAction()

  override val stateHashCode: StateFlow<Int> =
    enumState.currentState.mapStateIn(scope) { it?.hashCode() ?: 0 }

  /** Contains [SwapAction] and [EnumStateAction] for Enter/Exit state. */
  override val changeStateActions =
    listOf(
      SwapAction(tracker) {
        val nextState =
          enumState.states.firstOrNull { state -> state != enumState.currentState.value }
            ?: return@SwapAction
        enumState.currentState.value = nextState
      },
      enumState,
    )

  override fun updateStates(states: Set<Any>) {
    enumState.states = states.toTypedArray().toSet()
    setStartState(states.firstOrNull())
  }

  override fun getState(index: Int): Any? = enumState.currentState.value

  override fun setStartState(state: Any?) {
    enumState.currentState.value = state
  }
}
