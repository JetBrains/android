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

import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.model.BuildAnalyzerViewModel
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.build.attribution.ui.model.TasksPageId
import com.android.build.attribution.ui.model.TasksTreeNode

/**
 * Handlers that are called by the view on the corresponding action from the user.
 */
interface ViewActionHandlers {

  /**
   * Called when selection in data set combo box is changed by the user.
   */
  fun dataSetComboBoxSelectionUpdated(newSelectedData: BuildAnalyzerViewModel.DataSet)

  /** Called when selection in tasks grouping combo box is changed by the user. */
  fun tasksGroupingSelectionUpdated(grouping: TasksDataPageModel.Grouping)

  /** Called on tree node selection. */
  fun tasksTreeNodeSelected(tasksTreeNode: TasksTreeNode)

  /** Called on a link to another task details page click. */
  fun tasksDetailsLinkClicked(taskPageId: TasksPageId)

  /** Called on help link click. */
  fun helpLinkClicked()

  /** Called on 'generate report' link click. */
  fun generateReportClicked(taskData: TaskUiData)
}