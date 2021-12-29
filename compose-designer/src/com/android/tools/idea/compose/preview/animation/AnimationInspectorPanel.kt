/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation

import androidx.compose.animation.tooling.ComposeAnimatedProperty
import androidx.compose.animation.tooling.ComposeAnimation
import androidx.compose.animation.tooling.ComposeAnimationType
import androidx.compose.animation.tooling.TransitionInfo
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.util.ControllableTicker
import com.android.tools.idea.compose.preview.analytics.AnimationToolingEvent
import com.android.tools.idea.compose.preview.analytics.AnimationToolingUsageTracker
import com.android.tools.idea.compose.preview.animation.AnimationInspectorPanel.TransitionDurationTimeline
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.layoutlibSceneManagers
import com.android.tools.idea.flags.StudioFlags.COMPOSE_INTERACTIVE_ANIMATION_CURVES
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.ComposeAnimationToolingEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.AnActionButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import java.awt.geom.Path2D
import java.time.Duration
import java.util.Dictionary
import java.util.Hashtable
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.border.MatteBorder
import kotlin.math.ceil
import kotlin.math.max

private val LOG = Logger.getInstance(AnimationInspectorPanel::class.java)

/**
 * Height of the animation inspector timeline header, i.e. Transition Properties panel title and timeline labels.
 */
private const val TIMELINE_HEADER_HEIGHT = 25

/**
 * Height of the animation inspector footer.
 */
private const val TIMELINE_FOOTER_HEIGHT = 20

/**
 * Default max duration (ms) of the animation preview when it's not possible to get it from Compose.
 */
private const val DEFAULT_MAX_DURATION_MS = 10000L

/** Height of one row for animation. */
private const val TIMELINE_ROW_HEIGHT = 70

/** Offset between animation curves. */
private const val TIMELINE_CURVE_OFFSET = 45

/** Offset from the top of timeline to the first animation curve. */
private const val TIMELINE_TOP_OFFSET = 20

/** Offset between the curve and the label. */
private const val LABEL_OFFSET = 10


//TODO(b/161344747) This value could be dynamic depending on the curve type.
/** Number of points for one curve. */
private const val DEFAULT_CURVE_POINTS_NUMBER = 200

/**
 * Displays details about animations belonging to a Compose Preview. Allows users to see all the properties (e.g. `ColorPropKeys`) being
 * animated grouped by animation (e.g. `TransitionAnimation`, `AnimatedValue`). In addition, [TransitionDurationTimeline] is a timeline view
 * that can be controlled by scrubbing or through a set of controllers, such as play/pause and jump to end. The [AnimationInspectorPanel]
 * therefore allows a detailed inspection of Compose animations.
 */
class AnimationInspectorPanel(internal val surface: DesignSurface) : JPanel(TabularLayout("Fit,*", "Fit,*")), Disposable {

  /**
   * Animation transition for selected from/to states.
   * @param properties - map of properties for this Animation, it maps the index of the property to an [AnimatedProperty].
   */
  class Transition(val properties: Map<Int, AnimatedProperty<Double>?> = mutableMapOf()) {
    private val numberOfSubcomponents by lazy {
      properties.map { it.value?.dimension }.filterNotNull().sum()
    }
    val transitionTimelineHeight by lazy {
      numberOfSubcomponents * TIMELINE_ROW_HEIGHT + TIMELINE_HEADER_HEIGHT + TIMELINE_FOOTER_HEIGHT
    }
  }

  val logger = { type: ComposeAnimationToolingEvent.ComposeAnimationToolingEventType -> logAnimationInspectorEvent(type) }

  /**
   * Tabs panel where each tab represents a single animation being inspected. All tabs share the same [TransitionDurationTimeline], but have
   * their own playback toolbar, from/to state combo boxes and animated properties panel.
   */
  @VisibleForTesting
  val tabbedPane = AnimationTabs(surface).apply {
    addListener(TabChangeListener())
  }

