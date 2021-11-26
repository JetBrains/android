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
import com.android.tools.idea.flags.StudioFlags
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.util.ui.UIUtil

/**
 * The name of issue tab.
 */
private const val TAB_NAME = "Current File and Qualifiers"
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

  /**
   * The issue panel for both Layout Editor and Validation Tool.
   */
  private var issueAndQualifierTab: Content? = null
  private var layoutAndQualifierPanel: DesignerCommonIssuePanel? = null

  private val initLock = Any()
  private var inited = false

  private val layoutAndQualifierIssueProviders = LayoutIssueProviderGroup()

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

    if (StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      val issuePanel = DesignerCommonIssuePanel(project, project)
      sharedIssuePanel = issuePanel
      issuePanel.setIssueProvider(layoutAndQualifierIssueProviders)
      contentFactory.createContent(issuePanel.getComponent(), "Design Issue", true).apply {
        sharedIssueTab = this
        isCloseable = false
        contentManager.addContent(this@apply)
      }
    }

    if (StudioFlags.NELE_USE_LAYOUT_AND_QUALIFIER_ISSUE_PANEL.get()) {
      val issuePanel = DesignerCommonIssuePanel(project, project)
      layoutAndQualifierPanel = issuePanel
      issuePanel.setIssueProvider(layoutAndQualifierIssueProviders)
      contentFactory.createContent(issuePanel.getComponent(), TAB_NAME, true).apply {
        issueAndQualifierTab = this
        isCloseable = false
        contentManager.addContent(this@apply)
      }
    }
  }

  /**
   * Return if the current issue panel of the given [DesignSurface] are showing.
   */
  fun isShowingIssuePanel(surface: DesignSurface) : Boolean {
    if (StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      if (!isTabShowing(sharedIssueTab)) {
        return false
      }
      return sharedIssuePanel?.getIssueProvider()?.source == surface.issueModel
    }
    else if (StudioFlags.NELE_USE_LAYOUT_AND_QUALIFIER_ISSUE_PANEL.get()) {
      if (!isLayoutAndQualifierPanelVisible()) {
        return false
      }

      return layoutAndQualifierIssueProviders.source.any { it.source == (surface.issueModel) }
    }
    return false
  }

  fun setShowIssuePanel(visible: Boolean, surface: DesignSurface, userInvoked: Boolean) {
    if (visible) {
      showIssuePanel(surface, userInvoked)
    }
    else {
      hideIssuePanel(surface, userInvoked)
    }
  }

  /**
   * Show the issue panel for the given [DesignSurface]. [userInvoked] identifies this was the direct consequence of a user action.
   */
  private fun showIssuePanel(surface: DesignSurface, userInvoked: Boolean) {
    val issueModel = surface.issueModel
    if (StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      val tab = sharedIssueTab ?: return
      if (!isTabShowing(tab)) {
        showTab(tab)
      }
      tab.displayName = "${surface.name} (${surface.issueModel.issueCount})"
      val panel = sharedIssuePanel ?: return
      // the tab is showing, replace the issue provider to the issue model of surface.
      val issueProvider = panel.getIssueProvider()
      if (issueProvider?.source != issueModel) {
        // TODO: Refactor to not rely on the virtual file
        val file = surface.model?.virtualFile ?: return
        panel.setIssueProvider(IssueModelProvider(issueModel, file))
        panel.updateTree(file, issueModel)
      }
    }
    else if (StudioFlags.NELE_USE_LAYOUT_AND_QUALIFIER_ISSUE_PANEL.get()) {
      if (!isLayoutAndQualifierPanelVisible()) {
        issueAndQualifierTab?.let {
          showTab(it)
          it.displayName = "${surface.name} (${surface.issueModel.issueCount})"
        }
      }
      val file = surface.model?.virtualFile ?: return
      attachIssueModel(issueModel, file)
    }
    else {
      surface.setShowIssuePanel(true, userInvoked)
    }
    return
  }

  /**
   * This hide IJ's Problem Panel as well. [userInvoked] identifies this was the direct consequence of a user action.
   * If IJ's Problem panel cannot be found, then this function does nothing.
   */
  private fun hideIssuePanel(surface: DesignSurface, userInvoked: Boolean) {
    if (StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      val problemsViewPanel = ProblemsView.getToolWindow(project) ?: return
      problemsViewPanel.hide()
    }
    else if (StudioFlags.NELE_USE_LAYOUT_AND_QUALIFIER_ISSUE_PANEL.get()) {
      // FIXME: We are not going to use attach and detach mechanism.
      detachIssueModel(surface.issueModel)
    }
    else {
      surface.setShowIssuePanel(false, userInvoked)
    }
  }

  /**
   * Select the highest severity issue related to the provided [NlComponent] and scroll the viewport to issue.
   * TODO: Remove the dependency of [NlComponent]
   */
  fun showIssueForComponent(surface: DesignSurface, userInvoked: Boolean, component: NlComponent, collapseOthers: Boolean) {
    setShowIssuePanel(true, surface, userInvoked)
    // TODO: The shared issue panel should support this feature.
    if (!StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get() && !StudioFlags.NELE_USE_LAYOUT_AND_QUALIFIER_ISSUE_PANEL.get()) {
      val issuePanel = surface.issuePanel
      val issueModel = surface.issueModel
      val issue: Issue = issueModel.getHighestSeverityIssue(component) ?: return
      val issueView = issuePanel.getDisplayIssueView(issue)
      if (issueView != null) {
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
    if (StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      // When this flag is enabled, we use attach and detach mechanism. The issue panel should never be hidden in this case.
      val tab = sharedIssueTab ?: return false
      if (!isTabShowing(tab)) {
        return false
      }
      return sharedIssuePanel?.getIssueProvider()?.source == surface.issueModel
    }
    else if (StudioFlags.NELE_USE_LAYOUT_AND_QUALIFIER_ISSUE_PANEL.get()) {
      if (isLayoutAndQualifierPanelVisible()) {
        // FIXME: We are not going to use attach and detach mechanism.
        return isIssueModelAttached(surface.issueModel)
      }
      return false
    }
    else {
      return !surface.issuePanel.isMinimized
    }
  }

  private fun isLayoutAndQualifierPanelVisible() = issueAndQualifierTab?.let { isTabShowing(it) } ?: false

  /**
   * Return true if IJ's problem panel is visible and selecting the given [tab], false otherwise.
   */
  private fun isTabShowing(tab: Content?): Boolean {
    if (tab == null) {
      return false
    }
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

  @Deprecated("We are not going to use attach and detach mechanism for shared issue panel")
  private fun isIssueModelAttached(issueModel: IssueModel): Boolean {
    return layoutAndQualifierIssueProviders.containsIssueModel(issueModel)
  }

  @Deprecated("We are not going to use attach and detach mechanism for shared issue panel")
  private fun attachIssueModel(issueModel: IssueModel, file: VirtualFile) {
    val commonIssuePanel = layoutAndQualifierPanel ?: return
    if (layoutAndQualifierIssueProviders.containsIssueModel(issueModel)) {
      return
    }
    layoutAndQualifierIssueProviders.addProvider(issueModel, file)
    commonIssuePanel.updateTree(file, issueModel)
  }

  @Deprecated("We are not going to use attach and detach mechanism for shared issue panel")
  private fun detachIssueModel(issueModel: IssueModel) {
    val commonIssuePanel = layoutAndQualifierPanel ?: return
    if (layoutAndQualifierIssueProviders.containsIssueModel(issueModel)) {
      layoutAndQualifierIssueProviders.removeProvider(issueModel)
      commonIssuePanel.updateTree(null, issueModel)
    }
  }

  /**
   * Get the issue panel for the given [DesignSurface], if any.
   */
  fun getIssuePanel(surface: DesignSurface): IssuePanel? {
    if (!StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get() && !StudioFlags.NELE_USE_LAYOUT_AND_QUALIFIER_ISSUE_PANEL.get()) {
      return surface.issuePanel
    }
    // We don't use shared issue panel for compose at this moment.
    return null
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): IssuePanelService = project.getService(IssuePanelService::class.java)
  }
}

/**
 * Sets the status of the issue panel.
 * @param show wether to show or hide the issue panel.
 * @param userInvoked if true, this was the direct consequence of a user action.
 */
private fun DesignSurface.setShowIssuePanel(show: Boolean, userInvoked: Boolean) {
  UIUtil.invokeLaterIfNeeded {
    issuePanel.isMinimized = !show
    if (userInvoked) {
      issuePanel.disableAutoSize()
    }
    revalidate()
    repaint()
  }
}
