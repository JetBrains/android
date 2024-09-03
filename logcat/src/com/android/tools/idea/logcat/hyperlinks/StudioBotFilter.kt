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
package com.android.tools.idea.logcat.hyperlinks

import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.messages.LOGCAT_MESSAGE_KEY
import com.android.tools.idea.logcat.util.extractStudioBotContent
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.buildPrompt
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.Result
import com.intellij.execution.filters.Filter.ResultItem
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ToolWindowManagerEx

internal class StudioBotFilter(private val editor: EditorEx) : Filter {
  override fun applyFilter(line: String, entireLength: Int): Result? {
    if (!StudioBot.getInstance().isAvailable()) {
      return null
    }
    val offset = entireLength - line.length
    val start = line.indexOf(linkText)
    if (start < 0) {
      return null
    }
    val end = start + linkText.length

    val item = ResultItem(start + offset, end + offset, StudioBotHyperLinkInfo(editor))

    return Result(listOf(item))
  }

  companion object {
    val linkText = LogcatBundle.message("logcat.studio.bot.link.text")
  }

  private class StudioBotHyperLinkInfo(private val editor: EditorEx) : HyperlinkInfo {
    override fun navigate(project: Project) {
      val studioBot = StudioBot.getInstance()
      if (!studioBot.isAvailable()) return

      val offset = editor.caretModel.offset
      editor.document.processRangeMarkersOverlappingWith(offset, offset) {
        val message =
          it.getUserData(LOGCAT_MESSAGE_KEY) ?: return@processRangeMarkersOverlappingWith true

        val content = message.extractStudioBotContent()
        val query = LogcatBundle.message("logcat.studio.bot.action.query.basic", content)

        // If context sharing is enabled, send the query immediately. Otherwise, stage
        // it in the query bar.
        if (studioBot.isContextAllowed(project)) {
          val prompt = buildPrompt(project) { userMessage { text(query, filesUsed = emptyList()) } }
          studioBot.chat(project).sendChatQuery(prompt, StudioBot.RequestSource.LOGCAT)
        } else {
          studioBot.chat(project).stageChatQuery(query, StudioBot.RequestSource.LOGCAT)
        }

        ApplicationManager.getApplication().invokeLater {
          ToolWindowManagerEx.getInstanceEx(project).hideToolWindow("Logcat", false)
        }
        false
      }
    }
  }
}
