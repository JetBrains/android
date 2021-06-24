/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual.visuallint

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.ui.components.JBCheckBox
import java.awt.event.ItemEvent
import javax.swing.JCheckBox
import javax.swing.JComponent

class ToggleOnlyShowLayoutWithIssuesAction(val surface: NlDesignSurface) : AnAction(), CustomComponentAction {
  private val checkBox: JCheckBox

  init {
    checkBox = JBCheckBox("Show issues only")
    checkBox.addItemListener {
      if (it.stateChange == ItemEvent.SELECTED) {
        val managersWithIssue = surface.issueModel.issues.filterIsInstance<VisualLintRenderIssue>()
          .mapNotNull { issue -> surface.getSceneManager(issue.sourceModel) }
          .toList()
        managersWithIssue.flatMap { manager -> manager.sceneViews }.forEach { view -> view.isVisible = true }
        surface.sceneManagers.minus(managersWithIssue).flatMap { manager -> manager.sceneViews }.forEach { view -> view.isVisible = false }
      }
      else if (it.stateChange == ItemEvent.DESELECTED) {
        surface.sceneManagers.flatMap { manager -> manager.sceneViews }.forEach { sceneView -> sceneView.isVisible = true }
      }
      surface.revalidateScrollArea()
      surface.repaint()
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return checkBox
  }

  override fun update(e: AnActionEvent) {
    if (!StudioFlags.NELE_VISUAL_LINT_TOGGLE_ISSUE_LAYOUTS.get()) {
      checkBox.isVisible = false
      return
    }
    val hasIssue = surface.issueModel.hasIssues()
    e.presentation.isEnabledAndVisible = hasIssue
    checkBox.isVisible = hasIssue
  }

  override fun actionPerformed(e: AnActionEvent) = Unit
}
