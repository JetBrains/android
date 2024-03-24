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
package com.android.tools.idea.compose.preview.animation

import androidx.compose.animation.tooling.ComposeAnimation
import kotlinx.coroutines.Job

/**
 * The minimal interface allowing to receive sufficient information to control the
 * [ComposeAnimation] state.
 */
interface ComposeAnimationHandler {
  /**
   * A controller for the existing [ComposeAnimation]. See [AnimationClock] interface for more
   * details.
   */
  var animationClock: AnimationClock?

  /**
   * Notifies the handler that an instance of [ComposeAnimation] is added to the Composable. Meaning
   * that the [animation] can be controlled with [AnimationClock] (see e.g.
   * [AnimationClock.setClockTimes]).
   */
  fun addAnimation(animation: ComposeAnimation): Job

  /**
   * Notifies the handler that an instance of [ComposeAnimation] is removed from the Composable.
   * Meaning that it can no longer be controlled.
   */
  fun removeAnimation(animation: ComposeAnimation): Job
}
