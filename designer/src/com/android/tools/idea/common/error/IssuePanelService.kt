/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.editor.DesignToolsSplitEditor
import com.android.tools.idea.common.editor.SplitEditor
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.typeOf
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.uibuilder.editor.multirepresentation.sourcecode.SourceCodePreview
import com.android.tools.idea.uibuilder.type.DrawableFileType
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.android.tools.idea.uibuilder.type.MenuFileType
import com.android.tools.idea.uibuilder.type.PreferenceScreenFileType
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.ide.DataManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.ColorUtil.toHtmlColor
import com.intellij.ui.content.Content
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.core.util.toPsiFile

private const val DEFAULT_SHARED_ISSUE_PANEL_TAB_NAME = "Designer"

/**
 * A service to help to show the issues of Design Tools in IJ's Problems panel.
 */
class IssuePanelService(private val project: Project) {

  /**
   * The shared issue panel between all tools.
   * This is the temp solution to replace the nested issue panel of all design tools.
   *
   * Some design tools should have independent tab, which will be added in the feature.
   * This feature is rely on [com.android.tools.idea.flags.StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS] flag.
   */
  private var sharedIssueTab: Content? = null
  private var sharedIssuePanel: DesignerCommonIssuePanel? = null

  private val initLock = Any()
  private var inited = false

  init {
    val manager = ToolWindowManager.getInstance(project)
    val problemsView = manager.getToolWindow(ProblemsView.ID)
    if (problemsView != null && !problemsView.isDisposed) {
      // ProblemsView has registered, init the tab.
      UIUtil.invokeLaterIfNeeded { initIssueTabs(problemsView) }
    }
    else {
      val connection = project.messageBus.connect()
      val listener: ToolWindowManagerListener
      listener = object : ToolWindowManagerListener {
        override fun toolWindowsRegistered(ids: MutableList<String>, toolWindowManager: ToolWindowManager) {
          if (ProblemsView.ID in ids) {
            val problemsViewToolWindow = ProblemsView.getToolWindow(project)
            if (problemsViewToolWindow != null && !problemsViewToolWindow.isDisposed) {
              initIssueTabs(problemsViewToolWindow)
              connection.disconnect()
            }
          }
        }
      }
      connection.subscribe(ToolWindowManagerListener.TOPIC, listener)
    }
  }

  fun initIssueTabs(problemsViewWindow: ToolWindow) {
    synchronized(initLock) {
      if (inited) {
        return
      }
      inited = true
    }

    // This is the only common issue panel.
    val contentManager = problemsViewWindow.contentManager
    val contentFactory = contentManager.factory

    // The shared issue panel for all design tools.
    if (StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      val issuePanel = DesignerCommonIssuePanel(project, project, DesignToolsIssueProvider(project))

      sharedIssuePanel = issuePanel
      contentFactory.createContent(issuePanel.getComponent(), "Design Issue", true).apply {
        sharedIssueTab = this
        isCloseable = false
        contentManager.addContent(this@apply)
      }
    }

    project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (!source.hasOpenFiles()) {
          // There is no opened file, remove the tab.
          removeSharedIssueTabFromProblemsPanel()
        }
      }

