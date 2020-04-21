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
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.util.UUID

class BuildAnalyzerViewControllerTest {
  @get:Rule
  val projectRule: ProjectRule = ProjectRule()

  @get:Rule
  val edtRule = EdtRule()

  private val tracker = TestUsageTracker(VirtualTimeScheduler())

  val model = BuildAnalyzerViewModel()
  val analytics = BuildAttributionUiAnalytics(projectRule.project)
  val buildSessionId = UUID.randomUUID().toString()
  val issueReporter = Mockito.mock(TaskIssueReporter::class.java)

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(tracker)
    analytics.newReportSessionId(buildSessionId)
  }

  @After
  fun tearDown() {
    UsageTracker.cleanAfterTesting()
  }

  @Test
  @RunsInEdt
  fun testDataSetComboBoxSelectionUpdatedToTasks() {
    model.selectedData = BuildAnalyzerViewModel.DataSet.OVERVIEW

    val controller = BuildAnalyzerViewController(model, analytics, issueReporter)
    controller.dataSetComboBoxSelectionUpdated(BuildAnalyzerViewModel.DataSet.TASKS)

    assertThat(model.selectedData).isEqualTo(BuildAnalyzerViewModel.DataSet.TASKS)

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }

    buildAttributionEvents.single().studioEvent.buildAttributionUiEvent.verifyComboBoxPageChangeEvent(
      from = BuildAttributionUiEvent.Page.PageType.BUILD_SUMMARY,
      to = BuildAttributionUiEvent.Page.PageType.CRITICAL_PATH_TASKS_ROOT
    )
  }

  @Test
  @RunsInEdt
  fun testDataSetComboBoxSelectionUpdatedToWarnings() {
    model.selectedData = BuildAnalyzerViewModel.DataSet.OVERVIEW

    val controller = BuildAnalyzerViewController(model, analytics, issueReporter)
    controller.dataSetComboBoxSelectionUpdated(BuildAnalyzerViewModel.DataSet.WARNINGS)

    assertThat(model.selectedData).isEqualTo(BuildAnalyzerViewModel.DataSet.WARNINGS)

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }

    buildAttributionEvents.single().studioEvent.buildAttributionUiEvent.verifyComboBoxPageChangeEvent(
      from = BuildAttributionUiEvent.Page.PageType.BUILD_SUMMARY,
      to = BuildAttributionUiEvent.Page.PageType.WARNINGS_ROOT
    )
  }

  @Test
  @RunsInEdt
  fun testDataSetComboBoxSelectionUpdatedToOverview() {
    model.selectedData = BuildAnalyzerViewModel.DataSet.WARNINGS

    val controller = BuildAnalyzerViewController(model, analytics, issueReporter)
    controller.dataSetComboBoxSelectionUpdated(BuildAnalyzerViewModel.DataSet.OVERVIEW)

    assertThat(model.selectedData).isEqualTo(BuildAnalyzerViewModel.DataSet.OVERVIEW)

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }

    buildAttributionEvents.single().studioEvent.buildAttributionUiEvent.verifyComboBoxPageChangeEvent(
      from = BuildAttributionUiEvent.Page.PageType.WARNINGS_ROOT,
      to = BuildAttributionUiEvent.Page.PageType.BUILD_SUMMARY
    )
  }

  private fun BuildAttributionUiEvent.verifyComboBoxPageChangeEvent(
    from: BuildAttributionUiEvent.Page.PageType,
    to: BuildAttributionUiEvent.Page.PageType
  ) {
    assertThat(buildAttributionReportSessionId).isEqualTo(buildSessionId)
    // TODO (b/154988129): update type to combo-box usage when ready
    assertThat(eventType).isEqualTo(BuildAttributionUiEvent.EventType.UNKNOWN_TYPE)
    assertThat(currentPage.pageType).isEqualTo(from)
    assertThat(currentPage.pageEntryIndex).isEqualTo(1)
    assertThat(targetPage.pageType).isEqualTo(to)
    assertThat(targetPage.pageEntryIndex).isEqualTo(1)
  }
}