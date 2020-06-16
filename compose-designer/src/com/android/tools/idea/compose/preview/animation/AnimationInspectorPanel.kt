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

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.util.ControllableTicker
import com.android.tools.idea.compose.preview.animation.AnimationInspectorPanel.TransitionDurationTimeline
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.layoutlibSceneManagers
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.AnActionButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.time.Duration
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingConstants
import kotlin.reflect.KFunction
import kotlin.reflect.full.memberFunctions

/**
 * Displays details about animations belonging to a Compose Preview. Allows users to see all the properties (e.g. `ColorPropKeys`) being
 * animated grouped by animation (e.g. `TransitionAnimation`, `AnimatedValue`). In addition, [TransitionDurationTimeline] is a timeline view
 * that can be controlled by scrubbing or through a set of controllers, such as play/pause and jump to end. The [AnimationInspectorPanel]
 * therefore allows a detailed inspection of Compose animations.
 */
class AnimationInspectorPanel(private val surface: DesignSurface) : JPanel(TabularLayout("Fit,*", "Fit,*")), Disposable {

  /**
   * Used in the tab title when adding new tabs to [tabbedPane]. It should get incremented on each use.
   */
  private var currentTab = 1

  /**
   * [CommonTabbedPane] where each tab represents a single animation being inspected. All tabs share the same [TransitionDurationTimeline],
   * but have their own playback toolbar, from/to state combo boxes and animated properties panel.
   */
  private val tabbedPane = CommonTabbedPane().apply {
    addChangeListener {
      // Swing components cannot be placed into different containers, so we add the shared timeline to the active tab on tab change.
      (getComponentAt(selectedIndex) as AnimationTab).add(timeline, TabularLayout.Constraint(1, 1, 2))
    }
  }

  /**
   * Maps animation objects to the [AnimationTab] that represents them.
   */
  private val animationTabs = HashMap<Any, AnimationTab>()

  /**
   * Panel displayed when the preview has no animations subscribed.
   */
  private val noAnimationsPanel = JPanel(BorderLayout()).apply {
    add(JBLabel(message("animation.inspector.no.animations.panel.message"), SwingConstants.CENTER), BorderLayout.CENTER)
  }

  private val timeline = TransitionDurationTimeline()

  private val playPauseAction = PlayPauseAction()

  /**
   * Function `setClockTime` of [clock].
   */
  var setAnimationClockFunction: KFunction<*>? = null

  /**
   * Instance of `PreviewAnimationClock` that animations inspected in this panel are subscribed to. Null when there are no animations.
   */
  internal var clock: Any? = null
    set(value) {
      field = value
      value?.let {
        setAnimationClockFunction = it::class.memberFunctions.single {
          it.name == "setClockTime"
        }
      }
    }

  init {
    name = "Animation Inspector"
    var composableTitle = JBLabel(message("animation.inspector.panel.title")).apply {
      border = JBUI.Borders.empty(5, 0)
    }

    add(composableTitle, TabularLayout.Constraint(0, 0))
    add(noAnimationsPanel, TabularLayout.Constraint(1, 0, 2))
  }

  /**
   * Adds an [AnimationTab] corresponding to the given [animation] to [tabbedPane].
   */
  internal fun addTab(animation: Any) {
    if (tabbedPane.tabCount == 0) {
      // There are no tabs and we're about to add one. Replace the placeholder panel with the TabbedPane.
      remove(noAnimationsPanel)
      add(tabbedPane, TabularLayout.Constraint(1, 0, 2))
    }

    val animationTab = AnimationTab()
    animationTabs[animation] = animationTab
    tabbedPane.addTab("TransitionAnimation #${currentTab++}", animationTab)
  }

  /**
   * Removes the [AnimationTab] corresponding to the given [animation] from [tabbedPane].
   */
  internal fun removeTab(animation: Any) {
    tabbedPane.remove(animationTabs[animation])
    animationTabs.remove(animation)

    if (tabbedPane.tabCount == 0) {
      // There are no more tabs. Replace the TabbedPane with the placeholder panel.
      remove(tabbedPane)
      add(noAnimationsPanel, TabularLayout.Constraint(1, 0, 2))
    }
  }

  override fun dispose() {
    playPauseAction.dispose()
    animationTabs.clear()
  }

