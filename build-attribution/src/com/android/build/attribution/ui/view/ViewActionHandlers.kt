/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.attribution.ui.view

import com.android.build.attribution.analyzers.IncompatiblePluginWarning
import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.model.BuildAnalyzerViewModel
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.build.attribution.ui.model.TasksFilter
import com.android.build.attribution.ui.model.TasksPageId
import com.android.build.attribution.ui.model.TasksTreeNode
import com.android.build.attribution.ui.model.WarningsFilter
import com.android.build.attribution.ui.model.WarningsTreeNode
import com.android.build.attribution.ui.view.details.JetifierWarningDetailsView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.ui.awt.RelativePoint
import java.util.function.Supplier

/**
 * Handlers that are called by the view on the corresponding action from the user.
 */
interface ViewActionHandlers {

  /**
   * Called when selection in data set combo box is changed by the user.
   */
  fun dataSetComboBoxSelectionUpdated(newSelectedData: BuildAnalyzerViewModel.DataSet)

  /**
   * Called when navigation link clicked targeting to show tasks.
   * Used in Overview and view empty states.
   */
  fun changeViewToTasksLinkClicked(targetGrouping: TasksDataPageModel.Grouping)

  /**
   * Called when navigation link clicked targeting to show warnings.
   * Used in Overview and view empty states.
   */
  fun changeViewToWarningsLinkClicked()

  fun changeViewToDownloadsLinkClicked()

  /** Called when selection in tasks grouping control is changed by the user. */
  fun tasksGroupingSelectionUpdated(grouping: TasksDataPageModel.Grouping)

  /** Called on tasks page tree node selection. Passing null means clearing selection to empty. */
  fun tasksTreeNodeSelected(tasksTreeNode: TasksTreeNode?)

  /** Called on a link to another task details page click. */
  fun tasksDetailsLinkClicked(taskPageId: TasksPageId)

  /** Called on warnings page tree node selection. Passing null means clearing selection to empty. */
  fun warningsTreeNodeSelected(warningTreeNode: WarningsTreeNode?)

  /** Called on help link click. */
  fun helpLinkClicked(linkTarget: BuildAnalyzerBrowserLinks)

  /** Called on 'generate report' link click. */
  fun generateReportClicked(taskData: TaskUiData)

  /** Called on 'Open memory settings' button click from gradle daemon high gc warning. */
  fun openMemorySettings()

  /** Called when user changes filters selection on tasks page. */
  fun applyTasksFilter(filter: TasksFilter)

  /** Called when user changes filters selection on warnings page. */
  fun applyWarningsFilter(filter: WarningsFilter)

  /** Called when selection in warnings grouping control is changed by the user. */
  fun warningsGroupingSelectionUpdated(groupByPlugin: Boolean)

  /** Called when user clicks and confirms on a GC warning hiding link. */
  fun dontShowAgainNoGCSettingWarningClicked()

  /** Called when user clicks on configuration cache link on build overview page. */
  fun openConfigurationCacheWarnings()
  fun runAgpUpgrade()
  fun runTestConfigurationCachingBuild()
  fun turnConfigurationCachingOnInProperties()
  fun updatePluginClicked(pluginWarningData: IncompatiblePluginWarning)
  fun runCheckJetifierTask()
  fun turnJetifierOffInProperties(sourceRelativePointSupplier: Supplier<RelativePoint>)
  fun createFindSelectedLibVersionDeclarationAction(selectionSupplier: Supplier<JetifierWarningDetailsView.DirectDependencyDescriptor?>): AnAction
}