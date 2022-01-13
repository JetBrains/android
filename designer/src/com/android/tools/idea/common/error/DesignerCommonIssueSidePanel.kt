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
package com.android.tools.idea.common.error

import com.android.tools.adtui.common.primaryContentBackground
import com.android.utils.HtmlBuilder
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBHtmlEditorKit
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.BoxLayout
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.text.Element
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

/**
 * The side panel to show the detail of issue and its source code if available
 */
class DesignerCommonIssueSidePanel(private val project: Project, issue: Issue, private val file: VirtualFile) : JPanel(BorderLayout()) {

  private val splitter: OnePixelSplitter = OnePixelSplitter(true, 0.5f, 0.1f, 0.9f)
  val editor: Editor?

  init {
    splitter.firstComponent = DesignerCommonIssueDetailPanel(issue)

    editor = createEditor()
    if (editor != null) {
      splitter.secondComponent = editor.component
      splitter.setResizeEnabled(true)
    }
    add(splitter, BorderLayout.CENTER)
  }

  private fun createEditor(): Editor? {
    val document = ProblemsView.getDocument(project, file) ?: return null
    return EditorFactory.getInstance().createEditor(document, project, EditorKind.PREVIEW)
  }
}


/**
 * The side panel to show the details of issue detail in [DesignerCommonIssuePanel].
 */
private class DesignerCommonIssueDetailPanel(issue: Issue) : JPanel(BorderLayout()) {
  private val content = JPanel(BorderLayout())
  private val sourceLabel: JLabel = JLabel()
  private val errorTitle: JBLabel = JBLabel()
  private val errorDescription: JTextPane = JTextPane()
  private val fixPanel: JPanel = JPanel()

  init {
    background = primaryContentBackground

    val displayText = issue.source.displayText
    if (displayText.isEmpty()) {
      sourceLabel.isVisible = false
    }
    else {
      sourceLabel.text = displayText
    }

    val scrollPanel = JBScrollPane(content)
    add(scrollPanel, BorderLayout.CENTER)
    content.border = JBUI.Borders.empty(4)
    content.background = primaryContentBackground

    setupTitle()
    setupDescriptionPanel(issue)
    setupFixPanel(issue)

    content.add(errorTitle, BorderLayout.NORTH)
    content.add(errorDescription, BorderLayout.CENTER)
    content.add(fixPanel, BorderLayout.SOUTH)

    errorTitle.background = primaryContentBackground
    errorDescription.background = primaryContentBackground
    fixPanel.background = primaryContentBackground

    errorDescription.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
  }

  private fun setupTitle() {
    errorTitle.font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
  }

  private fun setupDescriptionPanel(issue: Issue) {
    errorDescription.editorKit = IssueHTMLEditorKit()
    errorDescription.addHyperlinkListener(issue.hyperlinkListener)
    errorDescription.font = UIUtil.getToolTipFont()

    errorDescription.text = updateImageSize(HtmlBuilder().openHtmlBody().addHtml(issue.description).closeHtmlBody().html,
                                            UIUtil.getFontSize(UIUtil.FontSize.NORMAL).toInt())
  }

  private fun setupFixPanel(issue: Issue) {
    fixPanel.layout = BoxLayout(fixPanel, BoxLayout.Y_AXIS)
    issue.fixes.forEach { fix: Issue.Fix -> createFixEntry(fix) }
    fixPanel.isVisible = fixPanel.componentCount > 0
  }

  private fun createFixEntry(fix: Issue.Fix) {
    fixPanel.add(IssueView.FixEntry(fix.buttonText, fix.description, fix.runnable))
  }

  private fun updateImageSize(html: String, size: Int): String {
    return html.replace("(<img .+ width=)[0-9]+( height=)[0-9]+".toRegex(), "$1$size$2$size")
  }
}

private class IssueHTMLEditorKit : HTMLEditorKit() {
  var style = createStyleSheet()
  fun createStyleSheet(): StyleSheet {
    val style = StyleSheet()
    style.addStyleSheet(JBHtmlEditorKit.createStyleSheet())
    style.addRule("body { font-family: Sans-Serif; }")
    style.addRule("code { font-size: 100%; font-family: monospace; }") // small by Swing's default
    style.addRule("small { font-size: small; }") // x-small by Swing's default
    style.addRule("a { text-decoration: none;}")
    return style
  }

  override fun getStyleSheet(): StyleSheet {
    return style
  }

  override fun createInputAttributes(element: Element, set: MutableAttributeSet) {
    // Do Nothing, the super implementation stripped out the <BR/> tags but we need them
  }
}
