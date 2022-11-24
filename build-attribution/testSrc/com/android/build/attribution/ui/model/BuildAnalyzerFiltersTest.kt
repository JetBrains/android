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
package com.android.build.attribution.ui.model

import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.ui.MockUiData
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.controllers.BuildAnalyzerViewController
import com.android.build.attribution.ui.controllers.TaskIssueReporter
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.mockTask
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Dimension
import java.util.UUID

class BuildAnalyzerFiltersTest {
  @get:Rule
  val projectRule: ProjectRule = ProjectRule()

  @get:Rule
  var disposableRule = DisposableRule()

  @get:Rule
  val edtRule = EdtRule()

  private val tracker = TestUsageTracker(VirtualTimeScheduler())

  private val task1 = mockTask(":app", "compile", "compiler.plugin", 2000).apply {
    issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(this))
  }
  private val task2 = mockTask(":app", "resources", "resources.plugin", 1000)
  private val task3 = mockTask(":lib", "compile", "compiler.plugin", 1000)

  private val defaultWarningFilterItemsList = listOf(
    BuildAttributionUiEvent.FilterItem.SHOW_ANDROID_PLUGIN_TASKS,
    BuildAttributionUiEvent.FilterItem.SHOW_THIRD_PARTY_TASKS,
    BuildAttributionUiEvent.FilterItem.SHOW_PROJECT_CUSTOMIZATION_TASKS,
    BuildAttributionUiEvent.FilterItem.SHOW_ALWAYS_RUN_TASK_WARNINGS,
    BuildAttributionUiEvent.FilterItem.SHOW_TASK_SETUP_ISSUE_WARNINGS,
    BuildAttributionUiEvent.FilterItem.SHOW_ANNOTATION_PROCESSOR_WARNINGS,
    BuildAttributionUiEvent.FilterItem.SHOW_CONFIGURATION_CACHE_WARNINGS,
    BuildAttributionUiEvent.FilterItem.SHOW_JETIFIER_USAGE_WARNINGS,
  )

  private val defaultTasksFilterItemsList = listOf(
    BuildAttributionUiEvent.FilterItem.SHOW_ANDROID_PLUGIN_TASKS,
    BuildAttributionUiEvent.FilterItem.SHOW_THIRD_PARTY_TASKS,
    BuildAttributionUiEvent.FilterItem.SHOW_PROJECT_CUSTOMIZATION_TASKS,
    BuildAttributionUiEvent.FilterItem.SHOW_TASKS_WITHOUT_WARNINGS
  )

  val model = BuildAnalyzerViewModel(MockUiData(tasksList = listOf(task1, task2, task3)), BuildAttributionWarningsFilter())
  val buildSessionId = UUID.randomUUID().toString()
  val issueReporter = Mockito.mock(TaskIssueReporter::class.java)
  lateinit var analytics: BuildAttributionUiAnalytics
  lateinit var controller: BuildAnalyzerViewController

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(tracker)
    analytics = BuildAttributionUiAnalytics(projectRule.project, uiSizeProvider = { Dimension(300, 200) })
    controller = BuildAnalyzerViewController(model, projectRule.project, analytics, issueReporter)
    analytics.newReportSessionId(buildSessionId)
  }

  @Test
  fun testInitialWarningsFilterState() {
    val initialFilterActionsState = (warningsFilterActions(model.warningsPageModel, controller) as DefaultActionGroup).childActionsOrStubs
      .filterIsInstance<WarningsFilterToggleAction>()
      .map { it.templateText to it.isSelected(model.warningsPageModel.filter) }

    val expected = listOf(
      "Show Always-run tasks" to true,
      "Show Task Setup issues" to true,
      "Show issues for Android/Java/Kotlin plugins" to true,
      "Show issues for other plugins" to true,
      "Show issues for project customization" to true,
      "Include issues for tasks non determining this build duration" to false,
      "Show annotation processors issues" to true,
      "Show configuration cache issues" to true,
      "Show Jetifier usage warning" to true,
    )

    Truth.assertThat(initialFilterActionsState).isEqualTo(expected)
  }

  @Test
  fun testShowAlwaysRunTasksFilterApplyToWarnings() {
    val filterActions = warningsFilterActions(model.warningsPageModel, controller) as DefaultActionGroup
    val filterToggleAction = filterActions.childActionsOrStubs.first { it.templateText == "Show Always-run tasks" }

    filterToggleAction.actionPerformed(TestActionEvent.createTestEvent())
    // Mock tasks have only one Always-run tasks warning. When it is filtered out, only AP, CC and Jetifier warnings should be shown.
    Truth.assertThat(model.warningsPageModel.treeRoot.childCount).isEqualTo(3)

    filterToggleAction.actionPerformed(TestActionEvent.createTestEvent())
    // All tasks should be back.
    Truth.assertThat(model.warningsPageModel.treeRoot.childCount).isEqualTo(4)

    verifyMetricsSent(BuildAttributionUiEvent.FilterItem.SHOW_ALWAYS_RUN_TASK_WARNINGS, defaultWarningFilterItemsList)
  }

  @Test
  fun testShowTasksForAndroidPluginsFilterApplyToWarnings() {
    val filterActions = warningsFilterActions(model.warningsPageModel, controller) as DefaultActionGroup
    val filterToggleAction = filterActions.childActionsOrStubs.first { it.templateText == "Show issues for Android/Java/Kotlin plugins" }

    filterToggleAction.actionPerformed(TestActionEvent.createTestEvent())
    // Mock tasks have only one Always-run tasks warning and task is attributed to Android plugin.
    // When it is filtered out, only AP, CC and Jetifier warnings should be shown.
    Truth.assertThat(model.warningsPageModel.treeRoot.childCount).isEqualTo(3)

    filterToggleAction.actionPerformed(TestActionEvent.createTestEvent())
    // All tasks should be back.
    Truth.assertThat(model.warningsPageModel.treeRoot.childCount).isEqualTo(4)

    verifyMetricsSent(BuildAttributionUiEvent.FilterItem.SHOW_ANDROID_PLUGIN_TASKS, defaultWarningFilterItemsList)
  }

  @Test
  fun testShowAnnotationProcessorIssuesFilterApplyToWarnings() {
    val filterActions = warningsFilterActions(model.warningsPageModel, controller) as DefaultActionGroup
    val filterToggleAction = filterActions.childActionsOrStubs.first { it.templateText == "Show annotation processors issues" }

    filterToggleAction.actionPerformed(TestActionEvent.createTestEvent())
    // When AP warnings are filtered out only Always-run tasks, CC and Jetifier warnings should be shown.
    Truth.assertThat(model.warningsPageModel.treeRoot.childCount).isEqualTo(3)

    filterToggleAction.actionPerformed(TestActionEvent.createTestEvent())
    // All warnings should be back.
    Truth.assertThat(model.warningsPageModel.treeRoot.childCount).isEqualTo(4)

    verifyMetricsSent(BuildAttributionUiEvent.FilterItem.SHOW_ANNOTATION_PROCESSOR_WARNINGS, defaultWarningFilterItemsList)
  }

  @Test
  fun testShowConfigurationCacheIssuesFilterApplyToWarnings() {
    val filterActions = warningsFilterActions(model.warningsPageModel, controller) as DefaultActionGroup
    val filterToggleAction = filterActions.childActionsOrStubs.first { it.templateText == "Show configuration cache issues" }

    filterToggleAction.actionPerformed(TestActionEvent.createTestEvent())
    // When CC warnings are filtered out only Always-run tasks, AP and Jetifier warnings should be shown.
    Truth.assertThat(model.warningsPageModel.treeRoot.childCount).isEqualTo(3)

    filterToggleAction.actionPerformed(TestActionEvent.createTestEvent())
    // All warnings should be back.
    Truth.assertThat(model.warningsPageModel.treeRoot.childCount).isEqualTo(4)

    verifyMetricsSent(BuildAttributionUiEvent.FilterItem.SHOW_CONFIGURATION_CACHE_WARNINGS, defaultWarningFilterItemsList)
  }

  @Test
  fun testShowJetifierUsageWarningFilterApplyToWarnings() {
    val filterActions = warningsFilterActions(model.warningsPageModel, controller) as DefaultActionGroup
    val filterToggleAction = filterActions.childActionsOrStubs.first { it.templateText == "Show Jetifier usage warning" }

    filterToggleAction.actionPerformed(TestActionEvent.createTestEvent())
    // When Jetifier warnings are filtered out only Always-run tasks, AP and CC warnings should be shown.
    Truth.assertThat(model.warningsPageModel.treeRoot.childCount).isEqualTo(3)

    filterToggleAction.actionPerformed(TestActionEvent.createTestEvent())
    // All warnings should be back.
    Truth.assertThat(model.warningsPageModel.treeRoot.childCount).isEqualTo(4)

    verifyMetricsSent(BuildAttributionUiEvent.FilterItem.SHOW_JETIFIER_USAGE_WARNINGS, defaultWarningFilterItemsList)
  }

  @Test
  fun testInitialTaskFilterState() {
    val initialFilterActionsState = (tasksFilterActions(model.tasksPageModel, controller) as DefaultActionGroup).childActionsOrStubs
      .filterIsInstance<TasksFilterToggleAction>()
      .map { it.templateText to it.isSelected(model.tasksPageModel.filter) }

    val expected = listOf(
      "Show tasks for Android/Java/Kotlin plugins" to true,
      "Show tasks for other plugins" to true,
      "Show tasks for project customization" to true,
      "Show tasks without warnings" to true
    )

    Truth.assertThat(initialFilterActionsState).isEqualTo(expected)
  }

  @Test
  fun testShowTasksWithoutWarningsFilterApplyToTasks() {
    val filterActions = tasksFilterActions(model.tasksPageModel, controller) as DefaultActionGroup
    val filterToggleAction = filterActions.childActionsOrStubs.first { it.templateText == "Show tasks without warnings" }

    filterToggleAction.actionPerformed(TestActionEvent.createTestEvent())
    // Only one of mock tasks has warnings, others should be filtered out now.
    Truth.assertThat(model.tasksPageModel.treeRoot.childCount).isEqualTo(1)

    filterToggleAction.actionPerformed(TestActionEvent.createTestEvent())
    // All tasks should be back.
    Truth.assertThat(model.tasksPageModel.treeRoot.childCount).isEqualTo(3)

    verifyMetricsSent(BuildAttributionUiEvent.FilterItem.SHOW_TASKS_WITHOUT_WARNINGS, defaultTasksFilterItemsList)
  }

  @Test
  fun testShowTasksForAndroidPluginsFilterApplyToTasks() {
    val filterActions = tasksFilterActions(model.tasksPageModel, controller) as DefaultActionGroup
    val filterToggleAction = filterActions.childActionsOrStubs.first { it.templateText == "Show tasks for Android/Java/Kotlin plugins" }

    filterToggleAction.actionPerformed(TestActionEvent.createTestEvent())
    // All mock tasks are attributed to android plugin thus should be filtered out now.
    Truth.assertThat(model.tasksPageModel.treeRoot.childCount).isEqualTo(0)

    filterToggleAction.actionPerformed(TestActionEvent.createTestEvent())
    // All tasks should be back.
    Truth.assertThat(model.tasksPageModel.treeRoot.childCount).isEqualTo(3)

    verifyMetricsSent(BuildAttributionUiEvent.FilterItem.SHOW_ANDROID_PLUGIN_TASKS, defaultTasksFilterItemsList)
  }

  // Verify metrics sent: 2 events, for item switch off and back on.
  private fun verifyMetricsSent(
    filterItemToggled: BuildAttributionUiEvent.FilterItem,
    defaultFilterState: List<BuildAttributionUiEvent.FilterItem>
  ) {
    val filterEvents = tracker.usages
      .filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
      .map { event -> event.studioEvent.buildAttributionUiEvent.let { it.eventType to it.appliedFiltersList } }

    val updatedFilterItemsList = defaultFilterState.filterNot { it == filterItemToggled }
    Truth.assertThat(filterEvents).isEqualTo(listOf(
      BuildAttributionUiEvent.EventType.FILTER_APPLIED to updatedFilterItemsList,
      BuildAttributionUiEvent.EventType.FILTER_APPLIED to defaultFilterState
    ))
  }
}