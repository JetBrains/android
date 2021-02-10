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
import com.android.flags.ifEnabled
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.util.ControllableTicker
import com.android.tools.idea.compose.preview.actions.CloseAnimationInspectorAction
import com.android.tools.idea.compose.preview.analytics.AnimationToolingEvent
import com.android.tools.idea.compose.preview.analytics.AnimationToolingUsageTracker
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.layoutlibSceneManagers
import com.android.tools.idea.flags.StudioFlags.COMPOSE_INTERACTIVE_ANIMATION_SWITCH
import com.android.utils.HtmlBuilder
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.ComposeAnimationToolingEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.AnActionButton
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.JBTabsPaneImpl
import com.intellij.ui.TabbedPane
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.ActionListener
import java.awt.event.MouseEvent
import java.time.Duration
import java.util.Dictionary
import java.util.Hashtable
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JTabbedPane.TOP
import javax.swing.border.MatteBorder
import javax.swing.plaf.basic.BasicSliderUI
import javax.swing.text.DefaultCaret
import kotlin.math.ceil

private val LOG = Logger.getInstance(AnimationInspectorPanel::class.java)

/**
 * Height of the animation inspector timeline header, i.e. "Prop Keys" panel title and timeline labels.
 */
private const val TIMELINE_HEADER_HEIGHT = 25

/**
 * Half width of the shape used as the handle of the timeline scrubber.
 */
private const val TIMELINE_HANDLE_HALF_WIDTH = 5

/**
 * Half height of the shape used as the handle of the timeline scrubber.
 */
private const val TIMELINE_HANDLE_HALF_HEIGHT = 5

/**
 * Default max duration (ms) of the animation preview when it's not possible to get it from Compose.
 */
private const val DEFAULT_MAX_DURATION_MS = 10000L

/**
 * Displays details about animations belonging to a Compose Preview. Allows users to see all the properties (e.g. `ColorPropKeys`) being
 * animated grouped by animation (e.g. `TransitionAnimation`, `AnimatedValue`). In addition, [TransitionDurationTimeline] is a timeline view
 * that can be controlled by scrubbing or through a set of controllers, such as play/pause and jump to end. The [AnimationInspectorPanel]
 * therefore allows a detailed inspection of Compose animations.
 */
class AnimationInspectorPanel(internal val surface: DesignSurface) : JPanel(TabularLayout("Fit,*", "Fit,*")), Disposable {

  /**
   * [TabbedPane] where each tab represents a single animation being inspected. All tabs share the same [TransitionDurationTimeline], but
   * have their own playback toolbar, from/to state combo boxes and animated properties panel.
   */
  @VisibleForTesting
  val tabbedPane = JBTabsPaneImpl(surface.project, TOP, this).apply {
    addChangeListener {
      if (selectedIndex < 0) return@addChangeListener

      (getComponentAt(selectedIndex) as? AnimationTab)?.let { tab ->
        // Swing components cannot be placed into different containers, so we add the shared timeline to the active tab on tab change.
        tab.addTimeline()
        timeline.selectedTab = tab
      }
    }
  }

  /**
   * Maps animation objects to the [AnimationTab] that represents them.
   */
  private val animationTabs = HashMap<ComposeAnimation, AnimationTab>()

