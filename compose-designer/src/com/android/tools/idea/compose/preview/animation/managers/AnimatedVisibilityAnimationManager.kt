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
package com.android.tools.idea.compose.preview.animation.managers

import androidx.compose.animation.tooling.ComposeAnimation
import com.android.tools.idea.compose.preview.animation.AnimationClock
import com.android.tools.idea.compose.preview.animation.ComposeAnimationTracker
import com.android.tools.idea.compose.preview.animation.getAnimatedVisibilityState
import com.android.tools.idea.compose.preview.animation.state.SingleState
import com.android.tools.idea.compose.preview.animation.updateAnimatedVisibilityState
import com.android.tools.idea.preview.animation.AnimationTabs
import com.android.tools.idea.preview.animation.PlaybackControls
import com.android.tools.idea.preview.animation.TimelinePanel
import javax.swing.JComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * This class handles AnimatedVisibility composable. It animates the appearance and disappearance of
 * its content.
 *
 * Animation can be in one of 2 states Enter or Exit, see [AnimatedVisibilityState] in AndroidX
 */
class AnimatedVisibilityAnimationManager(
  animation: ComposeAnimation,
  tabTitle: String,
  tracker: ComposeAnimationTracker,
  animationClock: AnimationClock,
  maxDurationPerIteration: StateFlow<Long>,
  timelinePanel: TimelinePanel,
  executeInRenderSession: suspend (Boolean, () -> Unit) -> Unit,
  tabbedPane: AnimationTabs,
  rootComponent: JComponent,
  playbackControls: PlaybackControls,
  updateTimelineElementsCallback: suspend () -> Unit,
  scope: CoroutineScope,
) :
  ComposeSupportedAnimationManager(
    animation,
    tabTitle,
    tracker,
    animationClock,
    maxDurationPerIteration,
    timelinePanel,
    executeInRenderSession,
    tabbedPane,
    rootComponent,
    playbackControls,
    updateTimelineElementsCallback,
    scope,
  ) {

  override val animationState: SingleState<*> =
    SingleState(tracker, animation.states, animation.states.firstOrNull())

  /** Initializes the state of the Compose animation before it starts */
  override suspend fun setupInitialAnimationState() {
    executeInRenderSession(true) {
      animationState.setInitialState(animationClock.getAnimatedVisibilityState(animation))
    }
  }

  /**
   * Updates the actual animation in Compose to set its state based on the selected value of
   * [animationState].
   */
  override suspend fun syncAnimationWithState() {
    animationClock.apply {
      val state = animationState.state.value ?: return
      executeInRenderSession(false) { updateAnimatedVisibilityState(animation, state) }
    }
  }
}

private fun <T> SingleState<T>.setInitialState(loadedState: Any?) {
  // AnimatedVisibilityState is an inline class in Compose that maps to a String.
  // Therefore, calling `getAnimatedVisibilityState` via reflection will return a String rather than
  // an AnimatedVisibilityState.
  // To work around that, we select the initial combo box item by checking the display value.
  val state = states.firstOrNull { it.toString() == loadedState.toString() } ?: return
  setState(state)
}
