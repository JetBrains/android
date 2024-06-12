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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Compose animation state. */
interface ComposeAnimationState : AnimationState {

  companion object {
    /** Create an [ComposeAnimationState] based on [ComposeAnimationType] and type of state. */
    fun ComposeAnimation.createState(
      tracker: ComposeAnimationTracker,
      callback: () -> Unit,
    ): ComposeAnimationState {
      val unit = ComposeUnit.parseStateUnit(this.states.firstOrNull())
      return when (this.type) {
        ComposeAnimationType.ANIMATED_VISIBILITY -> SingleState(tracker, callback)
        ComposeAnimationType.TRANSITION_ANIMATION,
        ComposeAnimationType.ANIMATE_X_AS_STATE,
        ComposeAnimationType.ANIMATED_CONTENT ->
          when {
            unit is ComposeUnit.Color -> ColorPickerState(tracker, callback)
            unit !is AnimationUnit.UnitUnknown -> PickerState(tracker, callback)
            states.firstOrNull() is Boolean -> FromToState(tracker, callback)
            states.firstOrNull() is Enum<*> -> FromToState(tracker, callback)
            else -> FromToState(tracker, callback)
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

  var callbackEnabled: Boolean

  /** Set list of available states. */
  fun updateStates(states: Set<Any>)

  /** Hash code of selected state. */
  fun stateHashCode(): Int

  /** Get selected state for the [index]. */
  fun getState(index: Int = 0): Any?

  /** Set a start state. */
  fun setStartState(state: Any?)

  override val stateHashCode
    get() = MutableStateFlow(stateHashCode()).asStateFlow()
}
