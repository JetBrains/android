/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.insights.AppInsightsIssue
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actions.AbstractToggleUseSoftWrapsAction
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.border.CompoundBorder
import org.jetbrains.annotations.VisibleForTesting

class DetailsPanelHeader(editor: Editor) : JPanel(BorderLayout()) {
  @VisibleForTesting val titleLabel = JBLabel()

  private val wrapAction =
    object : AbstractToggleUseSoftWrapsAction(SoftWrapAppliancePlaces.CONSOLE, false) {
      init {
        ActionUtil.copyFrom(this, IdeActions.ACTION_EDITOR_USE_SOFT_WRAPS)
      }
      override fun getEditor(e: AnActionEvent) = editor
      override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

  @VisibleForTesting
  val toolbar =
    ActionManager.getInstance()
      .createActionToolbar("StackTraceToolbar", DefaultActionGroup(wrapAction), true)

  init {
    border = JBUI.Borders.empty()
    add(titleLabel, BorderLayout.WEST)
    toolbar.targetComponent = this
    toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    toolbar.setReservePlaceAutoPopupIcon(false)
    ActionToolbarUtil.makeToolbarNavigable(toolbar)
    toolbar.component.apply {
      isOpaque = false
      isVisible = false
    }
    add(toolbar.component, BorderLayout.EAST)
    border =
      CompoundBorder(JBUI.Borders.customLineBottom(JBColor.border()), JBUI.Borders.emptyLeft(8))
    preferredSize = Dimension(0, JBUIScale.scale(28))
  }

  fun updateWithIssue(issue: AppInsightsIssue?) {
    titleLabel.icon = null
    titleLabel.text = null
    toolbar.component.isVisible = false

    if (issue == null) return

    titleLabel.icon = issue.issueDetails.fatality.getIcon()
    val (className, methodName) = issue.issueDetails.getDisplayTitle()
    val methodString =
      if (methodName.isNotEmpty()) {
        ".<B>$methodName</B>"
      } else ""
    titleLabel.text = "<html>$className$methodString</html>"
    toolbar.component.isVisible = true
  }
}
