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

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.utils.findIsInstanceAnd

/**
 * Minimum duration for the timeline. For transitions as snaps duration is 0. Minimum timeline
 * duration allows to interact with timeline even for 0-duration animations.
 */
const val MINIMUM_TIMELINE_DURATION_MS = 1000L

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
  private val tracker: AnimationTracker,
) : Disposable {

  protected val scope = AndroidCoroutineScope(this)

  // ******************
  // Properties: UI Components
  // *******************
  protected val animationPreviewPanel =
    JPanel(TabularLayout("*", "*,30px")).apply { name = "Animation Preview" }
  val component = TooltipLayeredPane(animationPreviewPanel)
  /**
   * Tabs panel where each tab represents a single animation being inspected. First tab is a
   * coordination tab. All tabs share the same [Timeline], but have their own playback toolbar and
   * from/to state combo boxes.
   */
  @VisibleForTesting
  val tabbedPane = AnimationTabs(project, this).apply { addListener(TabChangeListener()) }

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

  // *****************
  // Properties: Timeline
  // *****************
  protected val timeline = Timeline(animationPreviewPanel, component)

  // ************************
  // Protected Methods: Animation Control
  // *************************
  protected fun addAnimation(animation: T) {
    synchronized(animations) { animations = animations.plus(animation) }
  }

  protected fun removeAnimation(animation: T) {
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
  protected abstract suspend fun updateTimelineElements()

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
        animations.findIsInstanceAnd<SupportedAnimationManager> { it.tabComponent == component }
      if (component is AllTabPanel) { // If coordination tab is selected.
        component.addTimeline(timeline)
      } else {
        selectedAnimation?.addTimeline(timeline)
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
    var cachedVal = -1

    init {
      addChangeListener {
        if (value == cachedVal) return@addChangeListener // Ignore repeated values
        cachedVal = value
        scope.launch { setClockTime(value) }
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
}
