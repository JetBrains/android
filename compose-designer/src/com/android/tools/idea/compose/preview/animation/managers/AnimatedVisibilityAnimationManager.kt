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

  override suspend fun syncStateComboBoxWithAnimationStateInLibrary() {
    executeInRenderSession(true) {
      // AnimatedVisibilityState is an inline class in Compose that maps to a String. Therefore,
      // calling `getAnimatedVisibilityState`
      // via reflection will return a String rather than an AnimatedVisibilityState. To work
      // around that, we select the initial combo
      // box item by checking the display value.
      val state =
        animationClock.getAnimatedVisibilityState(animation).let { loadedState ->
          animation.states.firstOrNull { it.toString() == loadedState.toString() }
        }
      val finalState = state ?: animation.states.firstOrNull()
      stateComboBox.setStartState(finalState)
    }
    updateAnimatedVisibility()
  }
}
