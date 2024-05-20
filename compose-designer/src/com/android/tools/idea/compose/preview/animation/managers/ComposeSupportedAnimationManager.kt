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
import com.android.tools.idea.compose.preview.animation.state.AnimationState.Companion.createState
import com.android.tools.idea.compose.preview.animation.updateAnimatedVisibilityState
import com.android.tools.idea.compose.preview.animation.updateFromAndToStates
import com.android.tools.idea.preview.animation.AnimatedProperty
import com.android.tools.idea.preview.animation.AnimationCard
import com.android.tools.idea.preview.animation.AnimationTab
import com.android.tools.idea.preview.animation.AnimationTabs
import com.android.tools.idea.preview.animation.AnimationUnit
import com.android.tools.idea.preview.animation.PlaybackControls
import com.android.tools.idea.preview.animation.SupportedAnimationManager
import com.android.tools.idea.preview.animation.TimelinePanel
import com.android.tools.idea.preview.animation.Transition
import com.android.tools.idea.preview.animation.actions.FreezeAction
import com.android.tools.idea.preview.animation.timeline.PositionProxy
import com.android.tools.idea.preview.animation.timeline.TimelineElement
import com.android.tools.idea.preview.animation.timeline.TimelineLine
import com.android.tools.idea.preview.animation.timeline.TransitionCurve
import com.android.tools.idea.preview.animation.timeline.getOffsetForValue
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.tabs.TabInfo
import javax.swing.JComponent
import kotlin.math.max
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val LOG = Logger.getInstance(SupportedAnimationManager::class.java)

// TODO(b/161344747) This value could be dynamic depending on the curve type.
/** Number of points for one curve. */
private const val DEFAULT_CURVE_POINTS_NUMBER = 200

