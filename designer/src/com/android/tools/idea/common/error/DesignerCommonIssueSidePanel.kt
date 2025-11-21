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
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
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
import org.jetbrains.annotations.TestOnly
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

/** The side panel to show the detail of issue and its source code if available */
class DesignerCommonIssueSidePanel(
  private val project: Project,
  parentDisposable: Disposable,
  private val fixWithAiActionProvider: (Issue) -> AnAction?,
) : JPanel(BorderLayout()), Disposable {

  private val splitter = OnePixelSplitter(true, 0.5f, 0.1f, 0.9f)
  private val fileToEditorMap = mutableMapOf<VirtualFile, Editor>()

  init {
    Disposer.register(parentDisposable, this)
    splitter.setResizeEnabled(true)
    add(splitter, BorderLayout.CENTER)

    project.messageBus
      .connect(this)
      .subscribe(
        FileEditorManagerListener.FILE_EDITOR_MANAGER,
        object : FileEditorManagerListener {
          override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
            fileToEditorMap.remove(file)?.let { EditorFactory.getInstance().releaseEditor(it) }
          }
        },
      )
  }

  /**
   * Load the data from the given [issueNode]. Return true if there is any content to display, or
   * false otherwise.
   */
  fun loadIssueNode(issueNode: DesignerCommonIssueNode?): Boolean {
    val issue = (issueNode as? IssueNode)?.issue
    splitter.firstComponent =
      issue?.let { DesignerCommonIssueDetailPanel(project, it, fixWithAiActionProvider) }
    return splitter.firstComponent != null
  }

  override fun dispose() {
    fileToEditorMap.values.forEach { EditorFactory.getInstance().releaseEditor(it) }
    fileToEditorMap.clear()
  }

  @TestOnly fun hasFirstComponent() = splitter.firstComponent != null

  @TestOnly fun getFirstSplitterComponent(): JComponent? = splitter.firstComponent
}

