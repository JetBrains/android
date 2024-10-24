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
package com.android.tools.idea.insights.ui.insight

import com.android.tools.idea.insights.ui.AqiHtmlRenderer
import com.android.tools.idea.insights.ui.MarkDownConverter
import com.intellij.icons.AllIcons
import com.intellij.ide.CopyProvider
import com.intellij.ide.actions.CopyAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.StartupUiUtil
import javax.swing.JTextPane
import javax.swing.text.DefaultCaret

private const val EMPTY_PARAGRAPH = "<p></p>"

/** [JTextPane] that displays the AI insight. */
class InsightTextPane : JTextPane(), CopyProvider {

  private val markDownConverter = MarkDownConverter { AqiHtmlRenderer(it) }

  init {
    contentType = "text/html"
    editorKit =
      HTMLEditorKitBuilder.simple().apply { styleSheet.addRule("body { white-space: pre-wrap; }") }
    isEditable = false
    isOpaque = false
    font = StartupUiUtil.labelFont

    val actionManager = ActionManager.getInstance()
    val group =
      DefaultActionGroup(
        CopyAction().apply {
          templatePresentation.text = "Copy"
          templatePresentation.icon = AllIcons.Actions.Copy
        }
      )
    val popupMenu = actionManager.createActionPopupMenu("InsightTextPaneContextMenu", group)
    componentPopupMenu = popupMenu.component
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

  override fun performCopy(dataContext: DataContext) =
    CopyPasteManager.copyTextToClipboard(selectedText ?: renderedText.trimIndent())

  override fun isCopyEnabled(dataContext: DataContext) = renderedText.isNotBlank()

  override fun isCopyVisible(dataContext: DataContext) = true

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  private val renderedText: String
    get() = document.getText(0, document.length)
}
