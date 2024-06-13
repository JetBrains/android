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
import androidx.compose.animation.tooling.ComposeAnimationType
import androidx.compose.animation.tooling.TransitionInfo
import com.android.tools.idea.compose.preview.animation.AnimationClock
import com.android.tools.idea.compose.preview.animation.ComposeAnimationTracker
import com.android.tools.idea.compose.preview.animation.ComposeUnit
import com.android.tools.idea.compose.preview.animation.getAnimatedProperties
import com.android.tools.idea.compose.preview.animation.setClockTime
import com.android.tools.idea.compose.preview.animation.state.ComposeAnimationState
import com.android.tools.idea.compose.preview.animation.state.ComposeAnimationState.Companion.createState
import com.android.tools.idea.compose.preview.animation.updateAnimatedVisibilityState
import com.android.tools.idea.compose.preview.animation.updateFromAndToStates
import com.android.tools.idea.preview.animation.AnimatedProperty
import com.android.tools.idea.preview.animation.AnimationTabs
import com.android.tools.idea.preview.animation.AnimationUnit
import com.android.tools.idea.preview.animation.PlaybackControls
import com.android.tools.idea.preview.animation.SupportedAnimationManager
import com.android.tools.idea.preview.animation.TimelinePanel
import com.android.tools.idea.preview.animation.Transition
import com.intellij.openapi.diagnostic.Logger
import javax.swing.JComponent
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val LOG = Logger.getInstance(SupportedAnimationManager::class.java)

// TODO(b/161344747) This value could be dynamic depending on the curve type.
/** Number of points for one curve. */
private const val DEFAULT_CURVE_POINTS_NUMBER = 200

