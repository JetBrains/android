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

import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.messages.LOGCAT_MESSAGE_KEY
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.SelectionModel

internal fun AnActionEvent.getEditor() = getData(LogcatPresenter.EDITOR)

internal fun AnActionEvent.getLogcatPresenter() = getData(LogcatPresenter.LOGCAT_PRESENTER_ACTION)

internal fun AnActionEvent.getConnectedDevice() = getData(LogcatPresenter.CONNECTED_DEVICE)

/** Gets the Logcat message surrounding the caret position or null if there is none */
internal fun AnActionEvent.getLogcatMessage(): LogcatMessage? {
  val offset = getEditor()?.caretModel?.offset ?: return null
  return getLogcatMessages(offset, offset).firstOrNull()
}

/** Gets the Logcat messages intersecting with the current selection */
internal fun AnActionEvent.getLogcatMessages(): List<LogcatMessage> {
  val editor = getEditor() ?: return emptyList()
  val selectionModel = editor.selectionModel
  val end =
    if (selectionModel.endsOnLineBreak()) selectionModel.selectionEnd - 1
    else selectionModel.selectionEnd
  return getLogcatMessages(selectionModel.selectionStart, end)
}

private fun AnActionEvent.getLogcatMessages(start: Int, end: Int): List<LogcatMessage> {
  val editor = getEditor() ?: return emptyList()
  return buildList {
    editor.document.processRangeMarkersOverlappingWith(start, end) {
      val message = it.getUserData(LOGCAT_MESSAGE_KEY)
      if (message != null) {
        add(message)
      }
      // If searching pos, stop after first message is found, otherwise, keep searching
      start != end || message == null
    }
  }
}

private fun SelectionModel.endsOnLineBreak(): Boolean {
  if (selectionStart == selectionEnd && selectionEnd <= 0) {
    return false
  }
  return editor.document.immutableCharSequence[selectionEnd - 1] == '\n'
}
