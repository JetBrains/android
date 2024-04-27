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
package com.android.tools.idea.logcat.actions

import com.android.tools.idea.explainer.IssueExplainer
import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.util.extractStudioBotQuestion
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ex.ToolWindowManagerEx

private val exceptionLinePattern = Regex("\n\\s*at .+\\(.+\\)")

internal class AskStudioBotAction : DumbAwareAction(IssueExplainer.get().getIcon()) {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = false
    if (!IssueExplainer.get().isAvailable()) {
      return
    }
    val editor = e.getEditor() ?: return
    val selection = editor.selectionModel.selectedText
    val message = e.getLogcatMessage()?.message

    val label =
      when {
        !selection.isNullOrBlank() -> LogcatBundle.message("logcat.studio.bot.action.selection")
        message == null -> return
        exceptionLinePattern.containsMatchIn(message) ->
          LogcatBundle.message("logcat.studio.bot.action.crash")
        else -> LogcatBundle.message("logcat.studio.bot.action.entry")
      }
    e.presentation.isVisible = true
    e.presentation.text = IssueExplainer.get().getFixLabel(label)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = e.getEditor() ?: return
    val selection = editor.selectionModel.selectedText
    val question =
      selection.takeIf { !it.isNullOrBlank() }
        ?: e.getLogcatMessage()?.extractStudioBotQuestion()
        ?: return
    IssueExplainer.get().explain(project, question, IssueExplainer.RequestKind.LOGCAT)
    ApplicationManager.getApplication().invokeLater {
      ToolWindowManagerEx.getInstanceEx(project).hideToolWindow("Logcat", false)
    }
  }
}