open class ComposeSupportedAnimationManager(
  final override val animation: ComposeAnimation,
  final override val tabTitle: String,
  tracker: ComposeAnimationTracker,
  protected val animationClock: AnimationClock,
  private val maxDurationPerIteration: StateFlow<Long>,
  timelinePanel: TimelinePanel,
  protected val executeInRenderSession: suspend (Boolean, () -> Unit) -> Unit,
  tabbedPane: AnimationTabs,
  rootComponent: JComponent,
  playbackControls: PlaybackControls,
  private val reset: suspend (Boolean) -> Unit,
  val updateTimelineElementsCallback: suspend () -> Unit,
  parentScope: CoroutineScope,
) :
  ComposeAnimationManager,
  SupportedAnimationManager(
    timelinePanel,
    playbackControls,
    tabbedPane,
    rootComponent,
    tracker,
    executeInRenderSession,
    parentScope,
    updateTimelineElementsCallback,
  ) {

  override val animationStateManager: ComposeAnimationState =
    animation.createState(tracker, animation.findCallback())

  override suspend fun resetCallback(longTimeout: Boolean) {
    reset(longTimeout)
  }

  /**
   * Due to a limitation in the Compose Animation framework, we might not know all the available
   * states for a given animation, only the initial/current one. However, we can infer all the
   * states based on the initial one depending on its type, e.g. for a boolean we know the available
   * states are only `true` or `false`.
   */
  private fun handleKnownStateTypes(originalStates: Set<Any>) =
    when (originalStates.iterator().next()) {
      is Boolean -> setOf(true, false)
      else -> originalStates
    }

  /** Initializes the state of the Compose animation before it starts */
  final override suspend fun setupStateManager() {
    animationStateManager.updateStates(handleKnownStateTypes(animation.states))
    syncStateComboBoxWithAnimationStateInLibrary()
    animationStateManager.callbackEnabled = true
  }

  protected open suspend fun syncStateComboBoxWithAnimationStateInLibrary() {
    val finalState = animation.getCurrentState()
    animationStateManager.setStartState(finalState)
    updateAnimationStartAndEndStates()
  }

  private fun ComposeAnimation.findCallback(): () -> Unit {
    return when (type) {
      ComposeAnimationType.TRANSITION_ANIMATION,
      ComposeAnimationType.ANIMATE_X_AS_STATE,
      ComposeAnimationType.ANIMATED_CONTENT -> { ->
          scope.launch {
            updateAnimationStartAndEndStates()
            loadTransition()
            loadAnimatedPropertiesAtCurrentTime(false)
            updateTimelineElementsCallback()
          }
        }
      ComposeAnimationType.ANIMATED_VISIBILITY -> { ->
          scope.launch {
            updateAnimatedVisibility()
            loadTransition()
            loadAnimatedPropertiesAtCurrentTime(false)
            updateTimelineElementsCallback()
          }
        }
      ComposeAnimationType.ANIMATED_VALUE,
      ComposeAnimationType.ANIMATABLE,
      ComposeAnimationType.ANIMATE_CONTENT_SIZE,
      ComposeAnimationType.DECAY_ANIMATION,
      ComposeAnimationType.INFINITE_TRANSITION,
      ComposeAnimationType.TARGET_BASED_ANIMATION,
      ComposeAnimationType.UNSUPPORTED -> { -> }
    }
  }

  /**
   * Updates the actual animation in Compose to set its start and end states to the ones selected in
   * the respective combo boxes.
   */
  private suspend fun updateAnimationStartAndEndStates(longTimeout: Boolean = false) {
    animationClock.apply {
      val startState = animationStateManager.getState(0) ?: return
      val toState = animationStateManager.getState(1) ?: return

      executeInRenderSession(longTimeout) { updateFromAndToStates(animation, startState, toState) }
      resetCallback(longTimeout)
    }
  }

  /**
   * Updates the actual animation in Compose to set its state based on the selected value of
   * [animationStateManager].
   */
  suspend fun updateAnimatedVisibility(longTimeout: Boolean = false) {
    animationClock.apply {
      val state = animationStateManager.getState(0) ?: return
      executeInRenderSession(longTimeout) { updateAnimatedVisibilityState(animation, state) }
      resetCallback(longTimeout)
    }
  }

  override fun loadTransitionFromLibrary(): Transition {
    val builders: MutableMap<Int, AnimatedProperty.Builder> = mutableMapOf()
    val clockTimeMsStep = max(1, maxDurationPerIteration.value / DEFAULT_CURVE_POINTS_NUMBER)

    fun getTransitions() {
      val composeTransitions =
        animationClock.getTransitionsFunction?.invoke(
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
          .mapValues { ComposeUnit.parseNumberUnit(it.value) }
          .forEach { (ms, unit) -> unit?.let { builder.add(ms.toInt(), unit) } }
        builders[index] = builder
      }
    }

    fun getAnimatedProperties() {
      for (clockTimeMs in 0..maxDurationPerIteration.value step clockTimeMsStep) {
        animationClock.setClockTime(clockTimeMs)
        val properties = animationClock.getAnimatedProperties(animation)
        for ((index, property) in properties.withIndex()) {
          ComposeUnit.parse(property)?.let { unit ->
            builders.getOrPut(index) { AnimatedProperty.Builder() }.add(clockTimeMs.toInt(), unit)
          }
        }
      }
    }

    try {
      if (animationClock.getTransitionsFunction != null) getTransitions()
      else getAnimatedProperties()
    } catch (e: Exception) {
      LOG.warn("Failed to load the Compose Animation properties", e)
    }

    builders
      .mapValues { it.value.build() }
      .let {
        return Transition(it)
      }
  }

  override suspend fun loadAnimatedPropertiesAtCurrentTime(longTimeout: Boolean) {
    var properties = emptyList<AnimationUnit.TimelineUnit>()
    executeInRenderSession(longTimeout) {
      animationClock.apply {
        try {
          properties =
            getAnimatedProperties(animation).map {
              AnimationUnit.TimelineUnit(it.label, ComposeUnit.parse(it))
            }
        } catch (e: Exception) {
          LOG.warn("Failed to get the Compose Animation properties", e)
        }
      }
    }
    animatedPropertiesAtCurrentTime = properties
  }
}

private fun ComposeAnimation.getCurrentState(): Any? {
  return when (type) {
    ComposeAnimationType.TRANSITION_ANIMATION ->
      animationObject::class
        .java
        .methods
        .singleOrNull { it.name == "getCurrentState" }
        ?.let {
          it.isAccessible = true
          it.invoke(animationObject)
        } ?: states.firstOrNull()
    else -> states.firstOrNull()
  }
}
