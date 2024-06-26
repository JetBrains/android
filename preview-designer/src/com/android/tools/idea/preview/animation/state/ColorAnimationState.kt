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

import com.android.tools.idea.preview.animation.AnimationTracker
import com.android.tools.idea.preview.animation.AnimationUnit
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.ui.JBColor
import java.awt.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * An abstract base class representing the state of a color animation.
 *
 * This class manages the initial and target colors of the animation, provides a hash code
 * representing the current state, and defines actions for changing the color states.
 *
 * @param T The type of animation unit representing a color (e.g., RGB, HSL).
 * @param tracker The [AnimationTracker] associated with this animation to log changes and events.
 * @param initialColor The starting color value for the animation.
 * @param targetColor The final color value for the animation.
 */
abstract class ColorAnimationState<T : AnimationUnit.Color<*, T>>(
  private val tracker: AnimationTracker,
  initialColor: T,
  targetColor: T,
) : FromToState<T> {
  companion object {
    val DEFAULT_COLOR: Color = JBColor.WHITE
  }

  /** The initial color state of the animation. */
  override val state: MutableStateFlow<Pair<T, T>> = MutableStateFlow(initialColor to targetColor)

  override val changeStateActions: List<AnAction> by lazy {
    val initial =
      ColorPickerAction(tracker, initialColor.color ?: DEFAULT_COLOR) {
        state.update { (_, target) -> target.create(it) to target }
      }
    val target =
      ColorPickerAction(tracker, targetColor.color ?: DEFAULT_COLOR) {
        state.update { (initial, _) -> initial to initial.create(it) }
      }

    listOf(SwapAction(tracker) { initial.swapWith(target) }, initial, ToolbarLabel("to"), target)
  }
}
