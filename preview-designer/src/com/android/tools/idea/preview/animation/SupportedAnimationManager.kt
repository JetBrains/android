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
package com.android.tools.idea.preview.animation

import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.preview.animation.actions.FreezeAction
import com.android.tools.idea.preview.animation.state.AnimationState
import com.android.tools.idea.preview.animation.timeline.PositionProxy
import com.android.tools.idea.preview.animation.timeline.TimelineElement
import com.android.tools.idea.preview.animation.timeline.TimelineLine
import com.android.tools.idea.preview.animation.timeline.TransitionCurve
import com.intellij.ui.tabs.TabInfo
import javax.swing.JComponent
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages supported animation types that can be opened in tabs or frozen within a timeline, or
 * their state can be changed for the sake of inspection.
 *
 * This abstract class provides the foundation for handling animations within the context of a
 * timeline panel, playback controls, and a tabbed interface. It manages animation state, timeline
 * elements, and loading/caching of transitions.
 *
 * @param getCurrentTime Returns time in milliseconds in the current timeline.
 * @param playbackControls The PlaybackControls to control the animation playback.
 * @param tabbedPane The AnimationTabs where animation tabs can be opened.
 * @param rootComponent The root JComponent used for UI elements.
 * @param tracker An AnimationTracker to track animation events.
 * @param executeInRenderSession A function to execute tasks within the render session.
 * @param parentScope The parent CoroutineScope for managing coroutines.
 * @param updateTimelineElementsCallback A callback function to update timeline elements.
 */
abstract class SupportedAnimationManager(
  getCurrentTime: () -> Int,
  playbackControls: PlaybackControls,
  private val tabbedPane: AnimationTabs,
  private val rootComponent: JComponent,
  private val tracker: AnimationTracker,
  private val executeInRenderSession: suspend (Boolean, () -> Unit) -> Unit,
  parentScope: CoroutineScope,
  private val updateTimelineElementsCallback: suspend () -> Unit,
) : AnimationManager {

  /** A dedicated CoroutineScope for managing coroutines specific to this animation. */
  protected val scope = parentScope.createChildScope(tabTitle)

  /**
   * Represents the frozen state of the animation, storing whether it's frozen and the time it was
   * frozen at.
   */
  data class FrozenState(val isFrozen: Boolean = false, val frozenAt: Int = 0)

  /** The current offset (in milliseconds) for shifting the animation timeline. */
  val offset = MutableStateFlow(0)

  /** Represents the current frozen state of the animation. */
  val frozenState = MutableStateFlow(FrozenState(false))

  /** An action to control freezing/unfreezing the animation. */
  private val freezeAction = FreezeAction(getCurrentTime, frozenState, tracker)

  /** Abstract property representing the state manager for this animation type. */
  abstract val animationState: AnimationState<*>

  /** Animation [Transition]. Could be empty for unsupported or not yet loaded transitions. */
  private var currentTransition = Transition()

  /** Callback when [animatedPropertiesAtCurrentTime] has been changed. */
  private var animatedPropertiesChangedCallback: (List<AnimationUnit.TimelineUnit>) -> Unit = {}

  /**
   * Current values of the animation's properties at the present time within the timeline.
   *
   * This list is dynamically updated whenever:
   * - The timeline slider is moved.
   * - The state of the animation changes (e.g., playing, paused, etc.).
   *
   * Note: This list may be empty if:
   * - The animation transition is not yet loaded.
   * - The animation type does not support property manipulation.
   */
  var animatedPropertiesAtCurrentTime = listOf<AnimationUnit.TimelineUnit>()
    protected set(value) {
      field = value
      animatedPropertiesChangedCallback(value)
    }

  /** [AnimationCard] for coordination panel. */
  final override lateinit var card: AnimationCard

  val tab by lazy {
    AnimationTab(rootComponent, playbackControls, animationState.changeStateActions, freezeAction)
  }
  override val timelineMaximumMs: Int
    get() = currentTransition.endMillis ?: 0

  override fun createTimelineElement(
    parent: JComponent,
    minY: Int,
    forIndividualTab: Boolean,
    positionProxy: PositionProxy,
  ): TimelineElement {
    val timelineElement =
      if (card.expanded.value || forIndividualTab) {
        val curve =
          TransitionCurve.create(frozenState.value, currentTransition, minY, positionProxy)
        animatedPropertiesChangedCallback = { curve.timelineUnits = it }
        curve.timelineUnits = animatedPropertiesAtCurrentTime
        curve
      } else
        TimelineLine(
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
    return timelineElement
  }

  protected abstract fun loadTransitionFromLibrary(): Transition

  /** Load transition for current animation state. */
  private suspend fun loadTransition(longTimeout: Boolean = false) {
    executeInRenderSession(longTimeout) { currentTransition = loadTransitionFromLibrary() }
  }

  abstract suspend fun loadAnimatedPropertiesAtCurrentTime(longTimeout: Boolean)

  override suspend fun destroy() {
    scope.cancel("AnimationManager is destroyed")
  }

  /** Initializes the state of the Compose animation before it starts */
  final override suspend fun setup() {
    setupInitialAnimationState()

    // To make sure that we load everything at least once before exiting the setup.
    syncState()

    withContext(uiThread) {
      card =
        AnimationCard(
            rootComponent,
            tabTitle,
            listOf(freezeAction) + animationState.changeStateActions,
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
                    setText(this@SupportedAnimationManager.tabTitle)
                    tabbedPane.addTabWithCloseButton(this) { tabInfo = null }
                  }
              }
              tabInfo?.let { tabbedPane.select(it, true) }
            }
            this.addOpenInTabListener { addTabToPane() }
          }
    }

    // Launch coroutines to handle state changes
    scope.launch { animationState.state.collect { syncState() } }
    scope.launch { frozenState.collect { updateTimelineElementsCallback() } }
    scope.launch { card.expanded.collect { updateTimelineElementsCallback() } }
  }

  private suspend fun syncState() {
    syncAnimationWithState()
    loadTransition()
    loadAnimatedPropertiesAtCurrentTime(false)
    updateTimelineElementsCallback()
  }

  /**
   * This method is called when the animation state changes. It should be overridden by subclasses
   * to perform any necessary synchronization between the animation and its state.
   */
  abstract suspend fun syncAnimationWithState()

  /** This method is called during the setup process to initialize the animation state. */
  abstract suspend fun setupInitialAnimationState()
}

private fun CoroutineScope.createChildScope(name: String) =
  CoroutineScope(
    this.coroutineContext +
      SupervisorJob(coroutineContext[Job]) +
      CoroutineName("AnimationManager.$name")
  )
