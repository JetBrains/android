/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.animation.state

import com.intellij.openapi.actionSystem.AnAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for managing the states of an animation.
 *
 * Animation states can be singular (e.g., visibility) or more commonly, transitions between initial
 * and target states (e.g., color A to B, int A to B, Compose state A to B). This interface provides
 * access to the current state and actions to modify it.
 */
interface AnimationState<T> {
  /**
   * A flow representing the current state of the animation.
   *
   * This flow emits updates whenever the animation state changes, allowing consumers to react to
   * the new state.
   */
  val state: StateFlow<T>

  /** A list of actions that can be performed to change the state of the animation. */
  val changeStateActions: List<AnAction>
}

/**
 * Represents the state of an animation that transitions between two values (from and to).
 *
 * This interface extends [AnimationState], specializing it for animations that have distinct start
 * and end states. It exposes a `MutableStateFlow` to manage the current from/to values.
 *
 * @param T The type of the animated value (e.g., Int, Float, Color, Compose State).
 */
interface FromToState<T> : AnimationState<Pair<T, T>> {

  /** A mutable flow representing the current "from" and "to" values of the animation. */
  override val state: MutableStateFlow<Pair<T, T>>
}
