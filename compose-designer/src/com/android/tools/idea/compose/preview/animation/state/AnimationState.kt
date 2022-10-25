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

import com.intellij.openapi.actionSystem.AnAction

/** Animation state. */
abstract class AnimationState(callback: () -> Unit = {}) {

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
