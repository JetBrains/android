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
package com.android.tools.idea.wear.preview.animation.state

import com.android.tools.idea.preview.animation.AnimationTracker
import com.android.tools.idea.preview.animation.state.ColorAnimationState
import com.android.tools.idea.preview.animation.state.FromToState
import com.android.tools.idea.wear.preview.animation.ColorUnit
import com.android.tools.idea.wear.preview.animation.ProtoAnimation
import com.android.tools.idea.wear.preview.animation.state.managers.actions.FloatInputComponentAction
import com.android.tools.idea.wear.preview.animation.state.managers.actions.IntInputComponentAction
import com.intellij.openapi.actionSystem.AnAction
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Interface representing a state for Wear Tile animations.
 *
 * A state is responsible for maintaining and updating the state of a specific type of animation
 * used in Wear Tiles. It provides access to the current "from" and "to" states of the animation.
 */
interface WearTileAnimationState<T> : FromToState<T> {
  /**
   * Applies the current state to the provided [ProtoAnimation] object, modifying the animation
   * properties to reflect the current "from" and "to" states.
   *
   * @param animation The [ProtoAnimation] object to be updated.
   */
  fun updateAnimation(animation: ProtoAnimation)
}

class WearTileFloatState(initial: Float?, target: Float?) : WearTileAnimationState<Float> {

  override val state = MutableStateFlow((initial ?: 0f) to (target ?: 0f))

  override fun updateAnimation(animation: ProtoAnimation) {
    val (initial, target) = state.value
    animation.setFloatValues(initial, target)
  }

  override val changeStateActions: List<AnAction> =
    listOf(
      FloatInputComponentAction(state.value.first) { state.value = it to state.value.second },
      // TODO AS Ladybug Beta 2 Merge
      //ToolbarLabel("to"),
      FloatInputComponentAction(state.value.second) { state.value = state.value.first to it },
    )
}

class WearTileIntState(initial: Int?, target: Int?) : WearTileAnimationState<Int> {
  override val state = MutableStateFlow((initial ?: 0) to (target ?: 0))

  override fun updateAnimation(animation: ProtoAnimation) {
    val (initial, target) = state.value
    animation.setIntValues(initial, target)
  }

  override val changeStateActions: List<AnAction> =
    listOf(
      IntInputComponentAction(state.value.first) { state.value = it to state.value.second },
      // TODO AS Ladybug Beta 2 Merge
      //ToolbarLabel("to"),
      IntInputComponentAction(state.value.second) { state.value = state.value.first to it },
    )
}

class WearTileColorPickerState(
  tracker: AnimationTracker,
  initialColor: ColorUnit,
  targetColor: ColorUnit,
) :
  WearTileAnimationState<ColorUnit>,
  ColorAnimationState<ColorUnit>(tracker, initialColor, targetColor) {

  override fun updateAnimation(animation: ProtoAnimation) {
    val (initialColor, targetColor) = state.value
    animation.setIntValues(initialColor.color.rgb, targetColor.color.rgb)
  }
}

object NoopAnimationState : WearTileAnimationState<Unit> {
  override val state: MutableStateFlow<Pair<Unit, Unit>> = MutableStateFlow(Unit to Unit)

  override fun updateAnimation(animation: ProtoAnimation) {
    // No-op - do nothing
  }

  override val changeStateActions: List<AnAction> = emptyList()
}
