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
import com.android.tools.idea.preview.animation.state.SwapAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.platform.util.coroutines.flow.mapStateIn
import kotlinx.coroutines.CoroutineScope

/**
 * [ComposeAnimationState] for animations where initial and target states should be selected with a
 * picker.
 */
class PickerState(tracker: ComposeAnimationTracker, scope: CoroutineScope) : ComposeAnimationState {

  private val buttonAction = PickerButtonAction(tracker)

  override val stateHashCode =
    buttonAction.state.mapStateIn(scope) { (initial, target) -> Pair(initial, target).hashCode() }

  override fun getState(index: Int): List<*> {
    return buttonAction.getState(index)
  }

  override fun setStartState(state: Any?) {
    buttonAction.updateInitialState(state)
  }

  override fun updateStates(states: Set<Any>) {
    buttonAction.updateInitialState(states.firstOrNull())
    buttonAction.updateTargetState(states.lastOrNull())
  }

  override val changeStateActions: List<AnAction> =
    listOf(SwapAction(tracker) { buttonAction.swapStates() }, buttonAction)
}
