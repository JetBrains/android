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
import com.android.tools.idea.preview.util.createToolbarWithNavigation
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.border.MatteBorder

/**
 * Represents a tab within an animation preview tool that displays individual animation and
 * timeline.
 *
 * This class manages the layout and components necessary to interact with the animation, including:
 * - [playbackControls] to control the animation playback (e.g., play, pause, rewind).
 * - A toolbar for additional actions, primarily to change the state of the animation.
 * - A button for freezing the animation at a specific frame using [freezeAction].
 *
 * The timeline itself (represented by a [TimelinePanel]) is added dynamically using the
 * [addTimeline] method.
 *
 * @param rootComponent The root component of the animation preview tool.
 * @param playbackControls The controls responsible for managing the animation playback.
 * @param changeStateActions Actions that change the state of the animation preview, displayed in
 *   the toolbar.
 * @param freezeAction The action to freeze the animation at a specific frame.
 */
class AnimationTab(
  private val rootComponent: JComponent,
  private val playbackControls: PlaybackControls,
  private val changeStateActions: List<AnAction>,
  private val freezeAction: AnAction,
) {
  private val tabScrollPane =
    JBScrollPane().apply { border = MatteBorder(1, 1, 0, 0, JBColor.border()) }

  val component: JComponent by lazy {
    JPanel(TabularLayout("*,Fit", "32px,*")).apply {
      //    |  playbackControls                            |  toolbar  |
      //    ------------------------------------------------------------
      //    |                                                          |
      //    |                     tabScrollPane                        |
      //    |                                                          |
      val toolbar = createToolbarWithNavigation(rootComponent, "State", changeStateActions)
      add(toolbar.component, TabularLayout.Constraint(0, 1))
      add(tabScrollPane, TabularLayout.Constraint(1, 0, 2))
      tabScrollPane.setViewportView(JPanel(BorderLayout()))
      add(playbackControls.createToolbar(listOf(freezeAction)), TabularLayout.Constraint(0, 0))
      isFocusable = false
      focusTraversalPolicy = LayoutFocusTraversalPolicy()
    }
  }

  /**
   * Adds [timeline] to this tab's layout. The timeline is shared across all tabs, and a Swing
   * component can't be added as a child of multiple components simultaneously. Therefore, this
   * method needs to be called everytime we change tabs.
   */
  @UiThread
  fun addTimeline(timeline: TimelinePanel) {
    tabScrollPane.viewport.add(timeline, BorderLayout.CENTER)
    tabScrollPane.revalidate()
  }
}
