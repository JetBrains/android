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

import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content

/**
 * A service to help to show the issues of Design Tools in IJ's Problems panel.
 */
class IssuePanelService(private val project: Project) {

  private var issueAndQualifierTab: Content? = null
  private var layoutAndQualifierPanel: DesignerCommonIssuePanel? = null

  private val initLock = Any()
  private var inited = false

  private val issueProviders = LayoutIssueProviderGroup()

  init {
    val manager = ToolWindowManager.getInstance(project)
    val problemsView = manager.getToolWindow(ProblemsView.ID)
    if (problemsView != null) {
      // ProblemsView has registered, init the tab.
      initIssueTabs(problemsView)
    }
    else {
      val connection = project.messageBus.connect()
      val listener: ToolWindowManagerListener
      listener = object : ToolWindowManagerListener {
        override fun toolWindowsRegistered(ids: MutableList<String>, toolWindowManager: ToolWindowManager) {
          if (ProblemsView.ID in ids) {
            val problemsViewToolWindow = ProblemsView.getToolWindow(project)
            if (problemsViewToolWindow != null) {
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

    val issuePanel = DesignerCommonIssuePanel(project, project)
    layoutAndQualifierPanel = issuePanel
    issuePanel.setIssueProvider(issueProviders)
    contentFactory.createContent(issuePanel.getComponent(), "Current File and Qualifiers", true).apply {
      issueAndQualifierTab = this
      isCloseable = false
      contentManager.addContent(this@apply)
    }
  }

  fun isLayoutAndQualifierPanelVisible() = issueAndQualifierTab?.let { isTabShowing(it) } ?: false

  fun isIssueModelAttached(issueModel: IssueModel): Boolean {
    return issueProviders.containsIssueModel(issueModel)
  }

  fun showCurrentFileAndQualifierTab() {
    issueAndQualifierTab?.let { showTab(it) }
  }

  /**
   * Return true if IJ's problem panel is visible and selecting the given [tab], false otherwise.
   */
  private fun isTabShowing(tab: Content): Boolean {
    val problemsViewPanel = ProblemsView.getToolWindow(project) ?: return false
    if (!problemsViewPanel.isVisible) {
      return false
    }
    return tab.isSelected
  }

  private fun showTab(tab: Content) {
    val problemsViewPanel = ProblemsView.getToolWindow(project) ?: return
    problemsViewPanel.show {
      tab.manager?.setSelectedContent(tab)
    }
  }

  fun attachIssueModel(issueModel: IssueModel, file: VirtualFile) {
    val commonIssuePanel = layoutAndQualifierPanel ?: return
    if (issueProviders.containsIssueModel(issueModel)) {
      return
    }
    issueProviders.addProvider(issueModel, file)
    commonIssuePanel.updateTree(file, issueModel)
  }

  fun detachIssueModel(issueModel: IssueModel) {
    val commonIssuePanel = layoutAndQualifierPanel ?: return
    if (issueProviders.containsIssueModel(issueModel)) {
      issueProviders.removeProvider(issueModel)
      commonIssuePanel.updateTree(null, issueModel)
    }
  }

  /**
   * This hide IJ's Problem Panel as well.
   * If IJ's Problem panel cannot be found, then this function does nothing.
   */
  fun hideIssuePanel() {
    // TODO: Hide the panel when not using attach/detach mechanism. At this moment we don't hide it.
    //val problemsViewPanel = ProblemsView.getToolWindow(project) ?: return
    //problemsViewPanel.hide()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): IssuePanelService? = project.getService(IssuePanelService::class.java)
  }
}