/** The side panel to show the details of issue detail in [DesignerCommonIssuePanel]. */
@Suppress("DialogTitleCapitalization")
private class DesignerCommonIssueDetailPanel(
  private val project: Project,
  private val issue: Issue,
  private val fixWithAiActionProvider: (Issue) -> AnAction?,
) : JPanel(BorderLayout()), UiDataProvider {

  init {
    border = JBUI.Borders.empty(18, 12, 0, 0)
    add(createTitle(), BorderLayout.NORTH)
    add(createContent(), BorderLayout.CENTER)
    createBottomPanel(issue.messageTips, issue.hyperlinkListener)?.let {
      add(it, BorderLayout.SOUTH)
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[PlatformDataKeys.VIRTUAL_FILE] = issue.source.files.firstOrNull()
  }

  private fun createTitle() = JBLabel(issue.summary).apply { font = font.deriveFont(Font.BOLD) }

  private fun createContent(): JComponent {
    val descriptionPane = DescriptionEditorPane()
    descriptionPane.addHyperlinkListener(issue.hyperlinkListener)
    descriptionPane.alignmentX = LEFT_ALIGNMENT
    descriptionPane.readHTML("<html><body>${issue.description}</body></html>")
    var alignment = BorderLayout.SOUTH
    if (issue is VisualLintRenderIssue) {
      alignment = BorderLayout.NORTH
    }
    val contentPanel = JPanel(BorderLayout()).apply { add(descriptionPane, alignment) }

    if (issue is VisualLintRenderIssue) {
      contentPanel.addVisualRenderIssue(issue)
    }

    if (StudioFlags.COMPOSE_RENDER_ERROR_FIX_WITH_AI.get()) {
      addFixWithAiButton(contentPanel, offSetBottom = true)
    }

    return JBScrollPane(
        contentPanel,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
      )
      .apply { border = JBUI.Borders.emptyTop(12) }
  }

  private fun createBottomPanel(
    tips: List<MessageTip>,
    hyperlinkListener: HyperlinkListener?,
  ): JComponent? {
    if (tips.isEmpty()) return null
    return JBPanel<JBPanel<*>>(VerticalLayout(1)).apply {
      border = JBUI.Borders.empty(3, 2)
      tips.forEach { add(createMessageTip(it, hyperlinkListener)) }
    }
  }

  private fun createMessageTip(tip: MessageTip, hyperlinkListener: HyperlinkListener?): JComponent {
    return JBPanel<JBPanel<*>>(HorizontalLayout(1)).apply {
      add(
        JBLabel(tip.icon).apply {
          verticalAlignment = SwingConstants.TOP
          border = JBUI.Borders.empty(3)
        }
      )
      add(
        object : JBLabel(tip.htmlText) {
          init {
            isAllowAutoWrapping = true
            setCopyable(true)
          }

          override fun createHyperlinkListener(): HyperlinkListener {
            return hyperlinkListener ?: super.createHyperlinkListener()
          }
        }
      )
    }
  }

  private fun JPanel.addVisualRenderIssue(issue: VisualLintRenderIssue) {
    val affectedFilePanel = createAffectedFilePanel(issue)
    if (StudioFlags.COMPOSE_UI_CHECK_FIX_WITH_AI.get()) {
      addFixWithAiButton(affectedFilePanel, offSetBottom = false)
    }
    add(affectedFilePanel, BorderLayout.CENTER)
  }

  private fun createAffectedFilePanel(issue: VisualLintRenderIssue): JPanel {
    val projectBasePath = project.basePath ?: return JPanel()
    val relatedFiles = issue.affectedFilesWithNavigatable
    if (relatedFiles.isEmpty()) return JPanel()

    return JPanel().apply {
      border = JBUI.Borders.empty(4, 0)
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(createAffectedFilesTitle())
      relatedFiles.forEach { file ->
        val link = createAffectedFileLink(projectBasePath, file)
        ToolTipManager.sharedInstance().registerComponent(link)
        add(link)
      }
    }
  }

  private fun createAffectedFilesTitle() =
    JBLabel("Affected Files:").apply {
      font = font.deriveFont(Font.BOLD)
      alignmentX = LEFT_ALIGNMENT
      border = JBUI.Borders.empty(4, 0)
    }

  private fun createAffectedFileLink(projectBasePath: String, file: VirtualFile): ActionLink {
    val pathToDisplay =
      FileUtilRt.getRelativePath(projectBasePath, file.path, File.separatorChar, true) ?: file.path
    return object :
        ActionLink(
          pathToDisplay,
          { OpenFileDescriptor(project, file).navigateInEditor(project, true) },
        ) {
        override fun getToolTipText(): String? {
          return if (size.width < minimumSize.width) pathToDisplay else null
        }
      }
      .apply { alignmentX = LEFT_ALIGNMENT }
  }

  private fun addFixWithAiButton(panel: JPanel, offSetBottom: Boolean) {
    fixWithAiActionProvider(issue)?.let {
      val actionToolbar = createToolbar(panel, it)
      val toolbarWrapper =
        JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
          alignmentX = LEFT_ALIGNMENT
          border = if (offSetBottom) JBUI.Borders.emptyBottom(8) else JBUI.Borders.emptyTop(8)
          add(actionToolbar.component)
          add(Box.createVerticalGlue())
        }
      panel.add(toolbarWrapper)
    }
  }

  private fun createToolbar(targetComponent: JComponent, fixWithAiAction: AnAction): ActionToolbar {
    fixWithAiAction.templatePresentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
    val actionGroup = DefaultActionGroup(fixWithAiAction)
    return ActionManager.getInstance()
      .createActionToolbar("DesignerCommonIssuesToolbar", actionGroup, true)
      .apply {
        this.targetComponent = targetComponent
        component.isOpaque = true
        component.border = JBUI.Borders.empty()
      }
  }
}
