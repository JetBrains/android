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
import com.android.tools.idea.compose.preview.animation.ComposeAnimationEventTracker
import com.android.tools.idea.compose.preview.animation.ComposeUnit
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.AnAction

/** Animation state. */
abstract class AnimationState(callback: () -> Unit = {}) {

  companion object {

    /** Create an [AnimationState] based on [ComposeAnimationType] and type of state. */
    fun ComposeAnimation.createState(
      tracker: ComposeAnimationEventTracker,
      callback: () -> Unit
    ): AnimationState {
      val unit = ComposeUnit.parseValue(this.states.firstOrNull())
      return when (this.type) {
        ComposeAnimationType.TRANSITION_ANIMATION -> FromToState(tracker, callback)
        ComposeAnimationType.ANIMATED_VISIBILITY -> SingleState(tracker, callback)
        ComposeAnimationType.ANIMATE_X_AS_STATE ->
          if (StudioFlags.COMPOSE_ANIMATION_PREVIEW_ANIMATE_X_AS_STATE.get()) {
            when (unit) {
              is ComposeUnit.Color -> ColorPickerState(tracker, callback)
              else -> PickerState(tracker, callback)
            }
          } else EmptyState()
        ComposeAnimationType.ANIMATED_CONTENT ->
          if (StudioFlags.COMPOSE_ANIMATION_PREVIEW_ANIMATED_CONTENT.get()) {
            when (unit) {
              is ComposeUnit.Color -> ColorPickerState(tracker, callback)
              else -> PickerState(tracker, callback)
            }
          } else EmptyState()
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

  var callbackEnabled = false

  /** [stateCallback] should be enabled or disabled with [callbackEnabled]. */
  protected val stateCallback = { if (callbackEnabled) callback() }

  /** Set list of available states. */
  abstract fun updateStates(states: Set<Any>)

  /** Hash code of selected state. */
  abstract fun stateHashCode(): Int

  /** Get selected state for the [index]. */
  abstract fun getState(index: Int = 0): Any?

  /** Set a start state. */
  abstract fun setStartState(state: Any?)

  open val extraActions: List<AnAction>
    get() = emptyList()
}
