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

import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.flags.StudioFlags
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
import java.lang.Integer.min
import javax.swing.JPanel

val REQUEST_SOURCE_KEY = DataKey.create<GeminiPluginApi.RequestSource>("RequestSource")
val SELECTED_EVENT_KEY = DataKey.create<Event>("SelectedEvent")

class AppInsightsContentPanel(
  projectController: AppInsightsProjectLevelController,
  project: Project,
  parentDisposable: Disposable,
  cellRenderer: AppInsightsTableCellRenderer,
  name: String,
  secondaryToolWindows: List<ToolWindowDefinition<AppInsightsToolWindowContext>>,
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
        innerComponent = mainContentPanel
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(APP_INSIGHTS_ID)
        firstSize = min((toolWindow?.component?.width ?: 1350) / 3, 700)
      }
    splitter.isFocusCycleRoot = false
    val workBench = workBenchFactory(this)
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
    if (name.contains("CRASHLYTICS") && StudioFlags.CRASHLYTICS_INSIGHT_IN_TOOLWINDOW.get()) {
      restoreToolWindowOrder(name, workBench, firstToolWindowName)
    } else if (name.contains("VITALS") && StudioFlags.PLAY_VITALS_INSIGHT_IN_TOOLWINDOW.get()) {
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
