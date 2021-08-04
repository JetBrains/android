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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.visual.VisualizationToolWindowFactory
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.ide.DataManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel


/**
 * A service to help to show the issues of Design Tools in IJ's Problems panel.
 */
class IssuePanelService(private val project: Project) {

  private var layoutEditorTab: Content? = null
  private var validationToolTab: Content? = null
  private val initLock = Any()
  private var inited = false

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
    addLayoutTabToProblemsWindow(problemsViewWindow)
  }

  private fun addLayoutTabToProblemsWindow(problemsWindow: ToolWindow) {
    val contentManager = problemsWindow.contentManager
    val contentFactory = contentManager.factory

    // Add tab for layout editor.
    layoutEditorTab = contentFactory.createContent(JPanel(BorderLayout()), "Layout Editor", true).apply {
      isCloseable = false
      contentManager.addContent(this@apply)
    }

    // Add tab for visual linting in layout validation tool.
    if (StudioFlags.NELE_VISUAL_LINT.get()) {
      validationToolTab = contentFactory.createContent(JPanel(BorderLayout()), "Layout Validation", true).apply {
        isCloseable = false
        contentManager.addContent(this@apply)
      }
      // Register tool window state event for tracing the visibility of Layout Validation Tool.
      project.messageBus.connect().subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
        override fun stateChanged(toolWindowManager: ToolWindowManager) {
          loadValidationToolIssuePanel()
        }
      })
    }

    // Register editor change event.
    project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) = updateContent(event.newEditor)
    })
    // Register tool window tab change event.
    contentManager.addContentManagerListener(object : ContentManagerListener {
      override fun selectionChanged(event: ContentManagerEvent) = updateContent(FileEditorManager.getInstance(project)?.selectedEditor)
    })

    // Load issue panel for current selected editor if needed.
    updateContent(FileEditorManager.getInstance(project)?.selectedEditor)
  }

  private fun updateContent(editor: FileEditor?) {
    layoutEditorTab?.let {
      if (isTabShowing(it)) {
        loadLayoutEditorIssuePanel(editor)
      }
    }
    val validationTab = validationToolTab
    if (StudioFlags.NELE_VISUAL_LINT.get() && validationTab != null) {
      if (isTabShowing(validationTab)) {
        loadValidationToolIssuePanel()
      }
    }
  }

  private fun loadLayoutEditorIssuePanel(editor: FileEditor?) {
    val tab = layoutEditorTab ?: return
    val file = editor?.file
    val surface = (editor as? DesignToolsSplitEditor)?.designerEditor?.component?.surface
    val issuePanelContainer = tab.component
    if (editor == null || file == null || surface == null) {
      tab.displayName = "Layout Editor"
      issuePanelContainer.removeAll()
      issuePanelContainer.add(JLabel("Cannot find Layout File"))
    }
    else {
      tab.displayName = file.name
      issuePanelContainer.removeAll()
      issuePanelContainer.add(surface.issuePanel)
    }
  }

  private fun loadValidationToolIssuePanel() {
    val tab = validationToolTab ?: return
    val issuePanelContainer = tab.component
    issuePanelContainer.removeAll()

    val window = ToolWindowManager.getInstance(project).getToolWindow(VisualizationToolWindowFactory.TOOL_WINDOW_ID)
    if (window == null) {
      Logger.getInstance(IssuePanelService::class.java).warn("The validation tool is not activated")
      issuePanelContainer.add(JLabel("Layout Validation Tool is not enabled"))
      return
    }

    if (window.isVisible) {
      val surface = DataManager.getInstance().getDataContext(window.contentManager.component).getData(DESIGN_SURFACE)
      if (surface == null) {
        issuePanelContainer.add(JLabel("Cannot find preview panel in Layout Validation Tool"))
      }
      else {
        issuePanelContainer.add(surface.issuePanel)
      }
    }
    else {
      issuePanelContainer.add(JLabel("Layout Validation Tool is not opened."))
    }
  }

  fun isLayoutEditorIssuePanelVisible() = layoutEditorTab?.let { isTabShowing(it) } ?: false

  fun isLayoutValidationIssuePanelVisible() = validationToolTab?.let { isTabShowing(it) } ?: false

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

  /**
   * Show the issue panel of Layout Editor in IJ's Problem panel.
   * This open the Problem panel and switch to Layout Editor tab.
   * If the Problem panel is opened already, then it just switches to Layout Editor tab.
   *
   * If IJ's Problem panel cannot be found or ther eis no tab for layout editor, then this function does nothing.
   */
  fun showLayoutEditorIssuePanel() = layoutEditorTab?.let { showTab(it) }

  /**
   * Show the issue panel of Layout Validation Tool in IJ's Problem panel.
   * This open the Problem panel and switch to Layout Validation Tool tab.
   * If the Problem panel is opened already, then it just switches to Layout Validation Tool tab.
   *
   * If IJ's Problem panel cannot be found or there is no tab for validation tool, then this function does nothing.
   */
  fun showLayoutValidationIssuePanel() = validationToolTab?.let { showTab(it) }

  private fun showTab(tab: Content) {
    val problemsViewPanel = ProblemsView.getToolWindow(project) ?: return
    problemsViewPanel.show {
      tab.manager?.setSelectedContent(tab)
    }
  }

  /**
   * This hide IJ's Problem Panel as well.
   * If IJ's Problem panel cannot be found, then this function does nothing.
   */
  fun hideIssuePanel() {
    val problemsViewPanel = ProblemsView.getToolWindow(project) ?: return
    problemsViewPanel.hide()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): IssuePanelService? = ServiceManager.getService(project, IssuePanelService::class.java)
  }
}
