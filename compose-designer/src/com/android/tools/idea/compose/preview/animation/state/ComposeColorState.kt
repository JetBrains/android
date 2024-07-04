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

import com.android.tools.idea.compose.preview.animation.ComposeUnit
import com.android.tools.idea.preview.animation.AnimationTracker
import com.android.tools.idea.preview.animation.state.ColorAnimationState
import kotlinx.coroutines.CoroutineScope

/**
 * [ComposeAnimationState] for animations where initial and target states should be selected with a
 * color picker.
 */
class ComposeColorState(tracker: AnimationTracker, scope: CoroutineScope) :
  ComposeAnimationState, ColorAnimationState(tracker, scope) {

  override fun getState(index: Int): Any {
    return if (index == 0) ComposeUnit.Color.create(fromState.value).components
    else ComposeUnit.Color.create(toState.value).components
  }

  override fun setStartState(state: Any?) {
    (ComposeUnit.parseStateUnit(state) as? ComposeUnit.Color).let { setStates(it, it) }
  }

  override fun updateStates(states: Set<Any>) {
    setStates(
      states.firstOrNull().let { ComposeUnit.parseStateUnit(it) as? ComposeUnit.Color },
      states.lastOrNull().let { ComposeUnit.parseStateUnit(it) as? ComposeUnit.Color },
    )
  }

  fun setStates(initialColor: ComposeUnit.Color?, targetColor: ComposeUnit.Color?) {
    if (initialColor?.color != null && targetColor?.color != null) {
      setStates(initialColor.color, targetColor.color)
    }
  }
}
