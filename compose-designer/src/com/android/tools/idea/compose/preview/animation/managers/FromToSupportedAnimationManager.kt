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
import com.android.tools.idea.compose.preview.animation.updateFromAndToStates
import com.android.tools.idea.preview.animation.AnimationTabs
import com.android.tools.idea.preview.animation.AnimationUnit
import com.android.tools.idea.preview.animation.PlaybackControls
import com.android.tools.idea.preview.animation.TimelinePanel
import com.android.tools.idea.preview.animation.state.FromToState
import javax.swing.JComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Animation manager specifically designed for Compose animations that transition between two states
 * (from and to).
 *
 * This class extends [ComposeSupportedAnimationManager] and provides specialized handling for
 * animations that have distinct start and end states.
 *
 * @param animation The Compose animation being managed.
 * @param tabTitle The title to display in the animation preview tab.
 * @param tracker The [ComposeAnimationTracker] for tracking animation events.
 * @param animationClock The [AnimationClock] for controlling animation.
 * @param maxDurationPerIteration State flow representing the maximum duration of each iteration.
 * @param timelinePanel The [TimelinePanel] for visualizing the animation timeline.
 * @param executeInRenderSession Function to execute code within the render session.
 * @param tabbedPane The [AnimationTabs] component for switching between different animations.
 * @param rootComponent The root UI component for rendering the animation.
 * @param playbackControls The [PlaybackControls] for controlling animation playback.
 * @param updateTimelineElementsCallback Callback to update the timeline elements when the animation
 *   state changes.
 * @param scope The coroutine scope in which this manager operates.
 * @param animationState The state of the animation, represented as a [FromToState].
 */
class FromToSupportedAnimationManager(
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
  override val animationState: FromToState<*>,
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

  /** Initializes the state manager by collecting updates from the `animationState` flow. */
  override suspend fun setupInitialAnimationState() {}

  /** Updates the actual animation in Compose to match the selected start and end states. */
  override suspend fun syncAnimationWithState() {
    animationClock.apply {
      val (initial, target) = animationState.state.value
      if (initial is AnimationUnit.Unit<*> && target is AnimationUnit.Unit<*>) {
        executeInRenderSession(false) {
          updateFromAndToStates(animation, initial.components, target.components)
        }
      } else {
        executeInRenderSession(false) {
          updateFromAndToStates(animation, listOf(initial), listOf(target))
        }
      }
    }
  }
}
