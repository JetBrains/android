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

import com.android.tools.idea.common.editor.DesignToolsSplitEditor
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.editor.NlEditor
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * The ID of IJ's Problems ToolWindow.
 */
private const val PROBLEM_VIEW_ID = com.intellij.analysis.problemsView.toolWindow.ProblemsView.ID

class CreateIssueTabsInProblemsActivity : StartupActivity {
  override fun runActivity(project: Project) {
    IssuePanelService.getInstance(project)?.initIssueTabs()
  }
}

/**
 * A service to help to show the issues of Design Tools in IJ's Problems panel.
 */
class IssuePanelService(private val project: Project) {

  companion object {
    fun getInstance(project: Project): IssuePanelService? =
      if (StudioFlags.NELE_SHOW_ISSUE_PANEL_IN_PROBLEMS.get()) ServiceManager.getService(project, IssuePanelService::class.java) else null
  }

  fun initIssueTabs() {
    val problemsViewPanel = ToolWindowManager.getInstance(project).getToolWindow(PROBLEM_VIEW_ID)
    if (problemsViewPanel == null) {
      Logger.getInstance(IssuePanelService::class.java).error("Cannot find Problems panel")
      return
    }
    addLayoutTabToProblemsWindow(problemsViewPanel)
  }

  private fun addLayoutTabToProblemsWindow(problemsWindow: ToolWindow) {
    val contentManager = problemsWindow.contentManager
    val contentFactory = contentManager.factory

    // Add tab for layout editor.
    val layoutEditorIssueTab = JPanel(BorderLayout())
    val firstTab = contentFactory.createContent(layoutEditorIssueTab, "Layout Editor", true).apply {
      isCloseable = false
    }
    contentManager.addContent(firstTab)
    project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) {
        layoutEditorIssueTab.removeAll()
        val surface = ((event.newEditor as? DesignToolsSplitEditor)?.designerEditor as? NlEditor)?.component?.surface
        if (surface == null) {
          firstTab.displayName = "Layout Editor"
          layoutEditorIssueTab.add(JLabel("Cannot find Layout File"))
        }
        else {
          firstTab.displayName = event.newFile.name
          layoutEditorIssueTab.add(surface.issuePanel)
        }
      }
    })

    if (StudioFlags.NELE_VISUAL_LINT.get()) {
      // Add tab for visual linting in layout validation tool.
      // TODO: attach the issues in Layout Validation Tool
      val validationToolIssueTab = JPanel(BorderLayout()).apply {
        add(JLabel("TODO: Show the Layout Validation Tool issue here"))
      }
      val secondTab = contentFactory.createContent(validationToolIssueTab, "Layout Validation", true).apply {
        isCloseable = false
      }
      contentManager.addContent(secondTab)
    }
  }
}
