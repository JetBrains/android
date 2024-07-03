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
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.ui.JBColor
import java.awt.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private val DEFAULT_COLOR: Color = JBColor.WHITE

/**
 * An abstract base class representing the state of a color animation.
 *
 * This class manages the initial and target colors of the animation, provides a hash code
 * representing the current state, and defines actions for changing the color states.
 *
 * @param tracker The [AnimationTracker] associated with this animation state.
 * @param scope The [CoroutineScope] in which the state flow for the hash code is collected.
 */
abstract class ColorAnimationState(private val tracker: AnimationTracker, scope: CoroutineScope) :
  AnimationState {

  /** The initial color state of the animation. */
  val fromState: StateFlow<Color>
    get() = _fromState

  /** The target color state of the animation. */
  val toState: StateFlow<Color>
    get() = _toState

  private val _fromState: MutableStateFlow<Color> = MutableStateFlow(DEFAULT_COLOR)
  private val _toState: MutableStateFlow<Color> = MutableStateFlow(DEFAULT_COLOR)

  override val stateHashCode =
    combine(_fromState, _toState) { from, to -> Pair(from.rgb, to.rgb).hashCode() }
      .stateIn(
        scope,
        SharingStarted.Eagerly,
        initialValue = Pair(_fromState.value.rgb, _toState.value.rgb).hashCode(),
      )

  override val changeStateActions: List<AnAction> by lazy {
    val initial = ColorPickerAction(tracker, _fromState)
    val target = ColorPickerAction(tracker, _toState)

    listOf(SwapAction(tracker) { initial.swapWith(target) }, initial, ToolbarLabel("to"), target)
  }

  fun setStates(initialColor: Color, targetColor: Color) {
    _fromState.value = initialColor
    _toState.value = targetColor
  }
}
