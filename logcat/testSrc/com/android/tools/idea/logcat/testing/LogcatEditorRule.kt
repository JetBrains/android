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
package com.android.tools.idea.logcat.testing

import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.messages.LOGCAT_MESSAGE_KEY
import com.android.tools.idea.logcat.util.createLogcatEditor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.junit.rules.ExternalResource

/** A Rule that provides a Logcat Editor to a test */
internal class LogcatEditorRule(private val projectRule: ProjectRule) : ExternalResource() {
  lateinit var editor: EditorEx
    private set

  /**
   * RangeMarker's are kept in the Document as weak reference (see IntervalTreeImpl#createGetter) so
   * we need to keep them alive as long as they are valid.
   */
  private val markers = mutableListOf<RangeMarker>()

  override fun before() {
    editor = runInEdtAndGet { createLogcatEditor(projectRule.project) }
  }

  override fun after() {
    runInEdtAndWait { EditorFactory.getInstance().releaseEditor(editor) }
  }

  fun putLogcatMessages(
    vararg messages: LogcatMessage,
    formatMessage: LogcatMessage.() -> String = LogcatMessage::toString,
  ) {
    val document = editor.document
    messages.forEach {
      val start = document.textLength
      val text = it.formatMessage()
      document.insertString(start, "$text\n")
      document.createRangeMarker(start, start + text.length).apply {
        putUserData(LOGCAT_MESSAGE_KEY, it)
        markers.add(this)
      }
    }
  }
}
