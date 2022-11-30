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
package com.android.tools.idea.logcat.actions

import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.messages.LOGCAT_MESSAGE_KEY
import com.intellij.codeInsight.editorActions.TextBlockTransferable
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import javax.swing.Icon

internal class CopyMessageTextAction : DumbAwareAction(null as Icon?) {
  override fun update(e: AnActionEvent) {
    when (e.getLogcatMessages().count()) {
      0 -> e.presentation.isVisible = false
      1 -> e.presentation.enable(LogcatBundle.message("logcat.copy.message.action.text"))
      else -> e.presentation.enable(LogcatBundle.message("logcat.copy.messages.action.text"))
    }
  }

  override fun getActionUpdateThread() = BGT

  override fun actionPerformed(e: AnActionEvent) {
    val transferable = TextBlockTransferable(e.getLogcatMessages().joinToString("\n", postfix = "\n") { it.message }, emptyList(), null)
    CopyPasteManager.getInstance().setContents(transferable)
  }
}

private fun Presentation.enable(text: String) {
  isVisible = true
  this.text = text
}

private fun AnActionEvent.getLogcatMessages(): List<LogcatMessage> {
  val editor = getData(CommonDataKeys.EDITOR) as EditorEx? ?: return emptyList()
  val selectionModel = editor.selectionModel
  return buildList {
    editor.document.processRangeMarkersOverlappingWith(selectionModel.selectionStart, selectionModel.selectionEnd) {
      val message = it.getUserData(LOGCAT_MESSAGE_KEY)
      if (message != null && it.startOffset != selectionModel.selectionEnd) {
        add(message)
      }
      true
    }
  }
}
