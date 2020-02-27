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

import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.controllers.TaskIssueReporter
import com.android.build.attribution.ui.controllers.TreeNodeSelector
import com.android.build.attribution.ui.data.AnnotationProcessorUiData
import com.android.build.attribution.ui.data.AnnotationProcessorsReport
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.BuildSummary
import com.android.build.attribution.ui.data.ConfigurationUiData
import com.android.build.attribution.ui.data.CriticalPathPluginTasksUiData
import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.CriticalPathPluginsUiData
import com.android.build.attribution.ui.data.CriticalPathTasksUiData
import com.android.build.attribution.ui.data.PluginSourceType
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskIssuesGroup
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.panels.TreeLinkListener
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.google.common.truth.Truth
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.Calendar

class TreeStructureTest {

  @get:Rule
  val projectRule: ProjectRule = ProjectRule()

  @get:Rule
  val edtRule = EdtRule()

  private val tracker = TestUsageTracker(VirtualTimeScheduler())

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
    val mockListener = mock(TreeLinkListener::class.java) as TreeLinkListener<TaskIssueUiData>
    task1.issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(task1))
    val tasksRoot = CriticalPathTasksRoot(mockCriticalPathTasksUiData(), mockRoot, mockListener)
    val expectedStructure = """
      Tasks determining this build's duration|1 warning|15.000 s|CRITICAL_PATH_TASKS_ROOT
        :app:compile|null|2.000 s|CRITICAL_PATH_TASK_PAGE
        :app:resources|null|1.000 s|CRITICAL_PATH_TASK_PAGE
        :lib:compile|null|1.000 s|CRITICAL_PATH_TASK_PAGE
    """.trimIndent()
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    Truth.assertThat(tasksRoot.printTree()).isEqualTo(expectedStructure)
    tasksRoot.verifyNoPagesFailToBuild()
  }

  @Test
  @RunsInEdt
  fun testPluginsWithTasksDeterminingBuildDuration() {
    task1.issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(task1))
    val pluginsRoot = CriticalPathPluginsRoot(mockCriticalPathPluginsUiData(), mockRoot)
    val expectedStructure = """
      Plugins with tasks determining this build's duration|1 warning|15.000 s|PLUGINS_ROOT
        compiler.plugin|1 warning|3.000 s|PLUGIN_PAGE
          Tasks determining this build's duration|1 warning|3.000 s|PLUGIN_CRITICAL_PATH_TASKS_ROOT
            :app:compile|null|2.000 s|PLUGIN_CRITICAL_PATH_TASK_PAGE
            :lib:compile|null|1.000 s|PLUGIN_CRITICAL_PATH_TASK_PAGE
          Warnings (1)|null|null|PLUGIN_WARNINGS_ROOT
            Always-run Tasks|1 warning|2.000 s|PLUGIN_ALWAYS_RUN_ISSUE_ROOT
              :app:compile|null|2.000 s|PLUGIN_ALWAYS_RUN_NO_OUTPUTS_PAGE
        resources.plugin||1.000 s|PLUGIN_PAGE
          Tasks determining this build's duration||1.000 s|PLUGIN_CRITICAL_PATH_TASKS_ROOT
            :app:resources|null|1.000 s|PLUGIN_CRITICAL_PATH_TASK_PAGE
          Warnings (0)|null|null|PLUGIN_WARNINGS_ROOT
    """.trimIndent()
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    Truth.assertThat(pluginsRoot.printTree()).isEqualTo(expectedStructure)
    pluginsRoot.verifyNoPagesFailToBuild()
  }

  @Test
  @RunsInEdt
  fun testTaskIssueRootNode() {
    val data = createIssuesGroup(
      TaskIssueType.ALWAYS_RUN_TASKS,
      listOf(
        TaskIssueUiDataContainer.AlwaysRunUpToDateOverride(task1),
        TaskIssueUiDataContainer.AlwaysRunUpToDateOverride(task2),
        TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(task3)
      ))
    val issuesRootNode = TaskIssuesRoot(data, mockRoot)
    val expectedStructure = """
      Always-run Tasks|3 warnings|null|ALWAYS_RUN_ISSUE_ROOT
        :app:compile|null|2.000 s|ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE
        :app:resources|null|1.000 s|ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE
        :lib:compile|null|1.000 s|ALWAYS_RUN_NO_OUTPUTS_PAGE
    """.trimIndent()
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    Truth.assertThat(issuesRootNode.printTree()).isEqualTo(expectedStructure)
    issuesRootNode.verifyNoPagesFailToBuild()
  }

  @Test
  @RunsInEdt
  fun testAnnotationProcessorsNode() {
    val annotationProcessorsRoot = AnnotationProcessorsRoot(mockAnnotationProcessorsData, mockRoot)
    val expectedStructure = """
      Non-incremental Annotation Processors|3 warnings|null|ANNOTATION_PROCESSORS_ROOT
        com.google.auto.value.processor.AutoAnnotationProcessor|null|0.123 s|ANNOTATION_PROCESSOR_PAGE
        com.google.auto.value.processor.AutoValueBuilderProcessor|null|0.456 s|ANNOTATION_PROCESSOR_PAGE
        com.google.auto.value.processor.AutoOneOfProcessor|null|0.789 s|ANNOTATION_PROCESSOR_PAGE
    """.trimIndent()
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    Truth.assertThat(annotationProcessorsRoot.printTree()).isEqualTo(expectedStructure)
  }

  @Test
  @RunsInEdt
  fun testWarningsNode() {
    val data = MockData().apply {
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
      annotationProcessors = mockAnnotationProcessorsData
    }

    val warningsRoot = WarningsRootNode(data, mockRoot)
    val expectedStructure = """
      Warnings (7)|null|null|WARNINGS_ROOT
        Always-run Tasks|2 warnings|null|ALWAYS_RUN_ISSUE_ROOT
          :app:compile|null|2.000 s|ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE
          :lib:compile|null|1.000 s|ALWAYS_RUN_NO_OUTPUTS_PAGE
        Task Setup Issues|2 warnings|null|TASK_SETUP_ISSUE_ROOT
          :app:compile|null|2.000 s|TASK_SETUP_ISSUE_PAGE
          :app:resources|null|1.000 s|TASK_SETUP_ISSUE_PAGE
        Non-incremental Annotation Processors|3 warnings|null|ANNOTATION_PROCESSORS_ROOT
          com.google.auto.value.processor.AutoAnnotationProcessor|null|0.123 s|ANNOTATION_PROCESSOR_PAGE
          com.google.auto.value.processor.AutoValueBuilderProcessor|null|0.456 s|ANNOTATION_PROCESSOR_PAGE
          com.google.auto.value.processor.AutoOneOfProcessor|null|0.789 s|ANNOTATION_PROCESSOR_PAGE
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

    val data = MockData().apply {
      criticalPathTasks = mockCriticalPathTasksUiData()
      criticalPathPlugins = mockCriticalPathPluginsUiData()
      buildSummary = object : BuildSummary {
        override val buildFinishedTimestamp = Calendar.getInstance().let {
          it.set(2020, 0, 30, 12, 21)
          it.timeInMillis
        }
        override val totalBuildDuration = TimeWithPercentage(totalBuildDurationMs, totalBuildDurationMs)
        override val criticalPathDuration = TimeWithPercentage(criticalPathDurationMs, totalBuildDurationMs)
        override val configurationDuration = TimeWithPercentage(0, totalBuildDurationMs)
      }
      issues = criticalPathTasks.tasks.flatMap { it.issues }.groupBy { it.type }.map { (k, v) -> createIssuesGroup(k, v) }
      annotationProcessors = mockAnnotationProcessorsData
    }

    val rootNode = RootNode(data, mockRoot.analytics, mockRoot.issueReporter, mockRoot.nodeSelector)

    val expectedBuildFinishedString = DateFormatUtil.formatDateTime(data.buildSummary.buildFinishedTimestamp)
    val expectedStructure = """
      Build:|finished at ${expectedBuildFinishedString}|20.000 s|BUILD_SUMMARY
      Plugins with tasks determining this build's duration|2 warnings|15.000 s|PLUGINS_ROOT
        compiler.plugin|1 warning|3.000 s|PLUGIN_PAGE
          Tasks determining this build's duration|1 warning|3.000 s|PLUGIN_CRITICAL_PATH_TASKS_ROOT
            :app:compile|null|2.000 s|PLUGIN_CRITICAL_PATH_TASK_PAGE
            :lib:compile|null|1.000 s|PLUGIN_CRITICAL_PATH_TASK_PAGE
          Warnings (1)|null|null|PLUGIN_WARNINGS_ROOT
            Always-run Tasks|1 warning|2.000 s|PLUGIN_ALWAYS_RUN_ISSUE_ROOT
              :app:compile|null|2.000 s|PLUGIN_ALWAYS_RUN_NO_OUTPUTS_PAGE
        resources.plugin|1 warning|1.000 s|PLUGIN_PAGE
          Tasks determining this build's duration|1 warning|1.000 s|PLUGIN_CRITICAL_PATH_TASKS_ROOT
            :app:resources|null|1.000 s|PLUGIN_CRITICAL_PATH_TASK_PAGE
          Warnings (1)|null|null|PLUGIN_WARNINGS_ROOT
            Always-run Tasks|1 warning|1.000 s|PLUGIN_ALWAYS_RUN_ISSUE_ROOT
              :app:resources|null|1.000 s|PLUGIN_ALWAYS_RUN_NO_OUTPUTS_PAGE
      Tasks determining this build's duration|2 warnings|15.000 s|CRITICAL_PATH_TASKS_ROOT
        :app:compile|null|2.000 s|CRITICAL_PATH_TASK_PAGE
        :app:resources|null|1.000 s|CRITICAL_PATH_TASK_PAGE
        :lib:compile|null|1.000 s|CRITICAL_PATH_TASK_PAGE
      Warnings (5)|null|null|WARNINGS_ROOT
        Always-run Tasks|2 warnings|null|ALWAYS_RUN_ISSUE_ROOT
          :app:compile|null|2.000 s|ALWAYS_RUN_NO_OUTPUTS_PAGE
          :app:resources|null|1.000 s|ALWAYS_RUN_NO_OUTPUTS_PAGE
        Non-incremental Annotation Processors|3 warnings|null|ANNOTATION_PROCESSORS_ROOT
          com.google.auto.value.processor.AutoAnnotationProcessor|null|0.123 s|ANNOTATION_PROCESSOR_PAGE
          com.google.auto.value.processor.AutoValueBuilderProcessor|null|0.456 s|ANNOTATION_PROCESSOR_PAGE
          com.google.auto.value.processor.AutoOneOfProcessor|null|0.789 s|ANNOTATION_PROCESSOR_PAGE
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

  private class MockData : BuildAttributionReportUiData {
    override var buildSummary = mock(BuildSummary::class.java)
    override var criticalPathTasks = mock(CriticalPathTasksUiData::class.java)
    override var criticalPathPlugins = mock(CriticalPathPluginsUiData::class.java)
    override var issues = emptyList<TaskIssuesGroup>()
    override var configurationTime = mock(ConfigurationUiData::class.java)
    override var annotationProcessors = mock(AnnotationProcessorsReport::class.java)
  }

  private val totalBuildDurationMs = 20000L
  private val criticalPathDurationMs = 15000L

  private fun mockCriticalPathTasksUiData() = object : CriticalPathTasksUiData {
    override val criticalPathDuration = TimeWithPercentage(criticalPathDurationMs, totalBuildDurationMs)
    override val miscStepsTime = criticalPathDuration.supplement()
    override val tasks: List<TaskUiData> = listOf(task1, task2, task3)
    override val warningCount: Int = tasks.count { it.hasWarning }
    override val infoCount: Int = tasks.count { it.hasInfo }
  }

  private fun mockCriticalPathPluginsUiData() = object : CriticalPathPluginsUiData {
    override val criticalPathDuration = TimeWithPercentage(criticalPathDurationMs, totalBuildDurationMs)
    override val miscStepsTime = criticalPathDuration.supplement()
    override val plugins: List<CriticalPathPluginUiData> = listOf(
      createPluginData("compiler.plugin", listOf(task1, task3)),
      createPluginData("resources.plugin", listOf(task2))
    )
    override val warningCount: Int = plugins.sumBy { it.warningCount }
    override val infoCount: Int = plugins.sumBy { it.infoCount }
  }

  private class TestTaskUiData(
    override val module: String,
    override val name: String,
    override val executionTime: TimeWithPercentage
  ) : TaskUiData {
    override val taskPath: String = "$module:$name"
    override val taskType: String = "CompilationType"
    override val executedIncrementally: Boolean = true
    override val executionMode: String = "FULL"
    override val onLogicalCriticalPath: Boolean = true
    override val onExtendedCriticalPath: Boolean = true
    override val pluginName: String = "javac"
    override val sourceType: PluginSourceType = PluginSourceType.ANDROID_PLUGIN
    override val reasonsToRun: List<String> = emptyList()
    override var issues: List<TaskIssueUiData> = emptyList()
  }

  private val task1 = TestTaskUiData(":app", "compile", TimeWithPercentage(2000, criticalPathDurationMs))
  private val task2 = TestTaskUiData(":app", "resources", TimeWithPercentage(1000, criticalPathDurationMs))
  private val task3 = TestTaskUiData(":lib", "compile", TimeWithPercentage(1000, criticalPathDurationMs))

  private fun createPluginData(name: String, tasks: List<TaskUiData>) = object : CriticalPathPluginUiData {
    override val name = name
    override val criticalPathTasks = object : CriticalPathPluginTasksUiData {
      override val tasks = tasks
      override val criticalPathDuration = TimeWithPercentage(tasks.sumByLong { it.executionTime.timeMs }, criticalPathDurationMs)
      override val warningCount = tasks.count { it.hasWarning }
      override val infoCount = tasks.count { it.hasInfo }
    }
    override val criticalPathDuration = criticalPathTasks.criticalPathDuration
    override val issues = criticalPathTasks.tasks.flatMap { it.issues }.groupBy { it.type }.map { (k, v) -> createIssuesGroup(k, v) }
    override val warningCount = criticalPathTasks.warningCount
    override val infoCount = criticalPathTasks.infoCount
  }

  private fun createIssuesGroup(type: TaskIssueType, issues: List<TaskIssueUiData>) = object : TaskIssuesGroup {
    override val type = type
    override val issues: List<TaskIssueUiData> = issues.sortedByDescending { it.task.executionTime }
    override val timeContribution =
      TimeWithPercentage(issues.map { it.task.executionTime.timeMs }.sum(), totalBuildDurationMs)
  }

  private val mockAnnotationProcessorsData = object : AnnotationProcessorsReport {
    override val nonIncrementalProcessors = listOf(
      object : AnnotationProcessorUiData {
        override val className = "com.google.auto.value.processor.AutoAnnotationProcessor"
        override val compilationTimeMs = 123L
      },
      object : AnnotationProcessorUiData {
        override val className = "com.google.auto.value.processor.AutoValueBuilderProcessor"
        override val compilationTimeMs = 456L
      },
      object : AnnotationProcessorUiData {
        override val className = "com.google.auto.value.processor.AutoOneOfProcessor"
        override val compilationTimeMs = 789L
      }
    )
  }
}