  /**
   * [tabbedPane]'s tab titles mapped to the amount of tabs using that title. The count is used to differentiate tabs, which are named
   * "tabTitle #1", "tabTitle #2", etc., instead of multiple "tabTitle" tabs.
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

  init {
    name = "Animation Preview"
    border = MatteBorder(0, 0, 1, 0, JBColor.border())

    noAnimationsPanel.startLoading()

    COMPOSE_INTERACTIVE_ANIMATION_SWITCH.ifEnabled {
      add(createToolBar(), TabularLayout.Constraint(0, 0, 2))
    }
    add(noAnimationsPanel, TabularLayout.Constraint(1, 0, 2))
  }

  private fun createToolBar() = JPanel(BorderLayout()).apply {
    border = MatteBorder(0, 0, 1, 0, JBColor.border())
    isOpaque = false
    val animationsTitle = JBLabel(message("animation.inspector.title")).apply {
      border = JBUI.Borders.empty(5)
    }
    add(animationsTitle, BorderLayout.LINE_START)

    val rightSideActions = ActionManager.getInstance().createActionToolbar(
      "Animation Toolbar Actions",
      DefaultActionGroup(listOf(
        CloseAnimationInspectorAction { surface.sceneManagers.single().model.dataContext }
      )),
      true).component
    add(rightSideActions, BorderLayout.LINE_END)
  }

  /**
   * Updates the `from` and `to` state combo boxes to display the states of the given animation, and resets the timeline.
   */
  fun updateTransitionStates(animation: ComposeAnimation, states: Set<Any>) {
    animationTabs[animation]?.let { tab ->
      tab.isUpdatingAnimationStates = true
      tab.updateStateComboboxes(states.toTypedArray())
      tab.updateAnimationStartAndEndStates()
      tab.endStateComboBox.selectedIndex = 1.coerceIn(0, tab.endStateComboBox.itemCount)
      tab.isUpdatingAnimationStates = false
    }
    timeline.jumpToStart()
    timeline.setClockTime(0) // Make sure that clock time is actually set in case timeline was already in 0.
  }

  /**
   * Remove all tabs from [tabbedPane], replace it with [noAnimationsPanel], and clears the cached animations.
   */
  internal fun invalidatePanel() {
    tabbedPane.removeAll()
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
  }

  /**
   * Adds an [AnimationTab] corresponding to the given [animation] to [tabbedPane].
   */
  internal fun addTab(animation: ComposeAnimation) {
    if (tabbedPane.tabCount == 0) {
      // There are no tabs and we're about to add one. Replace the placeholder panel with the TabbedPane.
      noAnimationsPanel.stopLoading()
      remove(noAnimationsPanel)
      add(tabbedPane.component, TabularLayout.Constraint(1, 0, 2))
    }

    val animationTab = AnimationTab(animation)
    animationTabs[animation] = animationTab
    val tabName = animation.label
                  ?: when (animation.type) {
                    ComposeAnimationType.ANIMATED_VALUE -> message("animation.inspector.tab.animated.value.default.title")
                    ComposeAnimationType.TRANSITION_ANIMATION -> message("animation.inspector.tab.transition.animation.default.title")
                    else -> message("animation.inspector.tab.default.title")
                  }
    tabNamesCount[tabName] = tabNamesCount.getOrDefault(tabName, 0) + 1
    tabbedPane.insertTab("$tabName #${tabNamesCount[tabName]}", null, animationTab, null, tabbedPane.tabCount)
  }

  /**
   * Removes the [AnimationTab] corresponding to the given [animation] from [tabbedPane].
   */
  internal fun removeTab(animation: ComposeAnimation) {
    tabbedPane.remove(animationTabs[animation])
    animationTabs.remove(animation)

    if (tabbedPane.tabCount == 0) {
      // There are no more tabs. Replace the TabbedPane with the placeholder panel.
      showNoAnimationsPanel()
    }
  }

