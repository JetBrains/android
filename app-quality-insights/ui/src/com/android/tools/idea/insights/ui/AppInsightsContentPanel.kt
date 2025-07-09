/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.Event
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ThreeComponentsSplitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseListener
import javax.swing.JPanel

val REQUEST_SOURCE_KEY = DataKey.create<GeminiPluginApi.RequestSource>("RequestSource")
val SELECTED_EVENT_KEY = DataKey.create<Event>("SelectedEvent")

class AppInsightsContentPanel(
  projectController: AppInsightsProjectLevelController,
  project: Project,
  parentDisposable: Disposable,
  cellRenderer: AppInsightsTableCellRenderer,
  name: String,
  secondaryToolWindows: List<AppInsightsToolWindowDefinition>,
  tableMouseListener: MouseListener? = null,
  workBenchFactory: (Disposable) -> WorkBench<AppInsightsToolWindowContext> = {
    WorkBench(project, name, null, it)
  },
  createCenterPanel: ((Int) -> Unit) -> Component,
) : JPanel(BorderLayout()), Disposable {
  private val issuesTableView: AppInsightsIssuesTableView

  init {
    Disposer.register(parentDisposable, this)
    val issuesModel = AppInsightsIssuesTableModel(cellRenderer)
    issuesTableView =
      AppInsightsIssuesTableView(issuesModel, projectController, cellRenderer, tableMouseListener)
    Disposer.register(this, issuesTableView)
    val mainContentPanel = JPanel(BorderLayout())
    mainContentPanel.add(createCenterPanel(issuesTableView::setHeaderHeight))

    val splitter =
      ThreeComponentsSplitter(false, true).apply {
        setHonorComponentsMinimumSize(true)
        firstComponent = issuesTableView.component
        lastComponent = mainContentPanel
        ToolWindowManager.getInstance(project).getToolWindow(APP_INSIGHTS_ID)?.let { toolWindow ->
          val minSize = toolWindow.component.width / 4
          firstSize = minSize
          lastSize = minSize
        }
      }
    splitter.isFocusCycleRoot = false
    val workBench = workBenchFactory(this)
    workBench.addWorkBenchToolWindowListener { visibleWindows ->
      secondaryToolWindows.forEach { it.updateVisibility(it.name in visibleWindows) }
    }
    workBench.isFocusCycleRoot = false
    workBench.init(splitter, AppInsightsToolWindowContext(), secondaryToolWindows, false)
    // Set the Insight toolwindow as the default for the first time user launches with this feature.
    maybeRestoreToolWindowOrder(name, workBench, secondaryToolWindows.firstOrNull()?.name ?: "")

    add(workBench)
  }

  private fun maybeRestoreToolWindowOrder(
    name: String,
    workBench: WorkBench<AppInsightsToolWindowContext>,
    firstToolWindowName: String,
  ) {
    if (name.contains("CRASHLYTICS")) {
      restoreToolWindowOrder(name, workBench, firstToolWindowName)
    } else if (name.contains("VITALS")) {
      restoreToolWindowOrder(name, workBench, firstToolWindowName)
    }
  }

  /**
   * Restore the default layout in [WorkBench] and show the tool window with name matching
   * [toolWindowName]
   */
  private fun restoreToolWindowOrder(
    name: String,
    workBench: WorkBench<AppInsightsToolWindowContext>,
    toolWindowName: String,
  ) {
    val propertiesComponent = PropertiesComponent.getInstance()
    val key = "$name.workbench.toolwindow.order.updated"
    if (!propertiesComponent.isValueSet(key)) {
      workBench.restoreDefaultLayout()
      workBench.showToolWindow(toolWindowName)
      propertiesComponent.setValue(key, true)
    }
  }

  override fun dispose() = Unit
}
