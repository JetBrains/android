/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.ComposePreviewBundle.message
import com.android.tools.idea.flags.StudioFlags.COMPOSE_ANIMATION_PREVIEW_COORDINATION_DRAG
import com.google.wireless.android.sdk.stats.ComposeAnimationToolingEvent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction
import com.intellij.ui.AnActionButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.MatteBorder

/** Bottom control panel. */
class BottomPanel(val previewState: AnimationPreviewState,
                  surface: DesignSurface<*>,
                  private val tracker: ComposeAnimationEventTracker) : JPanel(
  BorderLayout()) {

  var clockTimeMs = 0

  private val resetListeners: MutableList<() -> Unit> = mutableListOf()
  fun addResetListener(listener: () -> Unit) {
    resetListeners.add(listener)
  }

  init {
    border = MatteBorder(1, 0, 0, 0, JBColor.border())
    val actions = listOf(ClockTimeLabel(), Separator()).let {
      if (COMPOSE_ANIMATION_PREVIEW_COORDINATION_DRAG.get()) it + ResetTimelineAction()
      else it
    }
    // West toolbar
    val westToolbar = DefaultToolbarImpl(surface, "ResetCoordinationTimeline", DefaultActionGroup(actions))
    add(westToolbar, BorderLayout.WEST)
    preferredSize = Dimension(width, InspectorLayout.BOTTOM_PANEL_HEIGHT)
  }

  private inner class ClockTimeLabel() : ToolbarLabelAction() {
    override fun createCustomComponent(presentation: Presentation,
                                       place: String): JComponent =
      (super.createCustomComponent(presentation, place) as JBLabel).apply {
        font = JBFont.smallOrNewUiMedium()
        foreground = UIUtil.getContextHelpForeground()
        border = JBUI.Borders.empty(6) // Empty border to align label vertically.
      }

    override fun update(e: AnActionEvent) {
      super.update(e)
      val presentation = e.presentation
      presentation.text = "$clockTimeMs ${message("animation.inspector.transition.ms")}"
    }
  }

  private inner class ResetTimelineAction()
    : AnActionButton(message("animation.inspector.action.reset.timeline"), StudioIcons.LayoutEditor.Toolbar.LEFT_ALIGNED) {
    override fun actionPerformed(e: AnActionEvent) {
      resetListeners.forEach { it() }
      tracker(ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.RESET_TIMELINE)
    }

    override fun updateButton(e: AnActionEvent) {
      super.updateButton(e)
      if (e.presentation.isEnabled != previewState.isCoordinationAvailable()) {
        e.presentation.isEnabled = previewState.isCoordinationAvailable()
        e.presentation.text =
          when {
            previewState.isCoordinationAvailable() && previewState.isCoordinationPanelOpened() -> message(
              "animation.inspector.action.reset.timeline")
            previewState.isCoordinationAvailable() && !previewState.isCoordinationPanelOpened() -> message(
              "animation.inspector.action.reset.single.animation")
            else -> message("animation.inspector.coordination.unavailable.reset.timeline")
          }
      }
    }
  }
}