  private inner class TabChangeListener : TabsListener {
    override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
      val tab = tabbedPane.selectedInfo?.component as? AnimationTab ?: return
      if (newSelection == oldSelection) return
      // Load animation when first tab was just created or transition has changed.
      tab.loadTransitionFromCacheOrLib()
      tab.updateProperties()
      // The following callbacks only need to be called when old selection is not null, which excludes the addition/selection of the first
      // tab. In that case, the logic will be handled by updateTransitionStates.
      if (oldSelection != null) {
        // Swing components cannot be placed into different containers, so we add the shared timeline to the active tab on tab change.
        tab.addTimeline()
        timeline.selectedTab = tab
        // Set the clock time when changing tabs to update the current tab's transition properties panel.
        timeline.setClockTime(timeline.cachedVal)
      }
    }
  }

  /**
   * Maps animation objects to the [AnimationTab] that represents them.
   */
  private val animationTabs = HashMap<ComposeAnimation, AnimationTab>()

  /**
   * [tabbedPane]'s tab titles mapped to the amount of tabs using that title. The count is used to differentiate tabs when there are
   * multiple tabs with the same name. For example, we should have "tabTitle", "tabTitle (1)", "tabTitle (2)", etc., instead of multiple
   * "tabTitle" tabs.
   */
  private val tabNamesCount = HashMap<String, Int>()

  /**
   * Loading panel displayed when the preview has no animations subscribed.
   */
  private val noAnimationsPanel = JBLoadingPanel(BorderLayout(), this).apply {
    name = "Loading Animations Panel"
    setLoadingText(message("animation.inspector.loading.animations.panel.message"))
  }

  private val timeline = TransitionDurationTimeline()

  private val playPauseAction = PlayPauseAction()

  private val timelineSpeedAction = TimelineSpeedAction()

  private val timelineLoopAction = TimelineLoopAction()

  /**
   * Wrapper of the `PreviewAnimationClock` that animations inspected in this panel are subscribed to. Null when there are no animations.
   */
  internal var animationClock: AnimationClock? = null

  private var maxDurationPerIteration = DEFAULT_MAX_DURATION_MS

  /**
   * Executor responsible for updating animation states off EDT.
   */
  private val updateAnimationStatesExecutor =
    if (ApplicationManager.getApplication().isUnitTestMode)
      MoreExecutors.directExecutor()
    else
      AppExecutorUtil.createBoundedApplicationPoolExecutor("Animation States Updater", 1)

  init {
    name = "Animation Preview"

    noAnimationsPanel.startLoading()
    add(noAnimationsPanel, TabularLayout.Constraint(1, 0, 2))
  }

  /**
   * Updates the `from` and `to` state combo boxes to display the states of the given animation, and resets the timeline. Invokes a given
   * callback once everything is populated.
   */
  fun updateTransitionStates(animation: ComposeAnimation, states: Set<Any>, callback: () -> Unit) {
    animationTabs[animation]?.let { tab ->
      tab.stateComboBox.updateStates(states)
      val transition = animation.animationObject
      transition::class.java.methods.singleOrNull { it.name == "getCurrentState" }?.let {
        it.isAccessible = true
        it.invoke(transition)?.let { state ->
          tab.stateComboBox.setStartState(state)
        }
      }

      // Call updateAnimationStartAndEndStates directly here to set the initial animation states in PreviewAnimationClock
      updateAnimationStatesExecutor.execute {
        // Use a longer timeout the first time we're updating the start and end states. Since we're running off EDT, the UI will not freeze.
        // This is necessary here because it's the first time the animation mutable states will be written, when setting the clock, and
        // read, when getting its duration. These operations take longer than the default 30ms timeout the first time they're executed.
        tab.updateAnimationStartAndEndStates(longTimeout = true)
        // Set up the combo box listeners so further changes to the selected state will trigger a call to updateAnimationStartAndEndStates.
        // Note: this is called only once per tab, in this method, when creating the tab.
        tab.stateComboBox.setupListeners()
        callback.invoke()
      }
    }
  }

  /**
   * Updates the combo box that displays the possible states of an `AnimatedVisibility` animation, and resets the timeline. Invokes a given
   * callback once the combo box is populated.
   */
  fun updateAnimatedVisibilityStates(animation: ComposeAnimation, callback: () -> Unit) {
    animationTabs[animation]?.let { tab ->
      tab.stateComboBox.updateStates(animation.states)

      updateAnimationStatesExecutor.execute {
        // Update the animated visibility combo box with the correct initial state, obtained from PreviewAnimationClock.
        var state: Any? = null
        executeOnRenderThread(useLongTimeout = true) {
          val clock = animationClock ?: return@executeOnRenderThread
          // AnimatedVisibilityState is an inline class in Compose that maps to a String. Therefore, calling `getAnimatedVisibilityState`
          // via reflection will return a String rather than an AnimatedVisibilityState. To work around that, we select the initial combo
          // box item by checking the display value.
          state = clock.getAnimatedVisibilityStateFunction.invoke(clock.clock, animation)
        }
        tab.stateComboBox.setStartState(state)

        // Use a longer timeout the first time we're updating the AnimatedVisiblity state. Since we're running off EDT, the UI will not
        // freeze. This is necessary here because it's the first time the animation mutable states will be written, when setting the clock,
        // and read, when getting its duration. These operations take longer than the default 30ms timeout the first time they're executed.
        tab.updateAnimatedVisibility(longTimeout = true)
        // Set up the combo box listener so further changes to the selected state will trigger a call to updateAnimatedVisibility.
        // Note: this is called only once per tab, in this method, when creating the tab.
        tab.stateComboBox.setupListeners()
        callback.invoke()
      }
    }
  }

  /**
   * Update the timeline window size, which is usually the duration of the longest animation being tracked. However, repeatable animations
   * are handled differently because they can have a large number of iterations resulting in a unrealistic duration. In that case, we take
   * the longest iteration instead to represent the window size and set the timeline max loop count to be large enough to display all the
   * iterations.
   */
  fun updateTimelineWindowSize(longTimeout: Boolean = false) {
    val clock = animationClock ?: return

    if (!executeOnRenderThread(longTimeout) {
        maxDurationPerIteration = clock.getMaxDurationPerIteration.invoke(clock.clock) as Long
      }) return
    timeline.updateMaxDuration(maxDurationPerIteration)

    var maxDuration = DEFAULT_MAX_DURATION_MS
    if (!executeOnRenderThread(longTimeout) { maxDuration = clock.getMaxDurationFunction.invoke(clock.clock) as Long }) return

    timeline.maxLoopCount = if (maxDuration > maxDurationPerIteration) {
      // The max duration is longer than the max duration per iteration. This means that a repeatable animation has multiple iterations,
      // so we need to add as many loops to the timeline as necessary to display all the iterations.
      ceil(maxDuration / maxDurationPerIteration.toDouble()).toLong()
    }
    // Otherwise, the max duration fits the window, so we just need one loop that keeps repeating when loop mode is active.
    else 1
  }

  /**
   * Remove all tabs from [tabbedPane], replace it with [noAnimationsPanel], and clears the cached animations.
   */
  internal fun invalidatePanel() {
    tabbedPane.removeAllTabs()
    animationTabs.clear()
    showNoAnimationsPanel()
  }

  /**
   * Replaces the [tabbedPane] with [noAnimationsPanel].
   */
  private fun showNoAnimationsPanel() {
    remove(tabbedPane.component)
    noAnimationsPanel.startLoading()
    add(noAnimationsPanel, TabularLayout.Constraint(1, 0, 2))
    // Reset tab names, so when new tabs are added they start as #1
    tabNamesCount.clear()
    timeline.cachedVal = -1 // Reset the timeline cached value, so when new tabs are added, any new value will trigger an update
    // The animation panel might not have the focus when the "No animations" panel is displayed, i.e. when a live literal is changed in the
    // editor and we need to refresh the animation preview so it displays the most up-to-date animations. For that reason, we need to make
    // sure the animation panel is repainted correctly.
    repaint()
    playPauseAction.pause()
  }

  /**
   * Adds an [AnimationTab] corresponding to the given [animation] to [tabbedPane].
   */
  internal fun addTab(animation: ComposeAnimation) {
    val animationTab = animationTabs[animation] ?: return

    val isAddingFirstTab = tabbedPane.tabCount == 0
    tabbedPane.addTab(TabInfo(animationTab).apply {
      text = animationTab.tabTitle
    })
    if (isAddingFirstTab) {
      // There are no tabs and we're about to add one. Replace the placeholder panel with the TabbedPane.
      noAnimationsPanel.stopLoading()
      remove(noAnimationsPanel)
      add(tabbedPane.component, TabularLayout.Constraint(1, 0, 2))
    }
  }

  /**
   * Creates an [AnimationTab] corresponding to the given [animation] and add it to the [animationTabs] map.
   * Note: this method does not add the tab to [tabbedPane]. For that, [addTab] should be used.
   */
  internal fun createTab(animation: ComposeAnimation) {
    val tabName = animation.label
                  ?: when (animation.type) {
                    ComposeAnimationType.ANIMATED_VALUE -> message("animation.inspector.tab.animated.value.default.title")
                    ComposeAnimationType.ANIMATED_VISIBILITY -> message("animation.inspector.tab.animated.visibility.default.title")
                    ComposeAnimationType.TRANSITION_ANIMATION -> message("animation.inspector.tab.transition.animation.default.title")
                    else -> message("animation.inspector.tab.default.title")
                  }
    val count = tabNamesCount.getOrDefault(tabName, 0)
    tabNamesCount[tabName] = count + 1
    val animationTab = AnimationTab(animation, "$tabName${if (count > 0) " ($count)" else ""}")
    if (animationTabs.isEmpty()) {
      // We need to make sure the timeline is added to a tab. Since there are no tabs yet, this will be the chosen one.
      animationTab.addTimeline()
      timeline.selectedTab = animationTab
    }
    animationTabs[animation] = animationTab
  }

  /**
   * Removes the [AnimationTab] corresponding to the given [animation] from [tabbedPane].
   */
  internal fun removeTab(animation: ComposeAnimation) {
    tabbedPane.tabs.find { (it.component as? AnimationTab)?.animation === animation }?.let { tabbedPane.removeTab(it) }
    animationTabs.remove(animation)

    if (tabbedPane.tabCount == 0) {
      // There are no more tabs. Replace the TabbedPane with the placeholder panel.
      showNoAnimationsPanel()
    }
  }

  override fun dispose() {
    playPauseAction.dispose()
    animationTabs.clear()
    tabNamesCount.clear()
  }

  private fun logAnimationInspectorEvent(type: ComposeAnimationToolingEvent.ComposeAnimationToolingEventType) {
    AnimationToolingUsageTracker.getInstance(surface).logEvent(AnimationToolingEvent(type))
  }

  /**
   * Content of a tab representing an animation. All the elements that aren't shared between tabs and need to be exposed should be defined
   * in this class, e.g. from/to state combo boxes.
   */
  private inner class AnimationTab(val animation: ComposeAnimation, val tabTitle: String) : JPanel(TabularLayout("Fit,*,Fit", "Fit,*")) {

    val stateComboBox: InspectorPainter.StateComboBox

    private val timelinePanelWithCurves = JBScrollPane().apply {
      border = MatteBorder(1, 1, 0, 0, JBColor.border())
    }
    private val timelinePanelNoCurves = JPanel(BorderLayout())
    private val cachedTransitions: MutableMap<Int, Transition> = mutableMapOf()
    private val playbackControls = createPlaybackControllers()
    private val playPauseComponent: Component?
      get() = playbackControls.component?.components?.elementAtOrNull(2)

    init {
      add(playbackControls.component, TabularLayout.Constraint(0, 0))
      stateComboBox = when (animation.type) {
        ComposeAnimationType.TRANSITION_ANIMATION -> InspectorPainter.StartEndComboBox(surface, logger) {
          updateAnimationStartAndEndStates()
          loadTransitionFromCacheOrLib()
          updateProperties()
        }
        ComposeAnimationType.ANIMATED_VISIBILITY -> InspectorPainter.AnimatedVisibilityComboBox(logger) {
          updateAnimatedVisibility()
          loadTransitionFromCacheOrLib()
          updateProperties()
        }
        ComposeAnimationType.ANIMATED_VALUE -> InspectorPainter.EmptyComboBox()
      }
      add(stateComboBox.component, TabularLayout.Constraint(0, 2))
      val splitterWrapper = JPanel(BorderLayout())
      if (COMPOSE_INTERACTIVE_ANIMATION_CURVES.get())
        splitterWrapper.add(timelinePanelWithCurves)
      else
        splitterWrapper.add(timelinePanelNoCurves, BorderLayout.CENTER)
      add(splitterWrapper, TabularLayout.Constraint(1, 0, 3))
      isFocusable = false
      focusTraversalPolicy = LayoutFocusTraversalPolicy()
    }

    /**
     * Updates the actual animation in Compose to set its start and end states to the ones selected in the respective combo boxes.
     */
    fun updateAnimationStartAndEndStates(longTimeout: Boolean = false) {
      val clock = animationClock ?: return
      val startState = stateComboBox.getState(0)
      val toState = stateComboBox.getState(1)

      if (!executeOnRenderThread(longTimeout) {
          clock.updateFromAndToStatesFunction.invoke(clock.clock, animation, startState, toState)
        }) return
      resetTimelineAndUpdateWindowSize(longTimeout)
    }

    /**
     * Updates the actual animation in Compose to set its start and end states based on the selected value of [animatedVisibilityComboBox].
     */
    fun updateAnimatedVisibility(longTimeout: Boolean = false) {
      val clock = animationClock ?: return
      if (!executeOnRenderThread(longTimeout) {
          clock.updateAnimatedVisibilityStateFunction.invoke(clock.clock, animation, stateComboBox.getState())
        }) return
      resetTimelineAndUpdateWindowSize(longTimeout)
    }

    private fun resetTimelineAndUpdateWindowSize(longTimeout: Boolean) {
      // Set the timeline to 0
      timeline.setClockTime(0, longTimeout)
      updateTimelineWindowSize(longTimeout)
      // Update the cached value manually to prevent the timeline to set the clock time to 0 using the short timeout.
      timeline.cachedVal = 0
      // Move the timeline slider to 0.
      UIUtil.invokeLaterIfNeeded { timeline.jumpToStart() }
    }

    /**
     * Load transition for current start and end state. If transition was loaded before, the cached result is used.
     */
    fun loadTransitionFromCacheOrLib(longTimeout: Boolean = false) {
      if (!COMPOSE_INTERACTIVE_ANIMATION_CURVES.get()) return

      val stateHash = stateComboBox.stateHashCode()

      cachedTransitions[stateHash]?.let {
        timeline.updateTransition(cachedTransitions[stateHash]!!)
        timelinePanelWithCurves.doLayout()
        return@loadTransitionFromCacheOrLib
      }

      val clock = animationClock ?: return

      executeOnRenderThread(longTimeout) {
        val transition = loadTransitionsFromLib(clock)
        cachedTransitions[stateHash] = transition
        timeline.updateTransition(transition)
        timelinePanelWithCurves.doLayout()
      }
    }

    private fun loadTransitionsFromLib(clock: AnimationClock): Transition {
      val builders: MutableMap<Int, AnimatedProperty.Builder> = mutableMapOf()
      val clockTimeMsStep = max(1, maxDurationPerIteration / DEFAULT_CURVE_POINTS_NUMBER)

      fun getTransitions() {
        val composeTransitions = clock.getTransitionsFunction?.invoke(clock.clock, animation, clockTimeMsStep) as List<TransitionInfo>
        for ((index, composeTransition) in composeTransitions.withIndex()) {
          val builder = AnimatedProperty.Builder()
            .setStartTimeMs(composeTransition.startTimeMillis.toInt())
            .setEndTimeMs(composeTransition.endTimeMillis.toInt())
          composeTransition.values.mapValues {
            ComposeUnit.parseValue(it.value)
          }.forEach { (ms, unit) ->
            unit?.let {
              builder.add(ms.toInt(), unit)
            }
          }
          builders[index] = builder
        }
      }

      fun getAnimatedProperties() {
        for (clockTimeMs in 0..maxDurationPerIteration step clockTimeMsStep) {
          clock.setClockTimeFunction.invoke(clock.clock, clockTimeMs)
          val properties = clock.getAnimatedPropertiesFunction.invoke(clock.clock, animation) as List<ComposeAnimatedProperty>
          for ((index, property) in properties.withIndex()) {
            ComposeUnit.parse(property)?.let { unit ->
              builders.getOrPut(index) { AnimatedProperty.Builder() }.add(clockTimeMs.toInt(), unit)
            }
          }
        }
      }

      try {
        if (clock.getTransitionsFunction != null) getTransitions()
        else getAnimatedProperties()

      }
      catch (e: Exception) {
        LOG.warn("Failed to load the Compose Animation properties", e)
      }

      builders.mapValues { it.value.build() }.let {
        return Transition(it)
      }
    }

    /**
     * Create a toolbar panel with actions to control the animation, e.g. play, pause and jump to start/end.
     *
     * TODO(b/157895086): Update action icons when we have the final Compose Animation tooling icons
     * TODO(b/157895086): Disable toolbar actions while build is in progress
     */
    private fun createPlaybackControllers() = ActionManager.getInstance().createActionToolbar(
      "Animation Preview",
      DefaultActionGroup(listOf(
        timelineLoopAction,
        GoToStartAction(),
        playPauseAction,
        GoToEndAction(),
        timelineSpeedAction
      )),
      true).apply {
      setTargetComponent(surface)
      ActionToolbarUtil.makeToolbarNavigable(this)
    }

    /**
     * Adds [timeline] to this tab's [timelinePanel]. The timeline is shared across all tabs, and a Swing component can't be added as a
     * child of multiple components simultaneously. Therefore, this method needs to be called everytime we change tabs.
     */
    fun addTimeline() {
      if (COMPOSE_INTERACTIVE_ANIMATION_CURVES.get()) {
        timelinePanelWithCurves.setViewportView(timeline)
        timelinePanelWithCurves.doLayout()
      }
      else timelinePanelNoCurves.add(timeline)
    }

    fun updateProperties() {
      val animClock = animationClock ?: return
      if (!COMPOSE_INTERACTIVE_ANIMATION_CURVES.get()) return
      try {
        val properties = animClock.getAnimatedPropertiesFunction.invoke(animClock.clock, animation) as List<ComposeAnimatedProperty>
        timeline.updateSelectedProperties(properties.map { ComposeUnit.TimelineUnit(it, ComposeUnit.parse(it)) })
      }
      catch (e: Exception) {
        LOG.warn("Failed to get the Compose Animation properties", e)
      }
    }

    /**
     * Snap the animation to the start state.
     */
    private inner class GoToStartAction
      : AnActionButton(message("animation.inspector.action.go.to.start"), StudioIcons.LayoutEditor.Motion.GO_TO_START) {
      override fun actionPerformed(e: AnActionEvent) {
        timeline.jumpToStart()
        logAnimationInspectorEvent(ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.TRIGGER_JUMP_TO_START_ACTION)
        // Switch focus to Play button if animation is not playing at the moment.
        // If animation is playing - no need to switch focus as GoToStart button will be enabled again.
        if (!playPauseAction.isPlaying)
          playPauseComponent?.requestFocus()
      }

      override fun updateButton(e: AnActionEvent) {
        super.updateButton(e)
        e.presentation.isEnabled = !timeline.isAtStart()
      }
    }

    /**
     * Snap the animation to the end state.
     */
    private inner class GoToEndAction
      : AnActionButton(message("animation.inspector.action.go.to.end"), StudioIcons.LayoutEditor.Motion.GO_TO_END) {
      override fun actionPerformed(e: AnActionEvent) {
        timeline.jumpToEnd()
        logAnimationInspectorEvent(ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.TRIGGER_JUMP_TO_END_ACTION)
        // Switch focus to Play button if animation is not playing in the loop at the moment.
        // If animation is playing in the loop - no need to switch focus as GoToEnd button will be enabled again.
        if (!playPauseAction.isPlaying || !timeline.playInLoop)
          playPauseComponent?.requestFocus()
      }

      override fun updateButton(e: AnActionEvent) {
        super.updateButton(e)
        e.presentation.isEnabled = !timeline.isAtEnd()
      }
    }
  }

  /**
   * Action to play and pause the animation. The icon and tooltip gets updated depending on the playing state.
   */
  private inner class PlayPauseAction : AnActionButton(message("animation.inspector.action.play"), StudioIcons.LayoutEditor.Motion.PLAY) {
    private val tickPeriod = Duration.ofMillis(30)

    /**
     *  Ticker that increment the animation timeline while it's playing.
     */
    private val ticker =
      ControllableTicker({
                           if (isPlaying) {
                             UIUtil.invokeLaterIfNeeded { timeline.incrementClockBy(tickPeriod.toMillis().toInt()) }
                             if (timeline.isAtEnd()) {
                               if (timeline.playInLoop) {
                                 handleLoopEnd()
                               }
                               else {
                                 pause()
                               }
                             }
                           }
                         }, tickPeriod)

    var isPlaying = false
      private set

    override fun actionPerformed(e: AnActionEvent) = if (isPlaying) {
      pause()
      logAnimationInspectorEvent(ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.TRIGGER_PAUSE_ACTION)
    }
    else {
      play()
      logAnimationInspectorEvent(ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.TRIGGER_PLAY_ACTION)
    }

    override fun updateButton(e: AnActionEvent) {
      super.updateButton(e)
      e.presentation.isEnabled = true
      e.presentation.apply {
        if (isPlaying) {
          icon = StudioIcons.LayoutEditor.Motion.PAUSE
          text = message("animation.inspector.action.pause")
        }
        else {
          icon = StudioIcons.LayoutEditor.Motion.PLAY
          text = message("animation.inspector.action.play")
        }
      }
    }

    private fun play() {
      if (timeline.isAtEnd()) {
        // If playing after reaching the timeline end, we should go back to start so the animation can be actually played.
        timeline.jumpToStart()
      }
      isPlaying = true
      ticker.start()
    }

    fun pause() {
      isPlaying = false
      ticker.stop()
    }

    private fun handleLoopEnd() {
      UIUtil.invokeLaterIfNeeded { timeline.jumpToStart() }
      timeline.loopCount++
      if (timeline.loopCount == timeline.maxLoopCount) {
        timeline.loopCount = 0
      }
    }

    fun dispose() {
      ticker.dispose()
    }
  }

  private enum class TimelineSpeed(val speedMultiplier: Float, val displayText: String) {
    X_0_1(0.1f, "0.1x"),
    X_0_25(0.25f, "0.25x"),
    X_0_5(0.5f, "0.5x"),
    X_0_75(0.75f, "0.75x"),
    X_1(1f, "1x"),
    X_2(2f, "2x")
  }

  /**
   * Action to speed up or slow down the timeline. The clock runs faster/slower depending on the value selected.
   *
   * TODO(b/157895086): Add a proper icon for the action.
   */
  private inner class TimelineSpeedAction : DropDownAction(message("animation.inspector.action.speed"),
                                                           message("animation.inspector.action.speed"),
                                                           null) {

    init {
      enumValues<TimelineSpeed>().forEach { addAction(SpeedAction(it)) }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.text = timeline.speed.displayText
    }

    override fun displayTextInToolbar() = true

    private inner class SpeedAction(private val speed: TimelineSpeed) : ToggleAction("${speed.displayText}", "${speed.displayText}", null) {
      override fun isSelected(e: AnActionEvent) = timeline.speed == speed

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        timeline.speed = speed
        val changeSpeedEvent = AnimationToolingEvent(ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.CHANGE_ANIMATION_SPEED)
          .withAnimationMultiplier(speed.speedMultiplier)
        AnimationToolingUsageTracker.getInstance(surface).logEvent(changeSpeedEvent)
      }
    }
  }

  /**
   * Action to keep the timeline playing in loop. When active, the timeline will keep playing indefinitely instead of stopping at the end.
   * When reaching the end of the window, the timeline will increment the loop count until it reaches its limit. When that happens, the
   * timelines jumps back to start.
   *
   * TODO(b/157895086): Add a proper icon for the action.
   */
  private inner class TimelineLoopAction : ToggleAction(message("animation.inspector.action.loop"),
                                                        message("animation.inspector.action.loop"),
                                                        StudioIcons.LayoutEditor.Motion.LOOP) {

    override fun isSelected(e: AnActionEvent) = timeline.playInLoop

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      timeline.playInLoop = state
      if (!state) {
        // Reset the loop when leaving playInLoop mode.
        timeline.loopCount = 0
      }
      logAnimationInspectorEvent(
        if (state) ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.ENABLE_LOOP_ACTION
        else ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.DISABLE_LOOP_ACTION
      )
    }
  }

  /**
   *  Timeline panel ranging from 0 to the max duration (in ms) of the animations being inspected, listing all the animations and their
   *  corresponding range as well. The timeline should respond to mouse commands, allowing users to jump to specific points, scrub it, etc.
   */
  private inner class TransitionDurationTimeline : JPanel(BorderLayout()) {

    var selectedTab: AnimationTab? = null
    var cachedVal = -1

    /**
     * Speed multiplier of the timeline clock. [TimelineSpeed.X_1] by default (normal speed).
     */
    var speed: TimelineSpeed = TimelineSpeed.X_1

    /**
     * Whether the timeline should play in loop or stop when reaching the end.
     */
    var playInLoop = false

    /**
     * 0-based count representing the current loop the timeline is in. This should be used as a multiplier of the |windowSize| (slider
     * maximum) offset applied when setting the clock time.
     */
    var loopCount = 0L

    /**
     * The maximum amount of loops the timeline has. When [loopCount] reaches this value, it needs to be reset.
     */
    var maxLoopCount = 1L

    override fun getPreferredSize(): Dimension {
      return Dimension(width - 50, transition.transitionTimelineHeight)
    }


    private val slider = object : JSlider(0, DEFAULT_MAX_DURATION_MS.toInt(), 0) {
      private var cachedSliderWidth = 0
      private var cachedMax = 0
      override fun updateUI() {
        setUI(TimelineSliderUI(this))
        updateLabelUIs()
      }

      override fun setMaximum(maximum: Int) {
        super.setMaximum(maximum)
        updateMajorTicks()
      }

      fun updateMajorTicks() {
        if (width == cachedSliderWidth && maximum == cachedMax) return
        cachedSliderWidth = width
        cachedMax = maximum
        val tickIncrement = InspectorPainter.Slider.getTickIncrement(this)
        // First, calculate where the labels are going to be painted, based on the maximum. We won't paint the major ticks themselves, as
        // minor ticks will be painted instead. The major ticks spacing is only set so the labels are painted in the right place.
        setMajorTickSpacing(tickIncrement)
        // Now, add the "ms" suffix to each label.
        labelTable = if (tickIncrement == 0) {
          // Handle the special case where maximum == 0 and we only have the "0ms" label.
          createMsLabelTable(labelTable)
        }
        else {
          createMsLabelTable(createStandardLabels(tickIncrement))
        }
      }

    }.apply {
      paintTicks = false
      paintLabels = true
      updateMajorTicks()
      setUI(TimelineSliderUI(this))
      addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) = updateMajorTicks()
      })
    }

    init {
      add(slider, BorderLayout.CENTER)
      slider.addChangeListener {
        if (slider.value == cachedVal) return@addChangeListener // Ignore repeated values
        val newValue = slider.value
        cachedVal = newValue
        setClockTime(newValue)
      }
    }

    var transition = Transition()

    /**
     * List of [ComposeUnit.TimelineUnit] for the clockTime set in [AnimationClock].
     * Corresponds to the selected time in the timeline.
     */
    var selectedProperties: List<ComposeUnit.TimelineUnit> = mutableListOf()

    /**
     * Update currently selected transition.
     */
    fun updateTransition(newTransition: Transition) {
      transition = newTransition
      preferredSize = Dimension(width - 50, transition.transitionTimelineHeight)
      repaint()
    }

    /**
     * Update currently selected properties in the timeline.
     */
    fun updateSelectedProperties(animatedPropKeys: List<ComposeUnit.TimelineUnit>) {
      selectedProperties = animatedPropKeys
    }

    fun updateMaxDuration(durationMs: Long) {
      slider.maximum = durationMs.toInt()
    }

    fun setClockTime(newValue: Int, longTimeout: Boolean = false) {
      val clock = animationClock ?: return
      val tab = selectedTab ?: return

      var clockTimeMs = newValue.toLong()
      if (playInLoop) {
        // When playing in loop, we need to add an offset to slide the window and take repeatable animations into account when necessary
        clockTimeMs += slider.maximum * loopCount
      }

      if (!executeOnRenderThread(longTimeout) { clock.setClockTimeFunction.invoke(clock.clock, clockTimeMs) }) return
      tab.updateProperties()
    }

    /**
     * Increments the clock by the given value, taking the current [speed] into account.
     */
    fun incrementClockBy(increment: Int) {
      slider.value += (increment * speed.speedMultiplier).toInt()
    }

    fun jumpToStart() {
      slider.value = 0
    }

    fun jumpToEnd() {
      slider.value = slider.maximum
    }

    fun isAtStart() = slider.value == 0

    fun isAtEnd() = slider.value == slider.maximum

    /**
     * Rewrite the labels by adding a `ms` suffix indicating the values are in milliseconds.
     */
    private fun createMsLabelTable(table: Dictionary<*, *>): Hashtable<Any, JBLabel> {
      val keys = table.keys()
      val labelTable = Hashtable<Any, JBLabel>()
      while (keys.hasMoreElements()) {
        val key = keys.nextElement()
        labelTable[key] = object : JBLabel("$key ms") {
          // Setting the enabled property to false is not enough because BasicSliderUI will check if the slider itself is enabled when
          // painting the labels and set the label enable status to match the slider's. Thus, we force the label color to the disabled one.
          override fun getForeground() = UIUtil.getLabelDisabledForeground()
        }
      }
      return labelTable
    }

    /**
     * Modified [JSlider] UI to simulate a timeline-like view. In general lines, the following modifications are made:
     *   * The horizontal track is hidden, so only the vertical thumb is shown
     *   * The vertical thumb is a vertical line that matches the parent height
     *   * The tick lines also match the parent height
     */
    private inner class TimelineSliderUI(slider: JSlider) : TimelinePanel(slider) {
      fun createCurveInfo(animation: AnimatedProperty<Double>, componentId: Int, minY: Int, maxY: Int): InspectorPainter.CurveInfo? =
        animation.components[componentId].let { component ->
          val curve: Path2D = Path2D.Double()
          val animationYMin = component.minValue
          val isZeroDuration = animation.endMs == animation.startMs
          val zeroDurationXOffset = if (isZeroDuration) 1 else 0
          val minX = xPositionForValue(animation.startMs)
          val maxX = xPositionForValue(animation.endMs)
          val stepY = (maxY - minY) / (component.maxValue - animationYMin)
          curve.moveTo(minX.toDouble() - zeroDurationXOffset, maxY.toDouble())
          if (isZeroDuration) {
            // If animation duration is zero, for example for snap animation - draw a vertical line,
            // It gives a visual feedback what animation is happened at that point and what graph is not missing where.
            curve.lineTo(minX.toDouble() - zeroDurationXOffset, minY.toDouble())
            curve.lineTo(maxX.toDouble() + zeroDurationXOffset, minY.toDouble())
          }
          else {
            component.points.forEach { (ms, value) ->
              curve.lineTo(xPositionForValue(ms).toDouble(), maxY - (value.toDouble() - animationYMin) * stepY)
            }
          }
          curve.lineTo(maxX.toDouble() + zeroDurationXOffset, maxY.toDouble())
          curve.lineTo(minX.toDouble() - zeroDurationXOffset, maxY.toDouble())

          return InspectorPainter.CurveInfo(minX = minX, maxX = maxX, y = maxY, curve = curve, linkedToNextCurve = component.linkToNext)
        }

      override fun paintTrack(g: Graphics) {
        super.paintTrack(g)
        g as Graphics2D
        // Leave the track empty if feature is not enabled
        if (!COMPOSE_INTERACTIVE_ANIMATION_CURVES.get()) return
        if (selectedProperties.isEmpty()) return
        var rowIndex = 0
        for ((index, animation) in transition.properties) {
          if (animation == null) continue
          for (componentId in 0 until animation.dimension) {
            val minY = TIMELINE_HEADER_HEIGHT - 1 + TIMELINE_ROW_HEIGHT * rowIndex + TIMELINE_TOP_OFFSET
            val maxY = minY + TIMELINE_ROW_HEIGHT
            val curveInfo = createCurveInfo(animation, componentId, minY, (maxY - TIMELINE_CURVE_OFFSET))
            if (curveInfo != null)
              InspectorPainter.paintCurve(g, curveInfo, index, TIMELINE_ROW_HEIGHT)

            if (selectedProperties.size > index) {
              InspectorPainter.BoxedLabel.paintBoxedLabel(g, selectedProperties[index], componentId, animation.grouped,
                                                          xPositionForValue(animation.startMs),
                                                          maxY - TIMELINE_CURVE_OFFSET + LABEL_OFFSET)
            }
            rowIndex++
          }
        }
        return
      }

      override fun createTrackListener(slider: JSlider) = TimelineTrackListener()

      /**
       * [Tracklistener] to allow setting [slider] value when clicking and scrubbing the timeline.
       */
      private inner class TimelineTrackListener : TrackListener() {

        private var isDragging = false

        override fun mousePressed(e: MouseEvent) {
          // We override the parent class behavior completely because it executes more operations than we need, being less performant than
          // this method. Since it recalculates the geometry of all components, the resulting UI on mouse press is not what we aim for.
          currentMouseX = e.getX()
          updateThumbLocationAndSliderValue()
          timeline.requestFocus() // Request focus to the timeline, so the selected tab actually gets the focus
        }

        override fun mouseDragged(e: MouseEvent) {
          super.mouseDragged(e)
          updateThumbLocationAndSliderValue()
          isDragging = true
        }

        override fun mouseReleased(e: MouseEvent?) {
          super.mouseReleased(e)
          logAnimationInspectorEvent(
            if (isDragging) ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.DRAG_ANIMATION_INSPECTOR_TIMELINE
            else ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.CLICK_ANIMATION_INSPECTOR_TIMELINE
          )
          isDragging = false
        }

        fun updateThumbLocationAndSliderValue() {
          val halfWidth = thumbRect.width / 2
          // Make sure the thumb X coordinate is within the slider's min and max. Also, subtract half of the width so the center is aligned.
          val thumbX = Math.min(Math.max(currentMouseX, xPositionForValue(slider.minimum)), xPositionForValue(slider.maximum)) - halfWidth
          setThumbLocation(thumbX, thumbRect.y)
          slider.value = valueForXPosition(currentMouseX)
        }
      }
    }
  }

  private fun executeOnRenderThread(useLongTimeout: Boolean, callback: () -> Unit): Boolean {
    val (time, timeUnit) = if (useLongTimeout) {
      // Make sure we don't block the UI thread when setting a large timeout
      ApplicationManager.getApplication().assertIsNonDispatchThread()
      5L to TimeUnit.SECONDS
    }
    else {
      30L to TimeUnit.MILLISECONDS
    }
    return surface.layoutlibSceneManagers.singleOrNull()?.executeCallbacksAndRequestRender(time, timeUnit) {
      callback()
    } ?: false
  }
}