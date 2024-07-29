/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import javax.swing.JTextPane
import javax.swing.text.DefaultCaret

private const val EMPTY_PARAGRAPH = "<p></p>"

/** [JTextPane] that displays the AI insight. */
class InsightTextPane : JTextPane() {

  private val markDownConverter = MarkDownConverter { AqiHtmlRenderer(it) }

  init {
    contentType = "text/html"
    editorKit = HTMLEditorKitBuilder.simple()
    isEditable = false
    isOpaque = false
    background = JBColor.background()
    border = JBUI.Borders.empty(8)
    font = StartupUiUtil.labelFont
  }

  override fun setText(text: String) {
    if (text.isBlank()) {
      // Set empty paragraph as text in order to avoid editor kit adding
      // random (un)ordered list tags
      super.setText(EMPTY_PARAGRAPH)
    } else {
      super.setText(markDownConverter.toHtml(text))
    }
    val caret = caret
    if (caret is DefaultCaret) {
      caret.updatePolicy = DefaultCaret.NEVER_UPDATE
    }
    caretPosition = 0
  }
}
