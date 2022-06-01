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
package com.android.tools.idea.common.error

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.util.containers.isEmpty
import java.awt.event.MouseEvent
import javax.swing.JList
import kotlin.streams.toList

class ShowQuickFixesAction : AnAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    presentation.text = ActionsBundle.actionText("ProblemsView.QuickFixes") ?: "Show Quick Fix"

    val node = event.getData(PlatformDataKeys.SELECTED_ITEM) as? IssueNode
    if (node == null) {
      presentation.isEnabled = false
      return
    }

    val fixes = node.issue.fixes
    val suppress = node.issue.suppresses
    if (fixes.isEmpty() && suppress.isEmpty()) {
      presentation.text = "No Quick Fix for This Issue"
      presentation.isEnabled = false
    }
    else {
      presentation.isEnabled = true
    }
  }

  override fun actionPerformed(event: AnActionEvent) {
    val node = event.getData(PlatformDataKeys.SELECTED_ITEM) as? IssueNode ?: return
    val issue = node.issue
    val fixable = issue.fixes.toList() + issue.suppresses.toList()

    val popup = JBPopupFactory.getInstance().createPopupChooserBuilder(fixable)
      .setRenderer(QuickFixableCellRenderer())
      .setItemChosenCallback { it.action.run() }
      .createPopup()

    val mouse = event.inputEvent as? MouseEvent ?: return popup.showInBestPositionFor(event.dataContext)
    val button = mouse.source as? ActionButton ?: return popup.showInBestPositionFor(event.dataContext)
    popup.showUnderneathOf(button)
  }
}

private class QuickFixableCellRenderer: ColoredListCellRenderer<Issue.QuickFixable>() {
  override fun customizeCellRenderer(list: JList<out Issue.QuickFixable>, element: Issue.QuickFixable, index: Int,
                                     selected: Boolean, hasFocus: Boolean) {
    icon = element.icon
    append(element.description)
  }
}
