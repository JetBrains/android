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

import com.android.tools.idea.logcat.LogcatToolWindowFactory
import com.intellij.execution.console.ConsoleConfigurable
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.ui.UIUtil

/**
 * A version of [com.intellij.execution.console.FoldLinesLikeThis] that works without a
 * [com.intellij.execution.ui.ConsoleView]
 */
internal class LogcatFoldLinesLikeThisAction(private val editor: Editor) :
  DumbAwareAction(ActionsBundle.message("action.ConsoleView.FoldLinesLikeThis.text")) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val enabled = getSingleLineSelection(editor) != null
    e.presentation.isEnabledAndVisible = enabled
  }

  override fun actionPerformed(e: AnActionEvent) {
    val selection = getSingleLineSelection(editor) ?: return
    ShowSettingsUtil.getInstance()
      .editConfigurable(
        editor.project,
        object : ConsoleConfigurable() {
          override fun editFoldingsOnly() = true

          override fun reset() {
            super.reset()
            UIUtil.invokeLaterIfNeeded { addRule(selection) }
          }
        },
      )
    LogcatToolWindowFactory.logcatPresenters.forEach { it.foldImmediately() }
  }
}

private fun getSingleLineSelection(editor: Editor): String? {
  val model = editor.selectionModel
  val document = editor.document
  return if (!model.hasSelection()) {
    val offset = editor.caretModel.offset
    if (offset <= document.textLength) {
      val lineNumber = document.getLineNumber(offset)
      document.text
        .substring(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber))
        .ifBlank { null }
    } else {
      null
    }
  } else {
    val start = model.selectionStart
    val end = model.selectionEnd
    if (document.getLineNumber(start) == document.getLineNumber(end)) {
      document.text.substring(start, end).ifBlank { null }
    } else {
      null
    }
  }
}
