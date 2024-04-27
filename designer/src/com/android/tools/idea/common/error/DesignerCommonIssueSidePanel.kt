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

import com.android.tools.idea.rendering.errors.ui.MessageTip
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.utils.HtmlBuilder
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.designer.model.EmptyXmlTag
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.ui.DescriptionEditorPane
import com.intellij.profile.codeInspection.ui.readHTML
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Font
import java.io.File
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.ToolTipManager
import javax.swing.event.HyperlinkListener

/** The side panel to show the detail of issue and its source code if available */
class DesignerCommonIssueSidePanel(private val project: Project, parentDisposable: Disposable) :
  JPanel(BorderLayout()), Disposable {

  private val splitter: OnePixelSplitter = OnePixelSplitter(true, 0.5f, 0.1f, 0.9f)

  private val fileToEditorMap = mutableMapOf<VirtualFile, Editor>()

  init {
    Disposer.register(parentDisposable, this)
    splitter.firstComponent = null
    splitter.secondComponent = null
    splitter.setResizeEnabled(true)
    add(splitter, BorderLayout.CENTER)

    project.messageBus
      .connect(this)
      .subscribe(
        FileEditorManagerListener.FILE_EDITOR_MANAGER,
        object : FileEditorManagerListener {
          override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
            fileToEditorMap.remove(file)?.let { editor ->
              EditorFactory.getInstance().releaseEditor(editor)
            }
          }
        }
      )
  }

  /**
   * Load the data from the given [issueNode]. Return true if there is any content to display, or
   * false otherwise.
   */
  fun loadIssueNode(issueNode: DesignerCommonIssueNode?): Boolean {
    splitter.firstComponent =
      (issueNode as? IssueNode)?.let { node -> DesignerCommonIssueDetailPanel(project, node.issue) }

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
    val newEditor =
      (EditorFactory.getInstance().createViewer(document, project, EditorKind.PREVIEW) as EditorEx)
        .apply {
          setCaretEnabled(false)
          isEmbeddedIntoDialogWrapper = true
          contentComponent.isOpaque = false
          setBorder(JBUI.Borders.empty())
          settings.setGutterIconsShown(false)
        }
    fileToEditorMap[file] = newEditor
    return newEditor
  }

  override fun dispose() {
    for (editor in fileToEditorMap.values) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    fileToEditorMap.clear()
  }

  @TestOnly fun hasFirstComponent() = splitter.firstComponent != null

  @TestOnly fun hasSecondComponent() = splitter.secondComponent != null
}

/** The side panel to show the details of issue detail in [DesignerCommonIssuePanel]. */
@Suppress("DialogTitleCapitalization")
private class DesignerCommonIssueDetailPanel(project: Project, issue: Issue) : JPanel() {

  init {
    border = JBUI.Borders.empty(18, 12, 0, 0)
    layout = BorderLayout()

    val title =
      JBLabel().apply {
        text = issue.summary
        font = font.deriveFont(Font.BOLD)
      }
    add(title, BorderLayout.NORTH)

    issue.messageTips
      .takeIf { it.isNotEmpty() }
      ?.let { add(createBottomPanel(it, issue.hyperlinkListener), BorderLayout.SOUTH) }

    val contentPanel = JPanel()
    contentPanel.layout = BorderLayout()
    val scrollPane =
      JBScrollPane(
        contentPanel,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      )
    scrollPane.border = JBUI.Borders.emptyTop(12)
    add(scrollPane, BorderLayout.CENTER)

    val descriptionEditorPane = DescriptionEditorPane()
    descriptionEditorPane.addHyperlinkListener(issue.hyperlinkListener)
    descriptionEditorPane.alignmentX = LEFT_ALIGNMENT
    contentPanel.add(descriptionEditorPane, BorderLayout.NORTH)

    val description = HtmlBuilder().openHtmlBody().addHtml(issue.description).closeHtmlBody().html
    descriptionEditorPane.readHTML(description)

    if (issue is VisualLintRenderIssue) {
      contentPanel.addVisualRenderIssue(issue, project)
    }
  }

  private fun createBottomPanel(issues: List<MessageTip>?, hyperlinkListener: HyperlinkListener?) =
    JBPanel<JBPanel<*>>(VerticalLayout(1)).apply {
      border = JBUI.Borders.empty(3, 2)
      issues?.forEach { messageType -> add(createMessageTip(messageType, hyperlinkListener)) }
    }

  private fun createMessageTip(messageType: MessageTip, hyperlinkListener: HyperlinkListener?) =
    JBPanel<JBPanel<*>>(HorizontalLayout(1)).apply {
      add(
        JBLabel(messageType.icon).apply {
          verticalAlignment = SwingConstants.TOP
          border = JBUI.Borders.empty(3)
        }
      )

      add(
        object : JBLabel(messageType.htmlText) {
          init {
            isAllowAutoWrapping = true
            setCopyable(true)
          }

          override fun createHyperlinkListener(): HyperlinkListener {
            return hyperlinkListener.takeIf { it != null } ?: super.createHyperlinkListener()
          }
        },
      )
    }

  private fun VisualLintRenderIssue.getAffectedFiles(): List<VirtualFile> {
    val modelFiles =
      models
        .filter { model -> this.shouldHighlight(model) }
        .map {
          @Suppress("UnstableApiUsage") BackedVirtualFile.getOriginFileIfBacked(it.virtualFile)
        }
        .distinct()
    val navigatableFile =
      (components.firstOrNull { it.tag == EmptyXmlTag.INSTANCE }?.navigatable
          as? OpenFileDescriptor)
        ?.file
    return if (navigatableFile == null || modelFiles.contains(navigatableFile)) {
      modelFiles
    } else {
      modelFiles.toMutableList().apply { add(navigatableFile) }
    }
  }

  private fun JPanel.addVisualRenderIssue(
    issue: VisualLintRenderIssue,
    project: Project,
  ) {
    val affectedFilePanel = JPanel().apply { border = JBUI.Borders.empty(4, 0) }
    affectedFilePanel.layout = BoxLayout(affectedFilePanel, BoxLayout.Y_AXIS)

    val projectBasePath = project.basePath
    if (projectBasePath != null) {
      val relatedFiles = issue.getAffectedFiles()
      if (relatedFiles.isNotEmpty()) {
        affectedFilePanel.add(
          JBLabel("Affected Files:").apply {
            font = font.deriveFont(Font.BOLD)
            alignmentX = LEFT_ALIGNMENT
            border = JBUI.Borders.empty(4, 0)
          },
        )
      }
      for (file in relatedFiles) {
        val pathToDisplay =
          FileUtilRt.getRelativePath(projectBasePath, file.path, File.separatorChar, true)
            ?: continue
        val link =
          object :
            ActionLink(
              pathToDisplay,
              { OpenFileDescriptor(project, file).navigateInEditor(project, true) },
            ) {
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
    add(affectedFilePanel, BorderLayout.CENTER)
  }
}
