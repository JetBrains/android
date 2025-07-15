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

import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.util.createToolbarWithNavigation
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.MatteBorder

/** Bottom control panel. */
class BottomPanel(rootComponent: JComponent) : JPanel(BorderLayout()) {

  var clockTimeMs: Int = 0
    set(value) {
      field = value
      westToolbar.updateActionsAsync()
    }

  private val westToolbar =
    createToolbarWithNavigation(
      rootComponent,
      "ClockTimeToolbar",
      listOf(ClockTimeLabel(), Separator()),
    )

  init {
    add(westToolbar.component, BorderLayout.WEST)
    border = MatteBorder(1, 0, 0, 0, JBColor.border())
    preferredSize = Dimension(width, InspectorLayout.BOTTOM_PANEL_HEIGHT)
  }

  private inner class ClockTimeLabel : ToolbarLabelAction() {
    override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
      (super.createCustomComponent(presentation, place) as JBLabel).apply {
        font = JBFont.smallOrNewUiMedium()
        foreground = UIUtil.getContextHelpForeground()
        border = JBUI.Borders.empty(6) // Empty border to align label vertically.
      }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      super.update(e)
      val presentation = e.presentation
      presentation.text = "$clockTimeMs ${message("animation.inspector.transition.ms")}"
    }
  }
}
