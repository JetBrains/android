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
import com.android.tools.idea.logcat.messages.LOGCAT_MESSAGE_KEY
import com.android.tools.idea.logcat.util.extractStudioBotQuestion
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ex.ToolWindowManagerEx

internal class ExplainLogcatCrashAction : DumbAwareAction() {

  override fun update(e: AnActionEvent) {
    val message = getMessage(e)
    when (message.count()) {
      0 -> e.presentation.isVisible = false
      else -> {
        val label = when {
          message.any { isCrashFrame(it) } -> LogcatBundle.message("logcat.studio.bot.action.crash")
          else -> LogcatBundle.message("logcat.studio.bot.action.entry")
        }
        e.presentation.isVisible = true
        e.presentation.text = IssueExplainer.get().getFixLabel(label)
      }
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val message = getMessage(e).joinToString("\n", postfix = "\n")
    IssueExplainer.get().explain(project, message, IssueExplainer.RequestKind.LOGCAT)

    ApplicationManager.getApplication().invokeLater {
      ToolWindowManagerEx.getInstanceEx(project).hideToolWindow("Logcat", false)
    }
  }

  /**
   * Try to compute the most relevant items from the selected lines. If there is no selection, and
   * the entry is part of a stacktrace, try to collect the whole stacktrace and summarize it.
   */
  private fun getMessage(e: AnActionEvent): List<String> {
    val editor = e.getEditor() ?: return emptyList()
    val selectionModel = editor.selectionModel
    return buildList {
      editor.document.processRangeMarkersOverlappingWith(
        selectionModel.selectionStart,
        selectionModel.selectionEnd
      ) {
        val message = it.getUserData(LOGCAT_MESSAGE_KEY)
        if (message != null && it.startOffset != selectionModel.selectionEnd) {
          add(message.extractStudioBotQuestion())
        }
        true
      }
    }
  }
}

private fun isCrashFrame(line: String): Boolean {
  return line.contains("\nat ")
}
