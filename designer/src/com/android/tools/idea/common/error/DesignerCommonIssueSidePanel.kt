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
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.ui.DescriptionEditorPane
import com.intellij.profile.codeInspection.ui.readHTML
import com.intellij.profile.codeInspection.ui.toHTML
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.io.File
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.ToolTipManager

/**
 * The side panel to show the detail of issue and its source code if available
 */
class DesignerCommonIssueSidePanel(private val project: Project, parentDisposable: Disposable) : JPanel(BorderLayout()), Disposable {

  private val splitter: OnePixelSplitter = OnePixelSplitter(true, 0.5f, 0.1f, 0.9f)

  private val fileToEditorMap = mutableMapOf<VirtualFile, Editor>()

  init {
    Disposer.register(parentDisposable, this)
    splitter.firstComponent = null
    splitter.secondComponent = null
    splitter.setResizeEnabled(true)
    add(splitter, BorderLayout.CENTER)

    project.messageBus.connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        fileToEditorMap.remove(file)?.let { editor -> EditorFactory.getInstance().releaseEditor(editor) }
      }
    })
  }

  /**
   * Load the data from the given [issueNode]. Return true if there is any content to display, or false otherwise.
   */
  fun loadIssueNode(issueNode: DesignerCommonIssueNode?): Boolean {
    splitter.firstComponent = (issueNode as? IssueNode)?.let { node -> DesignerCommonIssueDetailPanel(project, node.issue) }

    if (issueNode == null) {
      splitter.secondComponent = null
      return false
    }
    val editor = issueNode.getVirtualFile()?.let { getEditor(it) }
    if (editor != null) {
      editor.setBorder(JBUI.Borders.empty())
      val navigable = issueNode.getNavigatable()
      if (navigable is OpenFileDescriptor) {
        navigable.navigateIn(editor)
      }
    }
    splitter.secondComponent = editor?.component

    return splitter.firstComponent != null || splitter.secondComponent != null
  }

  private fun getEditor(file: VirtualFile): Editor? {
    val editor = fileToEditorMap[file]
    if (editor != null) {
      return editor
    }
    val document = ProblemsView.getDocument(project, file) ?: return null
    val newEditor = EditorFactory.getInstance().createEditor(document, project, file, false, EditorKind.PREVIEW)
    if (newEditor != null) {
      fileToEditorMap[file] = newEditor
    }
    return newEditor
  }

  override fun dispose() {
    for (editor in fileToEditorMap.values) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    fileToEditorMap.clear()
  }

  @TestOnly
  fun hasFirstComponent() = splitter.firstComponent != null

  @TestOnly
  fun hasSecondComponent() = splitter.secondComponent != null
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
          val link = object: ActionLink(pathToDisplay, { OpenFileDescriptor(project, file).navigateInEditor(project, true) }) {
            override fun getToolTipText(): String? {
              return if (size.width < minimumSize.width) {
                pathToDisplay
              } else {
                null
              }
            }
          }
          ToolTipManager.sharedInstance().registerComponent(link)
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
