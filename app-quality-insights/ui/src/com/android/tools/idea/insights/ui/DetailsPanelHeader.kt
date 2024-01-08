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

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.IssueState
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
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.border.CompoundBorder
import org.jetbrains.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy

class DetailsPanelHeader(editor: Editor) : JPanel(TabularLayout("*,Fit", "Fit")) {
  @VisibleForTesting val titleLabel = SimpleColoredComponent()

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
    add(titleLabel, TabularLayout.Constraint(0, 0))
    toolbar.targetComponent = this
    toolbar.layoutStrategy = ToolbarLayoutStrategy.HORIZONTAL_NOWRAP_STRATEGY
    toolbar.setReservePlaceAutoPopupIcon(false)
    ActionToolbarUtil.makeToolbarNavigable(toolbar)
    toolbar.component.apply {
      isOpaque = false
      isVisible = false
    }
    add(toolbar.component, TabularLayout.Constraint(0, 1))
    border =
      CompoundBorder(JBUI.Borders.customLineBottom(JBColor.border()), JBUI.Borders.empty(0, 8))
    preferredSize = Dimension(0, JBUIScale.scale(28))
  }

  fun updateWithIssue(issue: AppInsightsIssue?) {
    titleLabel.clear()
    toolbar.component.isVisible = false

    if (issue == null) return

    titleLabel.icon = issue.issueDetails.fatality.getIcon()
    val (className, methodName) = issue.issueDetails.getDisplayTitle()
    val style =
      when (issue.state) {
        IssueState.OPEN,
        IssueState.OPENING -> SimpleTextAttributes.STYLE_PLAIN
        IssueState.CLOSED,
        IssueState.CLOSING -> SimpleTextAttributes.STYLE_STRIKEOUT
      }
    titleLabel.append(className, SimpleTextAttributes(style, null))
    if (methodName.isNotEmpty()) {
      titleLabel.append(".", SimpleTextAttributes(style, null))
      titleLabel.append(
        methodName,
        SimpleTextAttributes(style or SimpleTextAttributes.STYLE_BOLD, null)
      )
    }
    toolbar.component.isVisible = true
  }
}
