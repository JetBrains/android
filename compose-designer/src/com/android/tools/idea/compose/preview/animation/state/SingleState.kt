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
import com.android.tools.idea.preview.animation.state.AnimationState
import com.android.tools.idea.preview.animation.state.SwapAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [ComposeAnimationState] to control animations like AnimatedVisibility.
 *
 * @param tracker The [AnimationTracker] for tracking animation usage.
 * @param scope The [CoroutineScope] associated with the AnimationManager, used for managing
 *   coroutine operations within the state.
 */
class SingleState<T>(private val tracker: AnimationTracker, val states: Set<T>, initialState: T) :
  AnimationState<T> {

  override val state = MutableStateFlow(initialState)

  private val enumState = EnumStateAction(states, { state.value = it }, initialState)

  fun setState(state: T) {
    enumState.currentState = state
  }

  /** Contains [SwapAction] and [EnumStateAction] for Enter/Exit state. */
  override val changeStateActions =
    listOf(
      SwapAction(tracker) {
        val nextState =
          enumState.states.firstOrNull { state -> state != enumState.currentState }
            ?: return@SwapAction
        enumState.currentState = nextState
      },
      enumState,
    )
}
