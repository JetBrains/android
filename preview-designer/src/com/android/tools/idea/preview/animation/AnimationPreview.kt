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

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.animation.timeline.TimelineElement
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.executeInRenderSession
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.util.io.await
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.utils.findIsInstanceAnd

/**
 * Minimum duration for the timeline. For transitions as snaps duration is 0. Minimum timeline
 * duration allows to interact with timeline even for 0-duration animations.
 */
private const val MINIMUM_TIMELINE_DURATION_MS = 1000L

/**
 * Provides tools for in-depth inspection of animations within your application.
 *
 * User can inspect animation by using: [timeline]:An interactive timeline view allowing users to
 * scrub through the animation's progress, jump to specific points, and visualize the duration of
 * individual animations. [playbackControls]: Intuitive controls for playing, pausing, and
 * controlling the speed of animation playback.
 *
 * T is subclass of AnimationManager AnimationPreview can work with.
 */
abstract class AnimationPreview<T : AnimationManager>(
  project: Project,
  private val sceneManagerProvider: () -> LayoutlibSceneManager?,
  rootComponent: JComponent,
  private val tracker: AnimationTracker,
) : Disposable {

  protected val scope = AndroidCoroutineScope(this)

  // ******************
  // Properties: UI Components
  // *******************
  private val animationPreviewPanel =
    JPanel(TabularLayout("*", "*,30px")).apply { name = "Animation Preview" }
  val component = TooltipLayeredPane(animationPreviewPanel)

  /**
   * Tabs panel where each tab represents a single animation being inspected. First tab is a
   * coordination tab. All tabs share the same [Timeline], but have their own playback toolbar and
   * from/to state combo boxes.
   */
  @VisibleForTesting
  val tabbedPane = AnimationTabs(project, this).apply { addListener(TabChangeListener()) }
  private val bottomPanel =
    BottomPanel(rootComponent, tracker).apply {
      addResetListener {
        scope.launch {
          animations.filterIsInstance<SupportedAnimationManager>().forEach { it.offset.value = 0 }
          resetTimelineAndUpdateWindowSize(false)
        }
      }
    }

  /** Loading panel displayed when the preview has no animations subscribed. */
  private val noAnimationsPanel =
    JBLoadingPanel(BorderLayout(), this).apply {
      name = "Loading Animations Panel"
      setLoadingText(message("animation.inspector.loading.animations.panel.message"))
    }

  /** A special tab that displays an overview of all the animations being inspected. */
  @VisibleForTesting val coordinationTab = AllTabPanel(this)

  // ************************
  // Properties: State Management
  // *************************
  /**
   * List of [AnimationManager], so all animation cards that are displayed in inspector. Please keep
   * it immutable to avoid [ConcurrentModificationException] as multiple threads can access and
   * modify [animations] at the same time.
   */
  @VisibleForTesting
  var animations: List<T> = emptyList()
    private set

  /** Holds the currently selected animation (for focused inspection on a single tab).* */
  protected var selectedAnimation: SupportedAnimationManager? = null
    private set

  /**
   * Tracks the maximum allowed duration of a single animation iteration. This is important for
   * handling long-running or repeating animations.
   */
  protected val maxDurationPerIteration: MutableStateFlow<Long> =
    MutableStateFlow(DEFAULT_ANIMATION_PREVIEW_MAX_DURATION_MS)

  // *****************
  // Properties: Timeline
  // *****************
  /**
   * An interactive timeline where users scrub or jump within an animation. Visual ranges display
   * the durations of individual animations on the timeline.
   */
  protected val timeline: Timeline =
    Timeline(animationPreviewPanel, component).apply {
      addChangeListener { scope.launch(uiThread) { bottomPanel.clockTimeMs = value } }
    }
  private val clockControl = SliderClockControl(timeline)

  /**
   * Provides buttons and controls for playing, pausing, and adjusting the playback speed of the
   * animation.
   */
  protected val playbackControls =
    PlaybackControls(clockControl, tracker, rootComponent, this).apply {
      coordinationTab.addPlayback(createToolbar())
    }

  // ************************
  // Protected Methods: Animation Control
  // *************************
  private fun addAnimation(animation: T) {
    synchronized(animations) { animations = animations.plus(animation) }
  }

  private fun removeAnimation(animation: T) {
    synchronized(animations) {
      animations = animations.filterNot { it == animation }
      if (selectedAnimation == animation) {
        selectedAnimation = null
      }
    }
  }

  /**
   * Set clock time, driving the animation's state.
   *
   * Usually does some invocations via reflection on androidx side.
   *
   * @param newValue new clock time in milliseconds.
   * @param longTimeout set true to use a long timeout.
   */
  protected abstract suspend fun setClockTime(newValue: Int, longTimeout: Boolean = false)

  /** Repaints [TimelineElement] on selected tab. */
  protected suspend fun updateTimelineElements() {
    // Call once to update all sizes as all curves / lines required it.
    withContext(uiThread) {
      timeline.sliderUI.elements.forEach { it.dispose() }
      timeline.sliderUI.elements = getTimelineElements()
    }
    timeline.repaint()
    timeline.revalidate()
    coordinationTab.revalidate()
  }

  @UiThread
  private fun getTimelineElements(): List<TimelineElement> {
    var currentY = InspectorLayout.timelineHeaderHeightScaled()

    val selectedAnimation = selectedAnimation
    val elements: List<TimelineElement> =
      if (selectedAnimation != null) {
        val element =
          selectedAnimation.createTimelineElement(
            timeline,
            currentY,
            forIndividualTab = true,
            timeline.sliderUI.positionProxy,
          )
        listOf(element)
      } else {
        animations.map { animation ->
          animation
            .createTimelineElement(
              timeline,
              currentY,
              forIndividualTab = false,
              timeline.sliderUI.positionProxy,
            )
            .also { element -> currentY += element.heightScaled() }
        }
      }
    elements.forEach { Disposer.tryRegister(this@AnimationPreview, it) }
    return elements
  }

  // *******************
  // Nested Classes
  // *******************
  protected inner class TabChangeListener : TabsListener {
    override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
      if (newSelection == oldSelection) return

      val component = newSelection?.component ?: return
      // If single supported animation tab is selected.
      // We assume here only supported animations could be opened.
      selectedAnimation =
        animations.findIsInstanceAnd<SupportedAnimationManager> { it.tab.component == component }
      if (component is AllTabPanel) { // If coordination tab is selected.
        component.addTimeline(timeline)
      } else {
        selectedAnimation?.tab?.addTimeline(timeline)
      }
      scope.launch { updateTimelineElements() }
    }
  }

  /**
   * Timeline panel ranging from 0 to the max duration (in ms) of the animations being inspected,
   * listing all the animations and their corresponding range as well. The timeline should respond
   * to mouse commands, allowing users to jump to specific points, scrub it, etc.
   */
  protected inner class Timeline(owner: JComponent, pane: TooltipLayeredPane) :
    TimelinePanel(Tooltip(owner, pane), tracker) {

    var cachedVal = 0

    init {
      addChangeListener {
        val newValue = value
        if (cachedVal != newValue) {
          cachedVal = newValue
          scope.launch {
            setClockTime(newValue)
            renderAnimation()
          }
        }
      }
      addComponentListener(
        object : ComponentAdapter() {
          override fun componentResized(e: ComponentEvent?) {
            scope.launch { updateTimelineElements() }
          }
        }
      )
    }
  }

  init {
    showNoAnimationsPanel()
    tabbedPane.addListener(TabChangeListener())
    scope.launch { maxDurationPerIteration.collect { updateTimelineMaximum() } }
  }

  /** Triggers a render/update of the displayed preview. */
  private suspend fun renderAnimation() {
    sceneManagerProvider()?.executeCallbacksAndRequestRender()?.await()
  }

  protected suspend fun resetTimelineAndUpdateWindowSize(longTimeout: Boolean) {
    // Set the timeline to 0
    setClockTime(0, longTimeout)
    updateMaxDuration(longTimeout)
    // Update the cached value manually to prevent the timeline to set the clock time to 0 using the
    // short timeout.
    timeline.cachedVal = 0
    // Move the timeline slider to 0.
    withContext(uiThread) { clockControl.jumpToStart() }
    updateTimelineElements()
    renderAnimation()
  }

  private fun updateTimelineMaximum() {
    val timelineMax =
      max(
        animations.maxOfOrNull { it.timelineMaximumMs }?.toLong() ?: maxDurationPerIteration.value,
        maxDurationPerIteration.value,
      )
    scope.launch {
      withContext(uiThread) {
        clockControl.updateMaxDuration(max(timelineMax, MINIMUM_TIMELINE_DURATION_MS))
      }
      updateTimelineElements()
    }
  }

  /**
   * Update the timeline window size, which is usually the duration of the longest animation being
   * tracked. However, repeatable animations are handled differently because they can have a large
   * number of iterations resulting in an unrealistic duration. In that case, we take the longest
   * iteration instead to represent the window size and set the timeline max loop count to be large
   * enough to display all the iterations.
   */
  protected abstract suspend fun updateMaxDuration(longTimeout: Boolean = false)

  /** Replaces the [tabbedPane] with [noAnimationsPanel]. */
  private fun showNoAnimationsPanel() {
    noAnimationsPanel.startLoading()
    animationPreviewPanel.add(noAnimationsPanel, TabularLayout.Constraint(0, 0))
    animationPreviewPanel.remove(tabbedPane.component)
    animationPreviewPanel.remove(bottomPanel)
    // Reset the timeline cached value, so when new tabs are added, any new value will trigger an
    // update
    clockControl.jumpToStart()
    // The animation panel might not have the focus when the "No animations" panel is displayed,
    // i.e. when code has changed in the editor using Fast Preview, and we need to refresh the
    // animation preview, so it displays the most up-to-date animations. For that reason, we need to
    // make
    // sure the animation panel is repainted correctly.
    animationPreviewPanel.repaint()
    playbackControls.pause()
  }

  /** Hide [noAnimationsPanel] and add [tabbedPane]. */
  private fun hideNoAnimationPanel() {
    noAnimationsPanel.stopLoading()
    animationPreviewPanel.remove(noAnimationsPanel)
    animationPreviewPanel.add(tabbedPane.component, TabularLayout.Constraint(0, 0))
    animationPreviewPanel.add(bottomPanel, TabularLayout.Constraint(1, 0))
  }

  /**
   * Remove all tabs from [tabbedPane], replace it with [noAnimationsPanel], and clears the cached
   * animations.
   */
  open fun invalidatePanel(): Job =
    scope.launch(uiThread) {
      /**
       * Calling [removeAnimationManager] for all animations will properly remove the cards from
       * AllTabPanel, animationsMap, and tabs from tabbedPane. It will also show the
       * noAnimationsPanel when removing all tabs.
       */
      animations.forEach { removeAnimationManager(it) }
    }

  @UiThread
  protected suspend fun removeAnimationManager(animationManager: T) {
    animationManager.destroy()
    coordinationTab.removeCard(animationManager.card)
    removeAnimation(animationManager)
    if (animationManager is SupportedAnimationManager) {
      tabbedPane.tabs
        .find { it.component == animationManager.tab.component }
        ?.let { tabbedPane.removeTab(it) }
    }
    if (animations.isEmpty()) {
      tabbedPane.removeAllTabs()
      // There are no more tabs. Replace the TabbedPane with the placeholder panel.
      showNoAnimationsPanel()
    } else if (tabbedPane.tabCount != 0) {
      tabbedPane.select(tabbedPane.getTabAt(0), true)
    }
  }

  /** Adds an [AnimationManager] card to [coordinationTab]. */
  @UiThread
  protected fun addAnimationManager(animationTab: T) {
    val isAddingFirstTab = animations.isEmpty()
    addAnimation(animationTab)
    coordinationTab.addCard(animationTab.card)

    if (isAddingFirstTab) {
      // There are no tabs, and we're about to add one. Replace the placeholder panel with the
      // TabbedPane.
      hideNoAnimationPanel()
      tabbedPane.addTab(
        TabInfo(coordinationTab).apply {
          text = "${message("animation.inspector.tab.all.title")}  "
        },
        0,
      )
      coordinationTab.addTimeline(timeline)
    }
  }

  protected suspend fun executeInRenderSession(longTimeout: Boolean = false, function: () -> Unit) {
    sceneManagerProvider()?.executeInRenderSession(longTimeout) { function() }
  }

  override fun dispose() {
    timeline.sliderUI.elements.forEach { it.dispose() }
  }
}
