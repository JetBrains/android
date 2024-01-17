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

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.getDesignSurface
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.common.type.typeOf
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_BUILD_TOPIC
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.uibuilder.type.DrawableFileType
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.android.tools.idea.uibuilder.type.MenuFileType
import com.android.tools.idea.uibuilder.type.PreferenceScreenFileType
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.content.Content
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.SlowOperations
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.core.util.toPsiFile

internal const val DEFAULT_SHARED_ISSUE_PANEL_TAB_TITLE = "Designer"
const val SHARED_ISSUE_PANEL_TAB_ID = "_Designer_Tab"

/** A service to help manage the shared issue panel for Design Tools in the Problems tool window. */
@Service(Service.Level.PROJECT)
class IssuePanelService(private val project: Project) : Disposable.Default {

  init {
    val connection = project.messageBus.connect(this)
    connection.subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      object : FileEditorManagerListener {
        override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
          val editor = source.getSelectedEditor(file)
          updateIssuePanelVisibility(file, editor)
        }

        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
          if (!source.hasOpenFiles()) {
            // There is no opened file, remove the tab.
            ProblemsViewToolWindowUtils.removeTab(project, SHARED_ISSUE_PANEL_TAB_ID)
          }
        }

        override fun selectionChanged(event: FileEditorManagerEvent) {
          event.newFile?.let { updateIssuePanelVisibility(it, event.newEditor) }
        }
      }
    )

    connection.subscribe(
      PROJECT_SYSTEM_SYNC_TOPIC,
      ProjectSystemSyncManager.SyncResultListener {
        FileEditorManager.getInstance(project).selectedEditors.forEach {
          updateIssuePanelVisibility(it.file, it)
        }
      }
    )

    connection.subscribe(
      PROJECT_SYSTEM_BUILD_TOPIC,
      object : ProjectSystemBuildManager.BuildListener {
        override fun buildCompleted(result: ProjectSystemBuildManager.BuildResult) {
          FileEditorManager.getInstance(project).selectedEditors.forEach {
            updateIssuePanelVisibility(it.file, it)
          }
        }
      }
    )
  }

  private fun updateIssuePanelVisibility(newFile: VirtualFile, newEditor: FileEditor?) {
    // TODO b/320563607
    SlowOperations.allowSlowOperations(ThrowableComputable {
        if (isSupportedDesignerFileType(newFile)) {
          updateIssuePanelVisibility(newFile)
          return@ThrowableComputable
        }
        val surface = newEditor?.getDesignSurface()
        if (surface != null) {
          updateIssuePanelVisibility(newFile)
        } else {
          ProblemsViewToolWindowUtils.removeTab(project, SHARED_ISSUE_PANEL_TAB_ID)
        }
      }
    )
  }

  private fun updateIssuePanelVisibility(file: VirtualFile) {
    val psiFileType = file.toPsiFile(project)?.typeOf()
    if (psiFileType is DrawableFileType) {
      // We don't support Shared issue panel for Drawable files.
      ProblemsViewToolWindowUtils.removeTab(project, SHARED_ISSUE_PANEL_TAB_ID)
    } else {
      if (ProblemsViewToolWindowUtils.getContentById(project, SHARED_ISSUE_PANEL_TAB_ID) == null) {
        ProblemsView.addPanel(project, SharedIssuePanelProvider(project))
      }
      updateSharedIssuePanelTabName()
    }
  }

  /**
   * Opens the problem panel and switch the tab to shared issue panel tab. The optional given
   * [onAfterSettingVisibility] is executed after the visibility is changed.
   */
  fun showSharedIssuePanel(focus: Boolean = false, onAfterSettingVisibility: Runnable? = null) {
    val problemsViewPanel = ProblemsView.getToolWindow(project) ?: return
    DesignerCommonIssuePanelUsageTracker.getInstance()
      .trackChangingCommonIssuePanelVisibility(true, project)
    ProblemsViewToolWindowUtils.getContentById(project, SHARED_ISSUE_PANEL_TAB_ID)?.let {
      if (!isTabShowing(it)) {
        problemsViewPanel.show {
          problemsViewPanel.contentManager.setSelectedContent(it)
          updateSharedIssuePanelTabName()
          if (focus) {
            problemsViewPanel.activate(null, true)
          }
          onAfterSettingVisibility?.run()
        }
      }
    }
  }

  fun getSharedPanelIssues() =
    getDesignerCommonIssuePanel(project)?.issueProvider?.getFilteredIssues() ?: emptyList()

  /** Update the tab name (includes the issue count) of shared issue panel. */
  private fun updateSharedIssuePanelTabName() {
    val tab =
      ProblemsViewToolWindowUtils.getContentById(project, SHARED_ISSUE_PANEL_TAB_ID) ?: return
    val newName = getSharedIssuePanelTabTitle()
    val panel = (tab.component as? DesignerCommonIssuePanel)?.apply { name = newName }
    val count = panel?.issueProvider?.getFilteredIssues()?.distinct()?.size ?: 0
    // This change the ui text, run it in the UI thread.
    runInEdt {
      if (project.isDisposed) return@runInEdt
      tab.displayName = panel?.getName(count) ?: newName
    }
  }

  /** Get the title of shared issue panel. The returned string doesn't include the issue count. */
  private fun getSharedIssuePanelTabTitle(): String {
    val editors = FileEditorManager.getInstance(project).selectedEditors
    if (editors.size != 1) {
      // TODO: What tab name should be show when opening multiple file editor?
      return DEFAULT_SHARED_ISSUE_PANEL_TAB_TITLE
    }
    val file = editors[0].file ?: return DEFAULT_SHARED_ISSUE_PANEL_TAB_TITLE

    val name = getTabNameOfSupportedDesignerFile(file)
    if (name != null) {
      return name
    }
    val surface = editors[0].getDesignSurface() ?: return DEFAULT_SHARED_ISSUE_PANEL_TAB_TITLE
    if (surface.name != null) {
      return surface.name
    }
    return DEFAULT_SHARED_ISSUE_PANEL_TAB_TITLE
  }

  private fun isComposeFile(file: VirtualFile): Boolean {
    val extension = file.extension
    val fileType = file.fileType
    val psiFile = file.toPsiFile(project)
    return extension == KotlinFileType.INSTANCE.defaultExtension &&
      fileType == KotlinFileType.INSTANCE &&
      psiFile?.getModuleSystem()?.usesCompose == true
  }

  private fun isSupportedDesignerFileType(file: VirtualFile): Boolean {
    return getTabNameOfSupportedDesignerFile(file) != null
  }

  /** Returns null if the given file is not the supported [DesignerEditorFileType]. */
  private fun getTabNameOfSupportedDesignerFile(file: VirtualFile): String? {
    val psiFile = file.toPsiFile(project) ?: return null
    return when {
      isComposeFile(file) -> "Compose"
      LayoutFileType.isResourceTypeOf(psiFile) -> "Layout and Qualifiers"
      PreferenceScreenFileType.isResourceTypeOf(psiFile) -> "Preference"
      MenuFileType.isResourceTypeOf(psiFile) -> "Menu"
      else -> null
    }
  }

  /**
   * Select the highest severity issue related to the provided [NlComponent] and scroll the viewport
   * to issue.
   */
  fun showIssueForComponent(surface: DesignSurface<*>, component: NlComponent) {
    val issueModel = surface.issueModel
    val issue: Issue = issueModel.getHighestSeverityIssue(component) ?: return
    showSharedIssuePanel()
    setSelectedNode(IssueNodeVisitor(issue))
  }

  /** Return the visibility of the issue panel. */
  fun isIssuePanelVisible(): Boolean {
    return isTabShowing(
      ProblemsViewToolWindowUtils.getContentById(project, SHARED_ISSUE_PANEL_TAB_ID)
    )
  }

  /**
   * Return true if IJ's problem panel is visible and selecting the given [tab], false otherwise.
   */
  private fun isTabShowing(tab: Content?): Boolean {
    if (tab == null) {
      return false
    }
    val problemsViewPanel = ProblemsView.getToolWindow(project) ?: return false
    if (!problemsViewPanel.isVisible || tab !in problemsViewPanel.contentManager.contents) {
      return false
    }
    return tab.isSelected
  }

  /** Select the node by using the given [TreeVisitor] */
  fun setSelectedNode(nodeVisitor: TreeVisitor) {
    getDesignerCommonIssuePanel(project)?.setSelectedNode(nodeVisitor)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): IssuePanelService =
      project.getService(IssuePanelService::class.java)

    fun getDesignerCommonIssuePanel(project: Project): DesignerCommonIssuePanel? =
      ProblemsViewToolWindowUtils.getTabById(project, SHARED_ISSUE_PANEL_TAB_ID)
        as? DesignerCommonIssuePanel
  }
}
