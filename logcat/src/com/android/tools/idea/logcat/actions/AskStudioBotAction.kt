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

import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.util.extractStudioBotContent
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.buildPrompt
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import icons.StudioIcons

private val exceptionLinePattern = Regex("\n\\s*at .+\\(.+\\)")

internal class AskStudioBotAction : DumbAwareAction(StudioIcons.StudioBot.ASK) {

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  private fun getLabel(selection: String?, message: String?): String? =
    when {
      !selection.isNullOrBlank() -> LogcatBundle.message("logcat.studio.bot.action.selection")
      message == null -> null
      exceptionLinePattern.containsMatchIn(message) ->
        LogcatBundle.message("logcat.studio.bot.action.crash")
      else -> LogcatBundle.message("logcat.studio.bot.action.entry")
    }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = false
    val studioBot = StudioBot.getInstance()
    if (!studioBot.isAvailable()) {
      return
    }
    val editor = e.getEditor() ?: return
    val selection = editor.selectionModel.selectedText
    val message = e.getLogcatMessage()?.message

    val label = getLabel(selection, message) ?: return
    e.presentation.isVisible = true
    e.presentation.text =
      StringUtil.toTitleCase(LogcatBundle.message("logcat.studio.bot.action.label", label))
  }

  override fun actionPerformed(e: AnActionEvent) {
    val studioBot = StudioBot.getInstance()
    val project = e.project ?: return
    val editor = e.getEditor() ?: return
    val selection = editor.selectionModel.selectedText
    val message = e.getLogcatMessage()?.message

    val label = getLabel(selection, message) ?: return
    val content =
      selection.takeIf { !it.isNullOrBlank() }
        ?: e.getLogcatMessage()?.extractStudioBotContent()
        ?: return

    val queryText = LogcatBundle.message("logcat.studio.bot.action.query", label, content)

    // Logcat output is considered sensitive text, so we have to check the context sharing setting
    if (studioBot.isContextAllowed(project)) {
      val prompt = buildPrompt(project) { userMessage { text(queryText, filesUsed = emptyList()) } }
      studioBot.chat(project).sendChatQuery(prompt, StudioBot.RequestSource.LOGCAT)
    } else {
      studioBot.chat(project).stageChatQuery(queryText, StudioBot.RequestSource.LOGCAT)
    }

    ApplicationManager.getApplication().invokeLater {
      ToolWindowManagerEx.getInstanceEx(project).hideToolWindow("Logcat", false)
    }
  }
}