      override fun selectionChanged(event: FileEditorManagerEvent) {
        val newFile = event.newFile
        if (newFile == null) {
          setSharedIssuePanelVisibility(false)
          return
        }
        if (isComposeFile(newFile)) {
          addSharedIssueTabToProblemsPanel()
          updateSharedIssuePanelTabName()
          selectSharedIssuePanelTab()
          return
        }
        val surface = getDesignSurface(event.newEditor)
        if (surface != null) {
          val psiFile = newFile.toPsiFile(project)?.typeOf()
          if (psiFile is DrawableFileType) {
            // We don't support Shared issue panel for Drawable files.
            removeSharedIssueTabFromProblemsPanel()
          }
          else {
            addSharedIssueTabToProblemsPanel()
            updateSharedIssuePanelTabName()
            selectSharedIssuePanelTab()
          }
        }
        else {
          removeSharedIssueTabFromProblemsPanel()
        }
      }
    })

    if (StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      // If the shared issue panel is initialized after opening editor, the message bus misses the file editor event.
      // This may happen when opening a project. Make sure the initial status is correct here.
      val visibleAfterInit = FileEditorManager.getInstance(project).selectedEditors.any {
        isDesignEditor(it) || (it.file?.let { file -> isComposeFile(file) } ?: false)
      }
      setSharedIssuePanelVisibility(visibleAfterInit)
    }
  }

  private fun selectSharedIssuePanelTab() {
    val tab = sharedIssueTab ?: return
    tab.manager?.setSelectedContent(tab)
  }

  /**
   * Remove the [sharedIssueTab] from Problems Tool Window. Return true if the [sharedIssueTab] is removed successfully, or false if
   * the [sharedIssueTab] doesn't exist or the [sharedIssueTab] is not in the Problems Tool Window (e.g. has been removed before).
   */
  private fun removeSharedIssueTabFromProblemsPanel(): Boolean {
    val tab = sharedIssueTab ?: return false
    val toolWindow = ProblemsView.getToolWindow(project) ?: return false
    toolWindow.contentManager.removeContent(tab, false)
    return true
  }

  /**
   * Add the [sharedIssueTab] into Problems Tool Window. Return true if the [sharedIssueTab] is added successfully, or false if the
   * [sharedIssueTab] doesn't exist or the [sharedIssueTab] is in the Problems Tool Window already (e.g. has been added before).
   */
  private fun addSharedIssueTabToProblemsPanel(): Boolean {
    val tab = sharedIssueTab ?: return false
    val toolWindow = ProblemsView.getToolWindow(project) ?: return false
    if (toolWindow.contentManager.contents.contains(tab)) {
      return false
    }
    toolWindow.contentManager.addContent(tab, 2)
    return true
  }

  /**
   * Return if the current issue panel of the given [DesignSurface] or the shared issue panel is showing.
   */
  fun isShowingIssuePanel(surface: DesignSurface) : Boolean {
    if (StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      return isSharedIssueTabShowing(sharedIssueTab)
    }
    return !surface.issuePanel.isMinimized
  }

  /**
   * Set the visibility of shared issue panel.
   * When [visible] is true, this opens the problem panel and switch the tab to shared issue panel tab.
   */
  fun setSharedIssuePanelVisibility(visible: Boolean) {
    val tab = sharedIssueTab ?: return
    val problemsViewPanel = ProblemsView.getToolWindow(project) ?: return
    if (visible) {
      if (!isSharedIssueTabShowing(tab)) {
        problemsViewPanel.show {
          updateSharedIssuePanelTabName()
          selectSharedIssuePanelTab()
        }
      }
    }
    else {
      problemsViewPanel.hide()
    }
  }

  /**
   * Update the tab name (includes the issue count) of shared issue panel.
   */
  private fun updateSharedIssuePanelTabName() {
    val tab = sharedIssueTab ?: return
    val count = sharedIssuePanel?.issueProvider?.getFilteredIssues()?.distinct()?.size
    tab.displayName = createTabName(getSharedIssuePanelTabTitle(), count)
  }

  /**
   * Get the title of shared issue panel. The returned string doesn't include the issue count.
   */
  private fun getSharedIssuePanelTabTitle(): String {
    val editors = FileEditorManager.getInstance(project).selectedEditors ?: return DEFAULT_SHARED_ISSUE_PANEL_TAB_NAME
    if (editors.size != 1) {
      // TODO: What tab name should be show when opening multiple file editor?
      return DEFAULT_SHARED_ISSUE_PANEL_TAB_NAME
    }
    val file = editors[0].file ?: return DEFAULT_SHARED_ISSUE_PANEL_TAB_NAME
    if (isComposeFile(file)) {
      return "Compose"
    }
    val surface = getDesignSurface(editors[0]) ?: return DEFAULT_SHARED_ISSUE_PANEL_TAB_NAME
    if (surface.name != null) {
      return surface.name
    }
    return when (file.toPsiFile(project)?.typeOf()) {
      is LayoutFileType -> "Layout and Qualifiers"
      is PreferenceScreenFileType -> "Preference"
      is MenuFileType -> "Menu"
      else -> DEFAULT_SHARED_ISSUE_PANEL_TAB_NAME
    }
  }

  /**
   * Return true if the given editor is Design Editor (e.g. compose editor, layout editor, navigation editor, ...)
   */
  private fun isDesignEditor(editor: FileEditor): Boolean {
    val virtualFile = editor.file ?: return false
    return isComposeFile(virtualFile) || getDesignSurface(editor) != null
  }

  private fun isComposeFile(file: VirtualFile): Boolean {
    val extension = file.extension
    val fileType = file.fileType
    val psiFile = file.toPsiFile(project)
    return extension == KotlinFileType.INSTANCE.defaultExtension &&
           fileType == KotlinFileType.INSTANCE &&
           psiFile?.getModuleSystem()?.usesCompose == true
  }

  private fun getDesignSurface(editor: FileEditor?): DesignSurface? {
    when (editor) {
      is DesignToolsSplitEditor -> return editor.designerEditor.component.surface
      is SplitEditor<*> -> {
        // Check if there is a design surface in the context of presentation. For example, Compose and CustomView preview.
        val component = (editor.preview as? SourceCodePreview)?.currentRepresentation?.component ?: return null
        return DataManager.getInstance().getDataContext(component).getData(DESIGN_SURFACE)
      }
      else -> return null
    }
  }

  /**
   * Select the highest severity issue related to the provided [NlComponent] and scroll the viewport to issue.
   * TODO: Remove the dependency of [NlComponent]
   */
  fun showIssueForComponent(surface: DesignSurface, userInvoked: Boolean, component: NlComponent, collapseOthers: Boolean) {
    // TODO: The shared issue panel should support this feature.
    if (StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      setSharedIssuePanelVisibility(true)
    }
    else {
      val issuePanel = surface.issuePanel
      val issueModel = surface.issueModel
      val issue: Issue = issueModel.getHighestSeverityIssue(component) ?: return
      val issueView = issuePanel.getDisplayIssueView(issue)
      if (issueView != null) {
        surface.setIssuePanelVisibility(true, userInvoked)
        if (collapseOthers) {
          issueModel.issues.filter { it != issue }.mapNotNull { issuePanel.getDisplayIssueView(it) }.forEach { it.setExpanded(false) }
          issueModel.issues.mapNotNull { issuePanel.getDisplayIssueView(it) }.filter { it.issue != issue }.forEach { it.setExpanded(false) }
        }
        issuePanel.scrollToIssueView(issueView)
      }
    }
  }

  /**
   * Return the visibility of issue panel for the given [DesignSurface].
   */
  fun isIssuePanelVisible(surface: DesignSurface): Boolean {
    return if (StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      isSharedIssueTabShowing(sharedIssueTab)
    }
    else {
      !surface.issuePanel.isMinimized
    }
  }

  /**
   * Return true if IJ's problem panel is visible and selecting the given [tab], false otherwise.
   */
  private fun isSharedIssueTabShowing(tab: Content?): Boolean {
    if (tab == null) {
      return false
    }
    val problemsViewPanel = ProblemsView.getToolWindow(project) ?: return false
    if (!problemsViewPanel.isVisible || tab !in problemsViewPanel.contentManager.contents) {
      return false
    }
    return tab.isSelected
  }

  /**
   * Get the issue panel for the given [DesignSurface], if any.
   */
  fun getIssuePanel(surface: DesignSurface): IssuePanel? {
    if (!StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      return surface.issuePanel
    }
    return null
  }

  fun getSelectedSharedIssuePanel(): DesignerCommonIssuePanel? {
    return if (sharedIssueTab?.isSelected == true) sharedIssuePanel else null
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): IssuePanelService = project.getService(IssuePanelService::class.java)
  }
}

/**
 * Helper function to set the issue panel in [DesignSurface].
 * @param show whether to show or hide the issue panel.
 * @param userInvoked if true, this was the direct consequence of a user action.
 */
fun DesignSurface.setIssuePanelVisibility(show: Boolean, userInvoked: Boolean) {
  UIUtil.invokeLaterIfNeeded {
    issuePanel.isMinimized = !show
    if (userInvoked) {
      issuePanel.disableAutoSize()
    }
    revalidate()
    repaint()
  }
}

/**
 * This is same as [com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel.getName] for consistency.
 */
private fun createTabName(title: String, issueCount: Int?): String {
  if (issueCount == null || issueCount <= 0) {
    return title
  }
  return HtmlBuilder()
    .append(title)
    .append(" ").append(HtmlChunk.tag("font").attr("color", toHtmlColor(UIUtil.getInactiveTextColor())).addText(issueCount.toString()))
    .wrapWithHtmlBody()
    .toString()
}