  /**
   * Content of a tab representing an animation. All the elements that aren't shared between tabs and need to be exposed should be defined
   * in this class, e.g. from/to state combo boxes.
   */
  private inner class AnimationTab : JPanel(TabularLayout("Fit,*,Fit", "Fit,*")) {
    private val startStateComboBox = ComboBox(DefaultComboBoxModel(arrayOf<Any>()))
    private val endStateComboBox = ComboBox(DefaultComboBoxModel(arrayOf<Any>()))

    init {
      add(createPlaybackControllers(), TabularLayout.Constraint(0, 0))
      add(createAnimationStateComboboxes(), TabularLayout.Constraint(0, 2))
      add(createAnimatedPropertiesPanel(), TabularLayout.Constraint(1, 0))
    }

    /**
     * Create a toolbar panel with actions to control the animation, e.g. play, pause and jump to start/end.
     *
     * TODO(b/157895086): Update action icons when we have the final Compose Animation tooling icons
     * TODO(b/157895086): Disable toolbar actions while build is in progress
     */
    private fun createPlaybackControllers(): JComponent = ActionManager.getInstance().createActionToolbar(
      "Animation inspector",
      DefaultActionGroup(listOf(
        GoToStartAction(),
        playPauseAction,
        GoToEndAction(),
        SwapStartEndStatesAction()
      )),
      true).component

    /**
     * Creates a couple of comboboxes representing the start and end states of the animation.
     */
    private fun createAnimationStateComboboxes(): JComponent {
      // TODO(b/157896171): states should be obtained from the TransitionAnimation object, not hard coded.
      val states = arrayOf("start", "end")
      val statesToolbar = JPanel(TabularLayout("Fit,Fit,Fit"))
      startStateComboBox.model = DefaultComboBoxModel(states)
      endStateComboBox.model = DefaultComboBoxModel(states)
      statesToolbar.add(startStateComboBox, TabularLayout.Constraint(0, 0))
      statesToolbar.add(JBLabel(message("animation.inspector.state.to.label")), TabularLayout.Constraint(0, 1))
      statesToolbar.add(endStateComboBox, TabularLayout.Constraint(0, 2))
      return statesToolbar
    }

    // TODO(b/157895086): this is a placeholder. This component should display the properties being animated.
    private fun createAnimatedPropertiesPanel() = JPanel().apply {
      preferredSize = JBDimension(200, 200)
      border = JBUI.Borders.customLine(JBColor.border(), 1)
    }

    /**
     * Swap start and end animation states in the corresponding combo boxes.
     */
    private inner class SwapStartEndStatesAction()
      : AnActionButton(message("animation.inspector.action.swap.states"), StudioIcons.LayoutEditor.Motion.PLAY_YOYO) {
      override fun actionPerformed(e: AnActionEvent) {
        val startState = startStateComboBox.selectedItem
        startStateComboBox.selectedItem = endStateComboBox.selectedItem
        endStateComboBox.selectedItem = startState
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
        timeline.value = 0
      }

      override fun updateButton(e: AnActionEvent) {
        super.updateButton(e)
        e.presentation.isEnabled = timeline.value > 0
      }
    }

    /**
     * Snap the animation to the end state.
     */
    private inner class GoToEndAction
      : AnActionButton(message("animation.inspector.action.go.to.end"), StudioIcons.LayoutEditor.Motion.GO_TO_END) {
      override fun actionPerformed(e: AnActionEvent) {
        timeline.value = timeline.maximum
      }

      override fun updateButton(e: AnActionEvent) {
        super.updateButton(e)
        e.presentation.isEnabled = timeline.value < timeline.maximum
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
                             // TODO(b/157895086): remove the long -> int cast when the timeline panel is not a JSlider anymore.
                             timeline.value += tickPeriod.toMillis().toInt()
                             if (timeline.value >= timeline.maximum) {
                               pause()
                             }
                           }
                         }, tickPeriod)

    private var isPlaying = false

    override fun actionPerformed(e: AnActionEvent) = if (isPlaying) pause() else play()

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
      isPlaying = true
      ticker.start()
    }

    private fun pause() {
      isPlaying = false
      ticker.stop()
    }

    fun dispose() {
      ticker.dispose()
    }
  }

  /**
   *  Timeline panel ranging from 0 to the max duration of the animations being inspected, listing all the animations and their
   *  corresponding range as well. The timeline should respond to mouse commands, allowing users to jump to specific points, scrub it, etc.
   *
   *  TODO(b/157896171): duration should be obtained from the animation, not hard coded.
   *  TODO(b/157895086): The slider is a placeholder. The actual UI component is more complex and will be done in a future pass.
   */
  private inner class TransitionDurationTimeline : JSlider(0, 10000, 0) {

    var cachedVal = -1

    init {
      addChangeListener {
        if (this.value == cachedVal) return@addChangeListener // Ignore repeated values
        val newValue = this.value
        cachedVal = newValue

        if (clock != null && setAnimationClockFunction != null) {
          surface.layoutlibSceneManagers.single().executeCallbacksAndRequestRender {
            setAnimationClockFunction!!.call(clock, newValue.toLong())
          }
          return@addChangeListener
        }
      }
    }
  }
}