open class ComposeSupportedAnimationManager(
  final override val animation: ComposeAnimation,
  final override val tabTitle: String,
  private val tracker: ComposeAnimationTracker,
  protected val animationClock: AnimationClock,
  private val maxDurationPerIteration: StateFlow<Long>,
  private val timelinePanel: TimelinePanel,
  protected val executeInRenderSession: suspend (Boolean, () -> Unit) -> Unit,
  private val tabbedPane: AnimationTabs,
  private val rootComponent: JComponent,
  playbackControls: PlaybackControls,
  val resetCallback: suspend (Boolean) -> Unit,
  val updateTimelineElementsCallback: suspend () -> Unit,
  parentScope: CoroutineScope,
) : ComposeAnimationManager, SupportedAnimationManager() {

  private val scope = parentScope.createChildScope(tabTitle)

  /** Callback when [selectedProperties] has been changed. */
  var selectedPropertiesCallback: (List<AnimationUnit.TimelineUnit>) -> Unit = {}
  /**
   * Currently selected properties in the timeline. Updated everytime the slider has moved or the
   * state of animation has changed. Could be empty if transition is not loaded or not supported.
   */
  private var selectedProperties = listOf<AnimationUnit.TimelineUnit>()
    private set(value) {
      field = value
      selectedPropertiesCallback(value)
    }

  override suspend fun destroy() {
    scope.cancel("AnimationManager is destroyed")
  }

  /** Animation [Transition]. Could be empty for unsupported or not yet loaded transitions. */
  private var currentTransition = Transition()
    private set(value) {
      field = value
      // If transition has changed, reset it offset.
      offset.value = 0
    }

  override val timelineMaximumMs: Int
    get() = currentTransition.endMillis?.let { max(it + offset.value, it) } ?: 0

  val stateComboBox = animation.createState(tracker, animation.findCallback())

  /** [AnimationCard] for coordination panel. */
  override val card: AnimationCard =
    AnimationCard(
        rootComponent,
        tabTitle,
        listOf(FreezeAction(timelinePanel, frozenState, tracker)) +
          stateComboBox.changeStateActions,
        tracker,
      )
      .apply {

        /** [TabInfo] for the animation when it is opened in a new tab. */
        var tabInfo: TabInfo? = null

        /** Create if required and open the tab. */
        fun addTabToPane() {
          if (tabInfo == null) {
            tabInfo =
              TabInfo(tab.component).apply {
                text = tabTitle
                tabbedPane.addTabWithCloseButton(this) { tabInfo = null }
              }
          }
          tabInfo?.let { tabbedPane.select(it, true) }
        }
        this.addOpenInTabListener { addTabToPane() }
      }

  override val tab by lazy {
    AnimationTab(
      rootComponent,
      playbackControls,
      stateComboBox.changeStateActions,
      FreezeAction(timelinePanel, frozenState, tracker),
    )
  }

  private val cachedTransitions: MutableMap<Int, Transition> = mutableMapOf()

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
  final override suspend fun setup() {
    stateComboBox.updateStates(handleKnownStateTypes(animation.states))
    syncStateComboBoxWithAnimationStateInLibrary()

    loadTransitionFromCacheOrLib(longTimeout = true)
    loadProperties()
    stateComboBox.callbackEnabled = true

    // Launch coroutines to handle state changes
    scope.launch { frozenState.collect { updateTimelineElementsCallback() } }
    scope.launch { card.expanded.collect { updateTimelineElementsCallback() } }
    scope.launch {
      offset.collect {
        loadProperties()
        updateTimelineElementsCallback()
      }
    }
  }

  protected open suspend fun syncStateComboBoxWithAnimationStateInLibrary() {
    val finalState = animation.getCurrentState()
    stateComboBox.setStartState(finalState)
    updateAnimationStartAndEndStates()
  }

  private fun ComposeAnimation.findCallback(): () -> Unit {
    return when (type) {
      ComposeAnimationType.TRANSITION_ANIMATION,
      ComposeAnimationType.ANIMATE_X_AS_STATE,
      ComposeAnimationType.ANIMATED_CONTENT -> { ->
          scope.launch {
            updateAnimationStartAndEndStates()
            loadTransitionFromCacheOrLib()
            loadProperties()
            updateTimelineElementsCallback()
          }
        }
      ComposeAnimationType.ANIMATED_VISIBILITY -> { ->
          scope.launch {
            updateAnimatedVisibility()
            loadTransitionFromCacheOrLib()
            loadProperties()
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
      val startState = stateComboBox.getState(0) ?: return
      val toState = stateComboBox.getState(1) ?: return

      executeInRenderSession(longTimeout) { updateFromAndToStates(animation, startState, toState) }
      resetCallback(longTimeout)
    }
  }

  /**
   * Updates the actual animation in Compose to set its state based on the selected value of
   * [stateComboBox].
   */
  suspend fun updateAnimatedVisibility(longTimeout: Boolean = false) {
    animationClock.apply {
      val state = stateComboBox.getState(0) ?: return
      executeInRenderSession(longTimeout) { updateAnimatedVisibilityState(animation, state) }
      resetCallback(longTimeout)
    }
  }

  /**
   * Load transition for current start and end state. If transition was loaded before, the cached
   * result is used.
   */
  suspend fun loadTransitionFromCacheOrLib(longTimeout: Boolean = false) {
    val stateHash = stateComboBox.stateHashCode()

    cachedTransitions[stateHash]?.let {
      currentTransition = it
      return@loadTransitionFromCacheOrLib
    }

    executeInRenderSession(longTimeout) {
      val transition = loadTransitionsFromLib()
      cachedTransitions[stateHash] = transition
      currentTransition = transition
    }
  }

  private fun loadTransitionsFromLib(): Transition {
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

  suspend fun loadProperties() {
    executeInRenderSession(false) {
      animationClock.apply {
        try {
          selectedProperties =
            getAnimatedProperties(animation).map {
              AnimationUnit.TimelineUnit(it.label, ComposeUnit.parse(it))
            }
        } catch (e: Exception) {
          LOG.warn("Failed to get the Compose Animation properties", e)
        }
      }
    }
  }

  override fun createTimelineElement(
    parent: JComponent,
    minY: Int,
    forIndividualTab: Boolean,
    positionProxy: PositionProxy,
  ): TimelineElement {
    val offsetPx = getOffsetForValue(offset.value, positionProxy)
    val timelineElement =
      if (card.expanded.value || forIndividualTab) {
        val curve =
          TransitionCurve.create(
            offsetPx,
            frozenState.value,
            currentTransition,
            minY,
            positionProxy,
          )
        selectedPropertiesCallback = { curve.timelineUnits = it }
        curve.timelineUnits = selectedProperties
        curve
      } else
        TimelineLine(
            offsetPx,
            frozenState.value,
            currentTransition.startMillis?.let { positionProxy.xPositionForValue(it) }
              ?: (positionProxy.minimumXPosition()),
            currentTransition.endMillis?.let { positionProxy.xPositionForValue(it) }
              ?: positionProxy.minimumXPosition(),
            minY,
          )
          .also {
            card.expandedSize = TransitionCurve.expectedHeight(currentTransition)
            card.setDuration(currentTransition.duration)
          }

    timelineElement.setNewOffsetCallback {
      offset.value = timelineElement.getValueForOffset(it, positionProxy)
    }
    return timelineElement
  }

  private fun TimelineElement.getValueForOffset(offsetPx: Int, positionProxy: PositionProxy) =
    if (offsetPx >= 0)
      positionProxy.valueForXPosition(minX + offsetPx) - positionProxy.valueForXPosition(minX)
    else positionProxy.valueForXPosition(maxX + offsetPx) - positionProxy.valueForXPosition(maxX)
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

private fun CoroutineScope.createChildScope(name: String) =
  CoroutineScope(SupervisorJob(coroutineContext[Job]) + CoroutineName("AnimationManager.$name"))
