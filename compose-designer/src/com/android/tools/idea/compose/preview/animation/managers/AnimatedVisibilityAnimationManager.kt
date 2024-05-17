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
import com.android.tools.idea.preview.animation.AnimationTabs
import com.android.tools.idea.preview.animation.PlaybackControls
import com.android.tools.idea.preview.animation.TimelinePanel
import javax.swing.JComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

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
  resetCallback: suspend (Boolean) -> Unit,
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
    resetCallback,
    updateTimelineElementsCallback,
    scope,
  ) {

  /**
   * Updates the combo box that displays the possible states of an `AnimatedVisibility` animation,
   * and resets the timeline
   */
  override suspend fun setup() {
    stateComboBox.updateStates(animation.states)
    // Update the animated visibility combo box with the correct initial state, obtained from
    // PreviewAnimationClock.
    var state: Any? = null
    executeInRenderSession(true) {
      // AnimatedVisibilityState is an inline class in Compose that maps to a String. Therefore,
      // calling `getAnimatedVisibilityState`
      // via reflection will return a String rather than an AnimatedVisibilityState. To work
      // around that, we select the initial combo
      // box item by checking the display value.
      state =
        animationClock.getAnimatedVisibilityState(animation).let { loadedState ->
          animation.states.firstOrNull { it.toString() == loadedState.toString() }
        }
    }

    stateComboBox.setStartState(state ?: animation.states.firstOrNull())

    // Use a longer timeout the first time we're updating the AnimatedVisibility state. Since
    // we're running off EDT, the UI will not
    // freeze. This is necessary here because it's the first time the animation mutable states
    // will be written, when setting the clock,
    // and read, when getting its duration. These operations take longer than the default 30ms
    // timeout the first time they're executed.
    updateAnimatedVisibility(longTimeout = true)
    loadTransitionFromCacheOrLib(longTimeout = true)
    loadProperties()

    // Set up the combo box listener so further changes to the selected state will trigger a
    // call to updateAnimatedVisibility.
    stateComboBox.callbackEnabled = true
  }
}
