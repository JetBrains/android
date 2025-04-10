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

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.CopyProvider
import com.intellij.ide.actions.CopyAction
import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneConfiguration
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import javax.swing.JTextPane
import javax.swing.event.HyperlinkEvent.EventType
import javax.swing.text.DefaultCaret
import javax.swing.text.html.HTMLEditorKit
import org.jetbrains.annotations.Nls

private const val EMPTY_PARAGRAPH = "<p></p>"

/** [JTextPane] that displays the AI insight. */
@Suppress("UnstableApiUsage")
class InsightTextPane(private val project: Project) : JBHtmlPane(), CopyProvider {

  init {
    isEditable = false
    isOpaque = false
    (editorKit as HTMLEditorKit).apply {
      with(styleSheet) { addRule("body { white-space: pre-wrap; }") }
    }

    font = StartupUiUtil.labelFont

    addHyperlinkListener {
      if (it.eventType == EventType.ACTIVATED) {
        BrowserUtil.browse(it.url)
      }
    }

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
    border = JBUI.Borders.empty()
  }

  override fun initializePaneConfiguration(builder: JBHtmlPaneConfiguration.Builder) {
    builder.underlinedHoveredHyperlink = true
  }

  override fun setText(text: @Nls String?) {
    if (text == null || text.isBlank()) {
      // Set empty paragraph as text in order to avoid editor kit adding
      // random (un)ordered list tags
      super.setText(EMPTY_PARAGRAPH)
    } else {
      val htmlText = runReadAction { DocMarkdownToHtmlConverter.convert(project, text) }
      super.setText(htmlText)
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
    get() = document.getText(0, document.length).replace("\u200B", "")
}
