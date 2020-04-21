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
package com.android.build.attribution.ui.controllers

import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.model.BuildAnalyzerViewModel

import com.android.build.attribution.ui.view.ViewActionHandlers
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent

class BuildAnalyzerViewController(
  val model: BuildAnalyzerViewModel,
  private val analytics: BuildAttributionUiAnalytics,
  private val issueReporter: TaskIssueReporter
) : ViewActionHandlers {

  init {
    val pageId = model.selectedData.toAnalyticsPage()
    analytics.initFirstPage(pageId)
  }

  override fun dataSetComboBoxSelectionUpdated(newSelectedData: BuildAnalyzerViewModel.DataSet) {
    model.selectedData = newSelectedData
    val pageId = newSelectedData.toAnalyticsPage()
    analytics.pageChange(pageId, BuildAttributionUiEvent.EventType.UNKNOWN_TYPE)
  }

  private fun BuildAnalyzerViewModel.DataSet.toAnalyticsPage(): BuildAttributionUiAnalytics.AnalyticsPageId {
    val type = when (this) {
      BuildAnalyzerViewModel.DataSet.OVERVIEW -> BuildAttributionUiEvent.Page.PageType.BUILD_SUMMARY
      BuildAnalyzerViewModel.DataSet.TASKS -> BuildAttributionUiEvent.Page.PageType.CRITICAL_PATH_TASKS_ROOT
      BuildAnalyzerViewModel.DataSet.WARNINGS -> BuildAttributionUiEvent.Page.PageType.WARNINGS_ROOT
    }
    return BuildAttributionUiAnalytics.AnalyticsPageId(type, this.name)
  }
}