  private fun TabbedPane.remove(animationTab: AnimationTab?) {
    if (animationTab == null) return
    for (i in 0 until tabCount) {
      if (animationTab === getComponentAt(i)) {
        removeTabAt(i)
        return
      }
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
  private inner class AnimationTab(val animation: ComposeAnimation) : JPanel(TabularLayout("Fit,*,Fit", "Fit,*")) {

    /**
     * Listens to [startStateComboBox] changes.
     */
    private val startStateChangeListener = ActionListener {
      if (isSwappingStates) {
        // The is no need to trigger the callback, since we're going to make a follow up call to update the end state.
        // Also, we only log start state changes if not swapping states, which has its own tracking. Therefore, we can early return here.
        return@ActionListener
      }
      if (!isUpdatingAnimationStates) {
        logAnimationInspectorEvent(ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.CHANGE_START_STATE)
      }
      updateAnimationStartAndEndStates()
    }

    /**
     * Listens to [endStateComboBox] changes.
     */
    private val endStateChangeListener = ActionListener {
      if (!isUpdatingAnimationStates && !isSwappingStates) {
        // Only log end state changes if not swapping states, which has its own tracking.
        logAnimationInspectorEvent(ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.CHANGE_END_STATE)
      }
      updateAnimationStartAndEndStates()
    }

    private val startStateComboBox = ComboBox(DefaultComboBoxModel(arrayOf<Any>()))
    val endStateComboBox = ComboBox(DefaultComboBoxModel(arrayOf<Any>()))

    /**
     * Flag to be used when the [SwapStartEndStatesAction] is triggered, in order to prevent the listener to be executed twice.
     */
    private var isSwappingStates = false

    /**
     * Flag to be used when updating the available start and end states, since it might trigger changes in the comboboxes that we don't want
     * to track, as they're not performed by the user.
     */
    var isUpdatingAnimationStates = false

    /**
     * Displays the animated properties and their value at the current timeline time.
     */
    private val animatedPropertiesPanel = AnimatedPropertiesPanel()

    private val timelinePanel = JPanel(BorderLayout())

    /**
     * Horizontal [JBSplitter] comprising of the animated properties panel and the animation timeline.
     */
    private val propertiesTimelineSplitter = JBSplitter(0.2f).apply {
      firstComponent = createAnimatedPropertiesPanel()
      secondComponent = timelinePanel
      dividerWidth = 1
    }

    init {
      add(createPlaybackControllers(), TabularLayout.Constraint(0, 0))
      if (animation.type == ComposeAnimationType.TRANSITION_ANIMATION) {
        add(createAnimationStateComboboxes(), TabularLayout.Constraint(0, 2))
      }
      val splitterWrapper = JPanel(BorderLayout()).apply {
        border = MatteBorder(1, 0, 0, 0, JBColor.border()) // Top border separating the splitter and the playback toolbar
      }
      splitterWrapper.add(propertiesTimelineSplitter, BorderLayout.CENTER)
      add(splitterWrapper, TabularLayout.Constraint(1, 0, 3))
    }

    /**
     * Updates the actual animation in Compose to set its start and end states to the ones selected in the respective combo boxes.
     */
    fun updateAnimationStartAndEndStates() {
      val clock = animationClock ?: return
      val startState = startStateComboBox.selectedItem
      val toState = endStateComboBox.selectedItem

      if (!surface.executeOnRenderThread { clock.updateFromAndToStatesFunction.invoke(clock.clock, animation, startState, toState) }) return

      timeline.jumpToStart()
      timeline.setClockTime(0) // Make sure that clock time is actually set in case timeline was already in 0.

      updateTimelineWindowSize()
    }

    /**
     * Update the timeline window size, which is usually the duration of the longest animation being tracked. However, repeatable animations
     * are handled differently because they can have a large number of iterations resulting in a unrealistic duration. In that case, we take
     * the longest iteration instead to represent the window size and set the timeline max loop count to be large enough to display all the
     * iterations.
     */
    fun updateTimelineWindowSize() {
      val clock = animationClock ?: return

      var maxDurationPerIteration = DEFAULT_MAX_DURATION_MS
      if (!surface.executeOnRenderThread { maxDurationPerIteration = clock.getMaxDurationPerIteration.invoke(clock.clock) as Long }) return
      timeline.updateMaxDuration(maxDurationPerIteration)

      var maxDuration = DEFAULT_MAX_DURATION_MS
      if (!surface.executeOnRenderThread { maxDuration = clock.getMaxDurationFunction.invoke(clock.clock) as Long }) return

      timeline.maxLoopCount = if (maxDuration > maxDurationPerIteration) {
        // The max duration is longer than the max duration per iteration. This means that a repeatable animation has multiple iterations,
        // so we need to add as many loops to the timeline as necessary to display all the iterations.
        ceil(maxDuration / maxDurationPerIteration.toDouble()).toLong()
      }
      // Otherwise, the max duration fits the window, so we just need one loop that keeps repeating when loop mode is active.
      else 1
    }

    /**
     * Create a toolbar panel with actions to control the animation, e.g. play, pause and jump to start/end.
     *
     * TODO(b/157895086): Update action icons when we have the final Compose Animation tooling icons
     * TODO(b/157895086): Disable toolbar actions while build is in progress
     */
    private fun createPlaybackControllers(): JComponent = ActionManager.getInstance().createActionToolbar(
      "Animation Preview",
      DefaultActionGroup(listOf(
        timelineLoopAction,
        GoToStartAction(),
        playPauseAction,
        GoToEndAction(),
        timelineSpeedAction
      )),
      true).component

    /**
     * Creates a couple of comboboxes representing the start and end states of the animation.
     */
    private fun createAnimationStateComboboxes(): JComponent {
      startStateComboBox.addActionListener(startStateChangeListener)
      endStateComboBox.addActionListener(endStateChangeListener)

      val states = arrayOf(message("animation.inspector.states.combobox.placeholder.message"))
      val statesToolbar = JPanel(TabularLayout("Fit,Fit,Fit,Fit"))
      startStateComboBox.model = DefaultComboBoxModel(states)
      endStateComboBox.model = DefaultComboBoxModel(states)

      val swapStatesActionToolbar = object : ActionToolbarImpl("Swap States", DefaultActionGroup(SwapStartEndStatesAction()), true) {
        // From ActionToolbar#setMinimumButtonSize, all the toolbar buttons have 25x25 pixels by default. Set the preferred size of the
        // toolbar to be 5 pixels more in both height and width, so it fits exactly one button plus a margin
        override fun getPreferredSize() = JBUI.size(30, 30)
      }
      statesToolbar.add(swapStatesActionToolbar, TabularLayout.Constraint(0, 0))
      statesToolbar.add(startStateComboBox, TabularLayout.Constraint(0, 1))
      statesToolbar.add(JBLabel(message("animation.inspector.state.to.label")), TabularLayout.Constraint(0, 2))
      statesToolbar.add(endStateComboBox, TabularLayout.Constraint(0, 3))
      return statesToolbar
    }

    fun updateStateComboboxes(states: Array<Any>) {
      startStateComboBox.model = DefaultComboBoxModel(states)
      endStateComboBox.model = DefaultComboBoxModel(states)
    }

    private fun createAnimatedPropertiesPanel() = JPanel(TabularLayout("*", "${TIMELINE_HEADER_HEIGHT}px,*")).apply {
      preferredSize = JBDimension(200, 200)
      val propKeysTitlePanel = JPanel(TabularLayout("*", "*")).apply {
        // Bottom border separating this title header from the properties panel.
        border = MatteBorder(0, 0, 1, 0, JBColor.border())
        background = UIUtil.getTextFieldBackground()
        add(JBLabel(message("animation.inspector.prop.keys.title")).apply {
          border = JBUI.Borders.empty(0, 5)
        }, TabularLayout.Constraint(0, 0))
      }
      add(propKeysTitlePanel, TabularLayout.Constraint(0, 0))
      add(JBScrollPane(animatedPropertiesPanel), TabularLayout.Constraint(1, 0))
    }

    /**
     * Adds [timeline] to this tab's [timelinePanel]. The timeline is shared across all tabs, and a Swing component can't be added as a
     * child of multiple components simultaneously. Therefore, this method needs to be called everytime we change tabs.
     */
    fun addTimeline() {
      timelinePanel.add(timeline, BorderLayout.CENTER)
    }

    fun updateProperties() {
      val animClock = animationClock ?: return
      try {
        var animatedPropKeys = animClock.getAnimatedPropertiesFunction.invoke(animClock.clock, animation) as List<ComposeAnimatedProperty>
        animatedPropertiesPanel.updateProperties(animatedPropKeys)
      }
      catch (e: Exception) {
        LOG.warn("Failed to get the Compose Animation properties", e)
      }
    }

    /**
     * HTML panel to display animated properties and their corresponding values at the time set in [TransitionDurationTimeline].
     */
    private inner class AnimatedPropertiesPanel : JEditorPane() {

      init {
        margin = JBUI.insets(5)
        editorKit = UIUtil.getHTMLEditorKit()
        isEditable = false
        text = createNoPropertiesPanel()
        // If the caret updates, every time we change the animated properties panel content, the panel will be scrolled to the end.
        (caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE
      }

      private fun createNoPropertiesPanel() =
        HtmlBuilder().openHtmlBody().add(message("animation.inspector.no.properties.message")).closeHtmlBody().html

      /**
       * Updates the properties panel content, displaying one property per line. Each line has the property label (default label color)
       * followed by the corresponding value at current time (disabled label color).
       */
      fun updateProperties(animatedPropKeys: List<ComposeAnimatedProperty>) {
        text = if (animatedPropKeys.isEmpty()) {
          createNoPropertiesPanel()
        }
        else {
          val htmlBuilder = HtmlBuilder().openHtmlBody().beginDiv("white-space: nowrap")
          animatedPropKeys.forEachIndexed { index, property ->
            if (index > 0) {
              // Don't add line breaks before the first property, only to separate properties.
              htmlBuilder.newline().newline()
            }

            htmlBuilder
              .beginSpan("color: ${UIUtil.getLabelForeground().toCss()}")
              .add(property.label)
              .endSpan()
              .newline()
              .beginSpan("color: ${UIUtil.getLabelDisabledForeground().toCss()}")
              .add(property.value.toString())
              .endSpan()
          }
          htmlBuilder.endDiv().closeHtmlBody().html
        }
      }

      private fun Color.toCss() = "rgb($red, $green, $blue)"
    }

    /**
     * Swap start and end animation states in the corresponding combo boxes.
     */
    private inner class SwapStartEndStatesAction()
      : AnActionButton(message("animation.inspector.action.swap.states"), StudioIcons.LayoutEditor.Motion.PLAY_YOYO) {
      override fun actionPerformed(e: AnActionEvent) {
        isSwappingStates = true
        val startState = startStateComboBox.selectedItem
        startStateComboBox.selectedItem = endStateComboBox.selectedItem
        endStateComboBox.selectedItem = startState
        isSwappingStates = false
        logAnimationInspectorEvent(ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.TRIGGER_SWAP_STATES_ACTION)
      }

      override fun updateButton(e: AnActionEvent) {
        super.updateButton(e)
        e.presentation.isEnabled = true
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

    private var isPlaying = false

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

    private fun pause() {
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
    X_0_25(0.25f, "0.25x"),
    X_0_5(0.5f, "0.5x"),
    X_1(1f, "1x"),
    X_1_5(1.5f, "1.5x"),
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
      set(value) {
        field = value
        // Sets the clock time in compose so the animation corresponding to the selected tab can animate to the correct time.
        setClockTime(slider.value)
      }
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

    private val slider = object : JSlider(0, DEFAULT_MAX_DURATION_MS.toInt(), 0) {
      override fun updateUI() {
        setUI(TimelineSliderUI())
        updateLabelUIs()
      }

      override fun setMaximum(maximum: Int) {
        super.setMaximum(maximum)
        updateMajorTicks()
      }

      fun updateMajorTicks() {
        // First, calculate where the major ticks and labels are going to be painted, based on the maximum.
        val tickIncrement = maximum / 5
        setMajorTickSpacing(tickIncrement)
        // Now, add the "ms" suffix to each label.
        if (tickIncrement == 0) {
          // Handle the special case where maximum == 0 and we only have the "0ms" label.
          labelTable = createMsLabelTable(labelTable)
        }
        else {
          labelTable = createMsLabelTable(createStandardLabels(tickIncrement))
        }
      }

    }.apply {
      paintTicks = true
      paintLabels = true
      updateMajorTicks()
      setUI(TimelineSliderUI())
    }

    init {
      border = MatteBorder(0, 1, 0, 0, JBColor.border()) // Left border to separate the timeline from the properties panel

      add(slider, BorderLayout.CENTER)
      slider.addChangeListener {
        if (slider.value == cachedVal) return@addChangeListener // Ignore repeated values
        val newValue = slider.value
        cachedVal = newValue
        setClockTime(newValue)
      }
    }

    fun updateMaxDuration(durationMs: Long) {
      slider.maximum = durationMs.toInt()
    }

    fun setClockTime(newValue: Int) {
      val clock = animationClock ?: return
      val tab = selectedTab ?: return

      var clockTimeMs = newValue.toLong()
      if (playInLoop) {
        // When playing in loop, we need to add an offset to slide the window and take repeatable animations into account when necessary
        clockTimeMs += slider.maximum * loopCount
      }

      if (!surface.executeOnRenderThread { clock.setClockTimeFunction.invoke(clock.clock, clockTimeMs) }) return
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
          // paiting the labels and set the label enable status to match the slider's. Thus, we force the label color to the disabled one.
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
    private inner class TimelineSliderUI : BasicSliderUI(slider) {

      private val labelVerticalMargin = 5

      override fun getThumbSize(): Dimension {
        val originalSize = super.getThumbSize()
        return if (slider.parent == null) originalSize else Dimension(originalSize.width, slider.parent.height - labelsAndTicksHeight())
      }

      override fun calculateTickRect() {
        // Make the vertical tick lines cover the entire panel.
        tickRect.x = thumbRect.x
        tickRect.y = thumbRect.y
        tickRect.width = thumbRect.width
        tickRect.height = thumbRect.height + labelsAndTicksHeight()
      }

      override fun calculateLabelRect() {
        super.calculateLabelRect()
        labelRect.y = labelVerticalMargin
      }

      override fun paintTrack(g: Graphics) {
        // Track should not be painted.
      }

      override fun paintFocus(g: Graphics?) {
        // BasicSliderUI paints a dashed rect around the slider when it's focused. We shouldn't paint anything.
      }

      override fun paintLabels(g: Graphics?) {
        super.paintLabels(g)
        // Draw the line border below the labels.
        g as Graphics2D
        g.color = JBColor.border()
        g.stroke = BasicStroke(1f)
        val borderHeight = TIMELINE_HEADER_HEIGHT - 1 // Subtract the stroke (1)
        g.drawLine(0, borderHeight, slider.width, borderHeight)
      }

      override fun paintThumb(g: Graphics) {
        g as Graphics2D
        g.color = JBColor(0x4A81FF, 0xB4D7FF)
        g.stroke = BasicStroke(1f)
        val halfWidth = thumbRect.width / 2
        val x = thumbRect.x + halfWidth
        val y = thumbRect.y + TIMELINE_HEADER_HEIGHT
        g.drawLine(x, y, thumbRect.x + halfWidth, thumbRect.height + labelsAndTicksHeight());

        // The scrubber handle should have the following shape:
        //         ___
        //        |   |
        //         \ /
        // We add 5 points with the following coordinates:
        // (x, y): bottom of the scrubbler handle
        // (x - halfWidth, y - halfHeight): where the scrubber angled part meets the vertical one (left side)
        // (x - halfWidth, y - Height): top-left point of the scrubber, where there is a right angle
        // (x + halfWidth, y - Height): top-right point of the scrubber, where there is a right angle
        // (x + halfWidth, y - halfHeight): where the scrubber angled part meets the vertical one (right side)
        val handleHeight = TIMELINE_HANDLE_HALF_HEIGHT * 2
        val xPoints = intArrayOf(
          x,
          x - TIMELINE_HANDLE_HALF_WIDTH,
          x - TIMELINE_HANDLE_HALF_WIDTH,
          x + TIMELINE_HANDLE_HALF_WIDTH,
          x + TIMELINE_HANDLE_HALF_WIDTH
        )
        val yPoints = intArrayOf(y, y - TIMELINE_HANDLE_HALF_HEIGHT, y - handleHeight, y - handleHeight, y - TIMELINE_HANDLE_HALF_HEIGHT)
        g.fillPolygon(xPoints, yPoints, xPoints.size)
      }

      override fun paintMajorTickForHorizSlider(g: Graphics, tickBounds: Rectangle, x: Int) {
        g as Graphics2D
        g.color = JBColor.border()
        g.drawLine(x, tickRect.y + TIMELINE_HEADER_HEIGHT, x, tickRect.height);
      }

      override fun createTrackListener(slider: JSlider) = TimelineTrackListener()

      private fun labelsAndTicksHeight() = tickLength + heightOfTallestLabel

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

  private fun DesignSurface.executeOnRenderThread(callback: () -> Unit) =
    surface.layoutlibSceneManagers.singleOrNull()?.executeCallbacksAndRequestRender {
      callback()
    } ?: false
}