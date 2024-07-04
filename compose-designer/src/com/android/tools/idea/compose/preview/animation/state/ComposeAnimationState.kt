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

import androidx.compose.animation.tooling.ComposeAnimation
import androidx.compose.animation.tooling.ComposeAnimationType
import com.android.tools.idea.compose.preview.animation.ComposeAnimationTracker
import com.android.tools.idea.compose.preview.animation.ComposeUnit
import com.android.tools.idea.preview.animation.AnimationUnit
import com.android.tools.idea.preview.animation.state.AnimationState
import kotlinx.coroutines.CoroutineScope

/** Compose [AnimationState] */
interface ComposeAnimationState : AnimationState {

  companion object {
    /**
     * Creates a [ComposeAnimationState] that represents the state of this [ComposeAnimation].
     *
     * @param tracker The [ComposeAnimationTracker] to track the animation.
     * @param scope The [CoroutineScope] associated with the [AnimationManager], used to manage
     *   coroutine operations within the state.
     * @return A [ComposeAnimationState] corresponding to the animation's type and animation's state
     *   type.
     */
    fun ComposeAnimation.createState(
      tracker: ComposeAnimationTracker,
      scope: CoroutineScope,
    ): ComposeAnimationState {
      val unit = ComposeUnit.parseStateUnit(this.states.firstOrNull())
      return when (this.type) {
        ComposeAnimationType.ANIMATED_VISIBILITY -> SingleState(tracker, scope)
        ComposeAnimationType.TRANSITION_ANIMATION,
        ComposeAnimationType.ANIMATE_X_AS_STATE,
        ComposeAnimationType.ANIMATED_CONTENT ->
          when {
            unit is ComposeUnit.Color -> ComposeColorState(tracker, scope)
            unit !is AnimationUnit.UnitUnknown -> PickerState(tracker, scope)
            states.firstOrNull() is Boolean -> FromToState(tracker, scope)
            states.firstOrNull() is Enum<*> -> FromToState(tracker, scope)
            else -> FromToState(tracker, scope)
          }
        ComposeAnimationType.ANIMATED_VALUE,
        ComposeAnimationType.ANIMATABLE,
        ComposeAnimationType.ANIMATE_CONTENT_SIZE,
        ComposeAnimationType.DECAY_ANIMATION,
        ComposeAnimationType.INFINITE_TRANSITION,
        ComposeAnimationType.TARGET_BASED_ANIMATION,
        ComposeAnimationType.UNSUPPORTED -> EmptyState()
      }
    }
  }

  /** Set list of available states. */
  fun updateStates(states: Set<Any>)

  /** Get selected state for the [index]. */
  fun getState(index: Int = 0): Any?

  /** Set a start state. */
  fun setStartState(state: Any?)
}
