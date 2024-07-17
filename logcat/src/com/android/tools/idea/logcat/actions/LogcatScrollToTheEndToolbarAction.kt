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
import com.android.tools.idea.logcat.util.isScrollAtBottom
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.DumbAware
import kotlin.math.max
import kotlin.math.min

/**
 * A Logcat specific version of [ScrollToTheEndToolbarAction]
 *
 * This version takes into account the scrollbar position for toggling the action state.
 */
internal class LogcatScrollToTheEndToolbarAction(private val editor: EditorEx) : ToggleAction(), DumbAware {
  init {
    @Suppress("DialogTitleCapitalization")
    val message = LogcatBundle.message("logcat.scroll.to.end.action.text")
    templatePresentation.description = message
    templatePresentation.text = message
    templatePresentation.icon = AllIcons.RunConfigurations.Scroll_down
  }

  override fun getActionUpdateThread() = EDT

  override fun isSelected(e: AnActionEvent): Boolean {
    val document = editor.document
    val isScrollAtBottom = editor.isScrollAtBottom(false)
    return (document.lineCount == 0 || editor.isCaretAtBottom()) && isScrollAtBottom
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      EditorUtil.scrollToTheEnd(editor)
    }
    else {
      val lastLine = max(0.0, (editor.document.lineCount - 1).toDouble()).toInt()
      val currentPosition = editor.caretModel.logicalPosition
      val position = LogicalPosition(
        max(0.0, min(currentPosition.line.toDouble(), (lastLine - 1).toDouble())).toInt(), currentPosition.column)
      editor.caretModel.moveToLogicalPosition(position)
    }
  }
}

private fun EditorEx.isCaretAtBottom() = document.getLineNumber(caretModel.offset) == document.lineCount - 1