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
import com.android.tools.idea.preview.animation.AnimationUnit
import com.android.tools.idea.preview.animation.state.AnimationState
import com.android.tools.idea.preview.animation.state.FromToState
import com.android.tools.idea.preview.animation.state.SwapAction
import com.intellij.openapi.actionSystem.AnAction
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [AnimationState] for animations where initial and target states should be selected with a picker.
 */
class PickerState(tracker: ComposeAnimationTracker, initial: Any?, target: Any?) :
  FromToState<AnimationUnit.Unit<*>> {

  private val buttonAction =
    PickerButtonAction(tracker).apply {
      updateInitialState(initial)
      updateTargetState(target)
    }

  override val state: MutableStateFlow<Pair<AnimationUnit.Unit<*>, AnimationUnit.Unit<*>>> =
    buttonAction.state

  override val changeStateActions: List<AnAction> =
    listOf(SwapAction(tracker) { buttonAction.swapStates() }, buttonAction)
}
