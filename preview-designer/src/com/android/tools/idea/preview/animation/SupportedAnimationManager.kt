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
package com.android.tools.idea.preview.animation

import kotlinx.coroutines.flow.MutableStateFlow

/** Supported animation types could be opened in a new tab. Its card could be frozen or offset. */
abstract class SupportedAnimationManager : AnimationManager {

  /** Represents the frozen state of the animation. */
  data class FrozenState(val isFrozen: Boolean = false, val frozenAt: Int = 0)

  /** The offset in ms for which the animation is shifted. */
  val offset = MutableStateFlow(0)

  val frozenState = MutableStateFlow(FrozenState(false))

  abstract val tab: AnimationTab
}
