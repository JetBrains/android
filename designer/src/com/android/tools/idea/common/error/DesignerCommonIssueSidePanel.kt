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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.errors.ui.MessageTip
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.utils.HtmlBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
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
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.ToolTipManager
import javax.swing.event.HyperlinkListener
import org.jetbrains.annotations.TestOnly

/** The side panel to show the detail of issue and its source code if available */
class DesignerCommonIssueSidePanel(
  private val project: Project,
  parentDisposable: Disposable,
  private val fixWithAiActionProvider: (Issue) -> AnAction?,
) : JPanel(BorderLayout()), Disposable {

  private val splitter: OnePixelSplitter = OnePixelSplitter(true, 0.5f, 0.1f, 0.9f)

  private val fileToEditorMap = mutableMapOf<VirtualFile, Editor>()

  init {
    Disposer.register(parentDisposable, this)
    splitter.firstComponent = null
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
        },
      )
  }

  /**
   * Load the data from the given [issueNode]. Return true if there is any content to display, or
   * false otherwise.
   */
  fun loadIssueNode(issueNode: DesignerCommonIssueNode?): Boolean {
    splitter.firstComponent =
      (issueNode as? IssueNode)?.let { node ->
        DesignerCommonIssueDetailPanel(project, node.issue, fixWithAiActionProvider)
      }
    return splitter.firstComponent != null
  }

  override fun dispose() {
    for (editor in fileToEditorMap.values) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    fileToEditorMap.clear()
  }

  @TestOnly fun hasFirstComponent() = splitter.firstComponent != null
}

/** The side panel to show the details of issue detail in [DesignerCommonIssuePanel]. */
@Suppress("DialogTitleCapitalization")
class DesignerCommonIssueDetailPanel(
  project: Project,
  issue: Issue,
  fixWithAiActionProvider: (Issue) -> AnAction?,
) : JPanel() {

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
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
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
      contentPanel.addVisualRenderIssue(
        issue as VisualLintRenderIssue,
        project,
        fixWithAiActionProvider,
      )
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
        }
      )
    }

  private fun JPanel.addVisualRenderIssue(
    issue: VisualLintRenderIssue,
    project: Project,
    fixWithAiActionProvider: (VisualLintRenderIssue) -> AnAction?,
  ) {
    val affectedFilePanel =
      JPanel().apply {
        border = JBUI.Borders.empty(4, 0)
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
      }

    val projectBasePath = project.basePath
    if (projectBasePath != null) {
      val relatedFiles = issue.affectedFilesWithNavigatable
      if (relatedFiles.isNotEmpty()) {
        affectedFilePanel.add(
          JBLabel("Affected Files:").apply {
            font = font.deriveFont(Font.BOLD)
            alignmentX = LEFT_ALIGNMENT
            border = JBUI.Borders.empty(4, 0)
          }
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
            .apply { alignmentX = LEFT_ALIGNMENT }
        ToolTipManager.sharedInstance().registerComponent(link)
        affectedFilePanel.add(link)
      }
    }

    if (StudioFlags.COMPOSE_UI_CHECK_FIX_WITH_AI.get()) {
      fixWithAiActionProvider(issue)?.let { fixWithAiAction ->
        val actionToolbar = createToolbar(affectedFilePanel, fixWithAiAction)
        val toolbarWrapper =
          JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            border = JBUI.Borders.emptyTop(8)
            add(actionToolbar.component)
            add(Box.createVerticalGlue())
          }
        affectedFilePanel.add(toolbarWrapper)
      }
    }
    add(affectedFilePanel, BorderLayout.CENTER)
  }

  private fun createToolbar(targetComponent: JComponent, fixWithAiAction: AnAction): ActionToolbar {
    fixWithAiAction.templatePresentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
    val actionGroup = DefaultActionGroup().apply { add(fixWithAiAction) }
    return ActionManager.getInstance()
      .createActionToolbar("DesignerCommonIssuesToolbar", actionGroup, true)
      .apply {
        this.targetComponent = targetComponent
        this.component.isOpaque = true
        this.component.border = JBUI.Borders.empty()
      }
  }
}
