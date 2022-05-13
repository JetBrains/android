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

import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.utils.HtmlBuilder
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.ui.DescriptionEditorPane
import com.intellij.profile.codeInspection.ui.readHTML
import com.intellij.profile.codeInspection.ui.toHTML
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.Link
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.io.File
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/**
 * The side panel to show the detail of issue and its source code if available
 */
class DesignerCommonIssueSidePanel(private val project: Project,
                                   issue: Issue,
                                   private val file: VirtualFile?,
                                   parentDisposable: Disposable) : JPanel(BorderLayout()), Disposable {

  private val splitter: OnePixelSplitter = OnePixelSplitter(true, 0.5f, 0.1f, 0.9f)
  val editor: Editor?

  init {
    Disposer.register(parentDisposable, this)
    splitter.firstComponent = DesignerCommonIssueDetailPanel(project, issue)

    editor = createEditor()
    if (editor != null) {
      editor.setBorder(JBUI.Borders.empty())
      splitter.secondComponent = editor.component
      splitter.setResizeEnabled(true)
    }
    add(splitter, BorderLayout.CENTER)
  }

  private fun createEditor(): Editor? {
    if (file == null) {
      return null
    }
    val document = ProblemsView.getDocument(project, file) ?: return null
    return EditorFactory.getInstance().createEditor(document, project, file, false, EditorKind.PREVIEW)
  }

  override fun dispose() {
    if (editor != null) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
  }
}


/**
 * The side panel to show the details of issue detail in [DesignerCommonIssuePanel].
 */
@Suppress("DialogTitleCapitalization")
private class DesignerCommonIssueDetailPanel(project: Project, issue: Issue) : JPanel() {

  init {
    border = JBUI.Borders.empty(18, 12, 0, 0)
    layout = BorderLayout()

    val title = JBLabel().apply {
      text = issue.summary
      font = font.deriveFont(Font.BOLD)
    }
    add(title, BorderLayout.NORTH)

    val contentPanel = JPanel()
    contentPanel.layout = BorderLayout()
    val scrollPane = JBScrollPane(contentPanel,
                                  ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                  ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    scrollPane.border = JBUI.Borders.empty(12, 0, 0, 0)
    add(scrollPane, BorderLayout.CENTER)

    val descriptionEditorPane = DescriptionEditorPane()
    descriptionEditorPane.addHyperlinkListener(issue.hyperlinkListener)
    descriptionEditorPane.alignmentX = LEFT_ALIGNMENT
    contentPanel.add(descriptionEditorPane, BorderLayout.NORTH)

    val description = updateImageSize(HtmlBuilder().openHtmlBody().addHtml(issue.description).closeHtmlBody().html,
                                      UIUtil.getFontSize(UIUtil.FontSize.NORMAL).toInt())
    descriptionEditorPane.readHTML(descriptionEditorPane.toHTML(description, false))

    if (issue is VisualLintRenderIssue) {
      val affectedFilePanel = JPanel().apply { border = JBUI.Borders.empty(4, 0) }
      affectedFilePanel.layout = BoxLayout(affectedFilePanel, BoxLayout.Y_AXIS)

      val projectBasePath = project.basePath
      if (projectBasePath != null) {
        val relatedFiles = issue.models.filter { model -> issue.shouldHighlight(model) }.map { it.virtualFile }.distinct()
        if (relatedFiles.isNotEmpty()) {
          affectedFilePanel.add(JBLabel("Affected Files:").apply {
            font = font.deriveFont(Font.BOLD)
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(4, 0)
          })
        }
        for (file in relatedFiles) {
          val pathToDisplay = FileUtilRt.getRelativePath(projectBasePath, file.path, File.separatorChar, true) ?: continue
          val link = Link(pathToDisplay, null) {
            OpenFileDescriptor(project, file).navigateInEditor(project, true)
            alignmentX = LEFT_ALIGNMENT
          }
          affectedFilePanel.add(link)
        }
      }
      contentPanel.add(affectedFilePanel, BorderLayout.CENTER)
    }
  }
}

private fun updateImageSize(html: String, size: Int): String {
  return html.replace("(<img .+ width=)[0-9]+( height=)[0-9]+".toRegex(), "$1$size$2$size")
}
