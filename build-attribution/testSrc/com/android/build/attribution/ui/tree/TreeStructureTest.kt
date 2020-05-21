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
package com.android.build.attribution.ui.tree

import com.android.build.attribution.ui.MockUiData
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.controllers.TaskIssueReporter
import com.android.build.attribution.ui.controllers.TreeNodeSelector
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.mockTask
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.google.common.truth.Truth
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.util.text.DateFormatUtil
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

class TreeStructureTest {

  @get:Rule
  val projectRule: ProjectRule = ProjectRule()

  @get:Rule
  val edtRule = EdtRule()

  private val tracker = TestUsageTracker(VirtualTimeScheduler())

  val task1 = mockTask(":app", "compile", "compiler.plugin", 2000)
  val task2 = mockTask(":app", "resources", "resources.plugin", 1000)
  val task3 = mockTask(":lib", "compile", "compiler.plugin", 1000)

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(tracker)
  }

  @After
  fun tearDown() {
    UsageTracker.cleanAfterTesting()
  }

  @Test
  @RunsInEdt
  fun testTasksDeterminingBuildDuration() {
    task1.issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(task1))
    val mockUiData = MockUiData(tasksList = listOf(task1, task2, task3))
    val tasksRoot = CriticalPathTasksRoot(mockUiData.criticalPathTasks, mockRoot)
    val expectedStructure = """
      Tasks determining this build's duration|1 warning|15.0s|CRITICAL_PATH_TASKS_ROOT
        :app:compile|null|2.0s|CRITICAL_PATH_TASK_PAGE
        :app:resources|null|1.0s|CRITICAL_PATH_TASK_PAGE
        :lib:compile|null|1.0s|CRITICAL_PATH_TASK_PAGE
    """.trimIndent()
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    Truth.assertThat(tasksRoot.printTree()).isEqualTo(expectedStructure)
    tasksRoot.verifyNoPagesFailToBuild()
  }

  @Test
  @RunsInEdt
  fun testPluginsWithTasksDeterminingBuildDuration() {
    task1.issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(task1))
    val mockUiData = MockUiData(tasksList = listOf(task1, task2, task3))
    val pluginsRoot = CriticalPathPluginsRoot(mockUiData.criticalPathPlugins, mockRoot)
    val expectedStructure = """
      Plugins with tasks determining this build's duration|1 warning|15.0s|PLUGINS_ROOT
        compiler.plugin|1 warning|3.0s|PLUGIN_PAGE
          Tasks determining this build's duration|1 warning|3.0s|PLUGIN_CRITICAL_PATH_TASKS_ROOT
            :app:compile|null|2.0s|PLUGIN_CRITICAL_PATH_TASK_PAGE
            :lib:compile|null|1.0s|PLUGIN_CRITICAL_PATH_TASK_PAGE
          Warnings (1)|null|null|PLUGIN_WARNINGS_ROOT
            Always-Run Tasks|1 warning|2.0s|PLUGIN_ALWAYS_RUN_ISSUE_ROOT
              :app:compile|null|2.0s|PLUGIN_ALWAYS_RUN_NO_OUTPUTS_PAGE
        resources.plugin||1.0s|PLUGIN_PAGE
          Tasks determining this build's duration||1.0s|PLUGIN_CRITICAL_PATH_TASKS_ROOT
            :app:resources|null|1.0s|PLUGIN_CRITICAL_PATH_TASK_PAGE
          Warnings (0)|null|null|PLUGIN_WARNINGS_ROOT
    """.trimIndent()
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    Truth.assertThat(pluginsRoot.printTree()).isEqualTo(expectedStructure)
    pluginsRoot.verifyNoPagesFailToBuild()
  }

  @Test
  @RunsInEdt
  fun testTaskIssueRootNode() {
    val mockUiData = MockUiData(tasksList = listOf(task1, task2, task3))
    val data = mockUiData.createIssuesGroup(
      TaskIssueType.ALWAYS_RUN_TASKS,
      listOf(
        TaskIssueUiDataContainer.AlwaysRunUpToDateOverride(task1),
        TaskIssueUiDataContainer.AlwaysRunUpToDateOverride(task2),
        TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(task3)
      ))
    val issuesRootNode = TaskIssuesRoot(data, mockRoot)
    val expectedStructure = """
      Always-Run Tasks|3 warnings|null|ALWAYS_RUN_ISSUE_ROOT
        :app:compile|null|2.0s|ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE
        :app:resources|null|1.0s|ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE
        :lib:compile|null|1.0s|ALWAYS_RUN_NO_OUTPUTS_PAGE
    """.trimIndent()
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    Truth.assertThat(issuesRootNode.printTree()).isEqualTo(expectedStructure)
    issuesRootNode.verifyNoPagesFailToBuild()
  }

  @Test
  @RunsInEdt
  fun testAnnotationProcessorsNode() {
    val mockUiData = MockUiData(tasksList = listOf(task1, task2, task3))
    val annotationProcessorsRoot = AnnotationProcessorsRoot(mockUiData.mockAnnotationProcessorsData(), mockRoot)
    val expectedStructure = """
      Non-incremental Annotation Processors|3 warnings|null|ANNOTATION_PROCESSORS_ROOT
        com.google.auto.value.processor.AutoAnnotationProcessor|null|0.1s|ANNOTATION_PROCESSOR_PAGE
        com.google.auto.value.processor.AutoValueBuilderProcessor|null|0.5s|ANNOTATION_PROCESSOR_PAGE
        com.google.auto.value.processor.AutoOneOfProcessor|null|0.8s|ANNOTATION_PROCESSOR_PAGE
    """.trimIndent()
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    Truth.assertThat(annotationProcessorsRoot.printTree()).isEqualTo(expectedStructure)
  }

  @Test
  @RunsInEdt
  fun testWarningsNode() {
    val data = MockUiData().apply {
      issues = listOf(
        createIssuesGroup(
          TaskIssueType.ALWAYS_RUN_TASKS,
          listOf(
            TaskIssueUiDataContainer.AlwaysRunUpToDateOverride(task1),
            TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(task3)
          )
        ),
        createIssuesGroup(
          TaskIssueType.TASK_SETUP_ISSUE,
          listOf(
            TaskIssueUiDataContainer.TaskSetupIssue(task1, task2, ""),
            TaskIssueUiDataContainer.TaskSetupIssue(task2, task1, "")
          )
        )
      )
      annotationProcessors = mockAnnotationProcessorsData()
    }

    val warningsRoot = WarningsRootNode(data, mockRoot)
    val expectedStructure = """
      Warnings (7)|null|null|WARNINGS_ROOT
        Always-Run Tasks|2 warnings|null|ALWAYS_RUN_ISSUE_ROOT
          :app:compile|null|2.0s|ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE
          :lib:compile|null|1.0s|ALWAYS_RUN_NO_OUTPUTS_PAGE
        Task Setup Issues|2 warnings|null|TASK_SETUP_ISSUE_ROOT
          :app:compile|null|2.0s|TASK_SETUP_ISSUE_PAGE
          :app:resources|null|1.0s|TASK_SETUP_ISSUE_PAGE
        Non-incremental Annotation Processors|3 warnings|null|ANNOTATION_PROCESSORS_ROOT
          com.google.auto.value.processor.AutoAnnotationProcessor|null|0.1s|ANNOTATION_PROCESSOR_PAGE
          com.google.auto.value.processor.AutoValueBuilderProcessor|null|0.5s|ANNOTATION_PROCESSOR_PAGE
          com.google.auto.value.processor.AutoOneOfProcessor|null|0.8s|ANNOTATION_PROCESSOR_PAGE
    """.trimIndent()
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    Truth.assertThat(warningsRoot.printTree()).isEqualTo(expectedStructure)
    warningsRoot.verifyNoPagesFailToBuild()
  }

  @Test
  @RunsInEdt
  fun testRootNodeStructure() {
    task1.issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(task1))
    task2.issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(task2))
    val data = MockUiData(tasksList = listOf(task1, task2, task3)).apply {
      issues = criticalPathTasks.tasks.flatMap { it.issues }.groupBy { it.type }.map { (k, v) -> createIssuesGroup(k, v) }
    }

    val rootNode = RootNode(data, mockRoot.analytics, mockRoot.issueReporter, mockRoot.nodeSelector)

    val expectedBuildFinishedString = DateFormatUtil.formatDateTime(data.buildSummary.buildFinishedTimestamp)
    val expectedStructure = """
      Build:|finished at ${expectedBuildFinishedString}|20.0s|BUILD_SUMMARY
      Plugins with tasks determining this build's duration|2 warnings|15.0s|PLUGINS_ROOT
        compiler.plugin|1 warning|3.0s|PLUGIN_PAGE
          Tasks determining this build's duration|1 warning|3.0s|PLUGIN_CRITICAL_PATH_TASKS_ROOT
            :app:compile|null|2.0s|PLUGIN_CRITICAL_PATH_TASK_PAGE
            :lib:compile|null|1.0s|PLUGIN_CRITICAL_PATH_TASK_PAGE
          Warnings (1)|null|null|PLUGIN_WARNINGS_ROOT
            Always-Run Tasks|1 warning|2.0s|PLUGIN_ALWAYS_RUN_ISSUE_ROOT
              :app:compile|null|2.0s|PLUGIN_ALWAYS_RUN_NO_OUTPUTS_PAGE
        resources.plugin|1 warning|1.0s|PLUGIN_PAGE
          Tasks determining this build's duration|1 warning|1.0s|PLUGIN_CRITICAL_PATH_TASKS_ROOT
            :app:resources|null|1.0s|PLUGIN_CRITICAL_PATH_TASK_PAGE
          Warnings (1)|null|null|PLUGIN_WARNINGS_ROOT
            Always-Run Tasks|1 warning|1.0s|PLUGIN_ALWAYS_RUN_ISSUE_ROOT
              :app:resources|null|1.0s|PLUGIN_ALWAYS_RUN_NO_OUTPUTS_PAGE
      Tasks determining this build's duration|2 warnings|15.0s|CRITICAL_PATH_TASKS_ROOT
        :app:compile|null|2.0s|CRITICAL_PATH_TASK_PAGE
        :app:resources|null|1.0s|CRITICAL_PATH_TASK_PAGE
        :lib:compile|null|1.0s|CRITICAL_PATH_TASK_PAGE
      Warnings (5)|null|null|WARNINGS_ROOT
        Always-Run Tasks|2 warnings|null|ALWAYS_RUN_ISSUE_ROOT
          :app:compile|null|2.0s|ALWAYS_RUN_NO_OUTPUTS_PAGE
          :app:resources|null|1.0s|ALWAYS_RUN_NO_OUTPUTS_PAGE
        Non-incremental Annotation Processors|3 warnings|null|ANNOTATION_PROCESSORS_ROOT
          com.google.auto.value.processor.AutoAnnotationProcessor|null|0.1s|ANNOTATION_PROCESSOR_PAGE
          com.google.auto.value.processor.AutoValueBuilderProcessor|null|0.5s|ANNOTATION_PROCESSOR_PAGE
          com.google.auto.value.processor.AutoOneOfProcessor|null|0.8s|ANNOTATION_PROCESSOR_PAGE
    """.trimIndent()
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    Truth.assertThat(rootNode.printTree()).isEqualTo(expectedStructure)
    rootNode.verifyNoPagesFailToBuild()
  }

  private fun AbstractBuildAttributionNode.printTree(): String = generateStrings().joinToString("\n")

  private fun RootNode.printTree(): String = children.asList()
    .flatMap { (it as AbstractBuildAttributionNode).generateStrings() }
    .joinToString("\n")

  private fun AbstractBuildAttributionNode.generateStrings(): List<String> {
    return mutableListOf<String>().apply {
      add("${nodeName}|${issuesCountsSuffix}|${timeSuffix}|${pageType}")
      addAll(
        children.asList().flatMap { (it as AbstractBuildAttributionNode).generateStrings().map { nodeString -> "  $nodeString" } }
      )
    }
  }

  private fun ControllersAwareBuildAttributionNode.verifyNoPagesFailToBuild() {
    val nodesList = if (this is AbstractBuildAttributionNode)
      listNodesTo(mutableListOf())
    else
      children.asList().flatMap { (it as AbstractBuildAttributionNode).listNodesTo(mutableListOf()) }
    Truth.assertThat(nodesList.collectPageCreationErrors()).isEmpty()
  }

  private fun List<AbstractBuildAttributionNode>.collectPageCreationErrors(): List<Pair<String, Throwable>> {
    return mapNotNull {
      try {
        it.component
        null
      }
      catch (t: Throwable) {
        Pair(it.name, t)
      }
    }
  }

  private fun AbstractBuildAttributionNode.listNodesTo(destinationList: MutableList<AbstractBuildAttributionNode>): List<AbstractBuildAttributionNode> {
    destinationList.add(this)
    children.forEach { (it as AbstractBuildAttributionNode).listNodesTo(destinationList) }
    return destinationList
  }

  private val mockRoot = object : ControllersAwareBuildAttributionNode(null) {
    override fun buildChildren(): Array<SimpleNode> {
      Assert.fail("This method is not supposed to be called in tests where this mock is used as root.")
      return emptyArray()
    }

    override val nodeSelector = mock(TreeNodeSelector::class.java)
    override val analytics = BuildAttributionUiAnalytics(projectRule.project)
    override val issueReporter = mock(TaskIssueReporter::class.java)
  }
}