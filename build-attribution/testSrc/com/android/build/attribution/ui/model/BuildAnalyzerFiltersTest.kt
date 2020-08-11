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

import com.android.build.attribution.ui.MockUiData
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.controllers.BuildAnalyzerViewController
import com.android.build.attribution.ui.controllers.TaskIssueReporter
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.mockTask
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.testing.IdeComponents
import com.google.common.truth.Truth
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.util.UUID

class BuildAnalyzerFiltersTest {
  @get:Rule
  val projectRule: ProjectRule = ProjectRule()

  @get:Rule
  var disposableRule = DisposableRule()

  @get:Rule
  val edtRule = EdtRule()

  private val tracker = TestUsageTracker(VirtualTimeScheduler())

  val task1 = mockTask(":app", "compile", "compiler.plugin", 2000).apply {
    issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(this))
  }
  val task2 = mockTask(":app", "resources", "resources.plugin", 1000)
  val task3 = mockTask(":lib", "compile", "compiler.plugin", 1000)

  val model = BuildAnalyzerViewModel(MockUiData(tasksList = listOf(task1, task2, task3)))
  val analytics = BuildAttributionUiAnalytics(projectRule.project)
  val buildSessionId = UUID.randomUUID().toString()
  val issueReporter = Mockito.mock(TaskIssueReporter::class.java)
  val controller = BuildAnalyzerViewController(model, projectRule.project, analytics, issueReporter)


  @Before
  fun setUp() {
    val ideComponents = IdeComponents(projectRule.project, disposableRule.disposable)
    UsageTracker.setWriterForTest(tracker)
    analytics.newReportSessionId(buildSessionId)
  }

  @Test
  fun testInitialWarningsFilterState() {
    val initialFilterActionsState = (warningsFilterActions(model.warningsPageModel, controller) as DefaultActionGroup).childActionsOrStubs
      .filterIsInstance<ToggleAction>()
      .map { it.templateText to it.isSelected(TestActionEvent()) }

    val expected = listOf(
      "Show Always-run tasks" to true,
      "Show Task Setup issues" to true,
      "Show issues for Android/Java/Kotlin plugins" to true,
      "Show issues for other plugins" to true,
      "Show issues for project customization" to true,
      "Show annotation processors issues" to true,
      "Include issues for tasks non determining this build duration" to false
    )

    Truth.assertThat(initialFilterActionsState).isEqualTo(expected)
  }

  @Test
  fun testShowAlwaysRunTasksFilterApplyToWarnings() {
    val filterActions = warningsFilterActions(model.warningsPageModel, controller) as DefaultActionGroup
    val filterToggleAction = filterActions.childActionsOrStubs.first { it.templateText == "Show Always-run tasks" }

    filterToggleAction.actionPerformed(TestActionEvent())
    // Mock tasks have only one Always-run tasks warning. When it is filtered out, only AP warnings should be shown.
    Truth.assertThat(model.warningsPageModel.treeRoot.childCount).isEqualTo(1)

    filterToggleAction.actionPerformed(TestActionEvent())
    // All tasks should be back.
    Truth.assertThat(model.warningsPageModel.treeRoot.childCount).isEqualTo(2)
  }

  @Test
  fun testShowTasksForAndroidPluginsFilterApplyToWarnings() {
    val filterActions = warningsFilterActions(model.warningsPageModel, controller) as DefaultActionGroup
    val filterToggleAction = filterActions.childActionsOrStubs.first { it.templateText == "Show issues for Android/Java/Kotlin plugins" }

    filterToggleAction.actionPerformed(TestActionEvent())
    // Mock tasks have only one Always-run tasks warning and task is attributed to Android plugin.
    // When it is filtered out, only AP warnings should be shown.
    Truth.assertThat(model.warningsPageModel.treeRoot.childCount).isEqualTo(1)

    filterToggleAction.actionPerformed(TestActionEvent())
    // All tasks should be back.
    Truth.assertThat(model.warningsPageModel.treeRoot.childCount).isEqualTo(2)
  }

  @Test
  fun testShowAnnotationProcessorIssuesFilterApplyToWarnings() {
    val filterActions = warningsFilterActions(model.warningsPageModel, controller) as DefaultActionGroup
    val filterToggleAction = filterActions.childActionsOrStubs.first { it.templateText == "Show annotation processors issues" }

    filterToggleAction.actionPerformed(TestActionEvent())
    // When AP warnings are filtered out only Always-run tasks warning should be shown.
    Truth.assertThat(model.warningsPageModel.treeRoot.childCount).isEqualTo(1)

    filterToggleAction.actionPerformed(TestActionEvent())
    // All tasks should be back.
    Truth.assertThat(model.warningsPageModel.treeRoot.childCount).isEqualTo(2)
  }

  @Test
  fun testInitialTaskFilterState() {
    val initialFilterActionsState = (tasksFilterActions(model.tasksPageModel, controller) as DefaultActionGroup).childActionsOrStubs
      .filterIsInstance<ToggleAction>()
      .map { it.templateText to it.isSelected(TestActionEvent()) }

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

    filterToggleAction.actionPerformed(TestActionEvent())
    // Only one of mock tasks has warnings, others should be filtered out now.
    Truth.assertThat(model.tasksPageModel.treeRoot.childCount).isEqualTo(1)

    filterToggleAction.actionPerformed(TestActionEvent())
    // All tasks should be back.
    Truth.assertThat(model.tasksPageModel.treeRoot.childCount).isEqualTo(3)
  }

  @Test
  fun testShowTasksForAndroidPluginsFilterApplyToTasks() {
    val filterActions = tasksFilterActions(model.tasksPageModel, controller) as DefaultActionGroup
    val filterToggleAction = filterActions.childActionsOrStubs.first { it.templateText == "Show tasks for Android/Java/Kotlin plugins" }

    filterToggleAction.actionPerformed(TestActionEvent())
    // All mock tasks are attributed to android plugin thus should be filtered out now.
    Truth.assertThat(model.tasksPageModel.treeRoot.childCount).isEqualTo(0)

    filterToggleAction.actionPerformed(TestActionEvent())
    // All tasks should be back.
    Truth.assertThat(model.tasksPageModel.treeRoot.childCount).isEqualTo(3)
  }
}