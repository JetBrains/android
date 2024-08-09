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
import androidx.compose.animation.tooling.TransitionInfo
import com.android.tools.idea.compose.preview.animation.AnimationClock
import com.android.tools.idea.compose.preview.animation.ComposeAnimationTracker
import com.android.tools.idea.compose.preview.animation.ComposeUnit
import com.android.tools.idea.compose.preview.animation.getAnimatedProperties
import com.android.tools.idea.preview.animation.AnimatedProperty
import com.android.tools.idea.preview.animation.AnimationTabs
import com.android.tools.idea.preview.animation.AnimationUnit
import com.android.tools.idea.preview.animation.PlaybackControls
import com.android.tools.idea.preview.animation.SupportedAnimationManager
import com.android.tools.idea.preview.animation.Transition
import com.intellij.openapi.diagnostic.Logger
import javax.swing.JComponent
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

private val LOG = Logger.getInstance(SupportedAnimationManager::class.java)

// TODO(b/161344747) This value could be dynamic depending on the curve type.
/** Number of points for one curve. */
private const val DEFAULT_CURVE_POINTS_NUMBER = 200

/**
 * Abstract base class for managing supported Compose animations in the Animation Preview tool.
 *
 * This class provides a common framework for handling different types of Compose animations,
 * including their interaction with the animation preview timeline, playback controls, and state
 * management. Subclasses specialize this behavior for specific animation types (e.g., from-to
 * animations, visibility animations).
 */
abstract class ComposeSupportedAnimationManager(
  final override val animation: ComposeAnimation,
  final override val tabTitle: String,
  tracker: ComposeAnimationTracker,
  protected val animationClock: AnimationClock,
  private val maxDurationPerIteration: StateFlow<Long>,
  getCurrentTime: () -> Int,
  protected val executeInRenderSession: suspend (Boolean, () -> Unit) -> Unit,
  tabbedPane: AnimationTabs,
  rootComponent: JComponent,
  playbackControls: PlaybackControls,
  updateTimelineElementsCallback: suspend () -> Unit,
  parentScope: CoroutineScope,
) :
  ComposeAnimationManager,
  SupportedAnimationManager(
    getCurrentTime,
    playbackControls,
    tabbedPane,
    rootComponent,
    tracker,
    executeInRenderSession,
    parentScope,
    updateTimelineElementsCallback,
  ) {

  final override fun loadTransitionFromLibrary(): Transition {
    val builders: MutableMap<Int, AnimatedProperty.Builder> = mutableMapOf()
    val clockTimeMsStep = max(1, maxDurationPerIteration.value / DEFAULT_CURVE_POINTS_NUMBER)

    try {
      val composeTransitions =
        animationClock.getTransitionsFunction.invoke(
          animationClock.clock,
          animation,
          clockTimeMsStep,
        ) as List<TransitionInfo>
      for ((index, composeTransition) in composeTransitions.withIndex()) {
        val builder =
          AnimatedProperty.Builder()
            .setStartTimeMs(composeTransition.startTimeMillis.toInt())
            .setEndTimeMs(composeTransition.endTimeMillis.toInt())
        composeTransition.values
          .mapValues { ComposeUnit.parseStateUnit(it.value) }
          .forEach { (ms, unit) ->
            if (unit is AnimationUnit.NumberUnit) {
              builder.add(ms.toInt(), unit)
            }
          }
        builders[index] = builder
      }
    } catch (e: Exception) {
      LOG.warn("Failed to load the Compose Animation properties", e)
    }

    builders
      .mapValues { it.value.build() }
      .let {
        return Transition(it)
      }
  }

  final override suspend fun loadAnimatedPropertiesAtCurrentTime(longTimeout: Boolean) {
    var properties = emptyList<AnimationUnit.TimelineUnit>()
    executeInRenderSession(longTimeout) {
      animationClock.apply {
        try {
          properties =
            getAnimatedProperties(animation).map {
              AnimationUnit.TimelineUnit(it.label, ComposeUnit.parseStateUnit(it))
            }
        } catch (e: Exception) {
          LOG.warn("Failed to get the Compose Animation properties", e)
        }
      }
    }
    animatedPropertiesAtCurrentTime = properties
  }
}
