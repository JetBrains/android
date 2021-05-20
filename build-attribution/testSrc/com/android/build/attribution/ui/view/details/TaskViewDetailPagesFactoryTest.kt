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
package com.android.build.attribution.ui.view.details

import com.android.build.attribution.ui.MockUiData
import com.android.build.attribution.ui.data.PluginSourceType
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.mockTask
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.build.attribution.ui.model.TasksDataPageModelImpl
import com.android.build.attribution.ui.model.TasksPageId
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.tools.adtui.TreeWalker
import com.google.common.truth.Truth
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel

class TaskViewDetailPagesFactoryTest {

  @get:Rule
  val applicationRule: ApplicationRule = ApplicationRule()

  @get:Rule
  val edtRule = EdtRule()

  private val mockHandlers = Mockito.mock(ViewActionHandlers::class.java)

  @Test
  fun testCreateTaskPageWithoutWarning() {
    val taskData = mockTask(":module1", "task1", "myPlugin", 100, criticalPathDurationMs = 1000)
    val data = MockUiData(tasksList = listOf(taskData), criticalPathDurationMs = 1000)
    val model = TasksDataPageModelImpl(data)
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)

    val detailsPage = factory.createDetailsPage(TasksPageId.task(taskData, TasksDataPageModel.Grouping.UNGROUPED))

    TreeWalker(detailsPage).descendants().filter { it is JEditorPane || it is JLabel }.let { textElements ->
      Truth.assertThat(clearHtml((textElements[0] as JEditorPane).text)).isEqualTo("<b>:module1:task1</b>")
      Truth.assertThat(clearHtml((textElements[1] as JEditorPane).text)).isEqualTo("""
        This task frequently determines build duration because of dependencies between its inputs/outputs and other tasks.<br>
        <br>
        <b>Duration:</b> 0.1s / 10.0%<br>
        Sub-project: :module1<br>
        Plugin: myPlugin<br>
        Type: CompilationType<br>
        <br>
        <b>Warnings</b><br>
      """.trimIndent())
      Truth.assertThat(clearHtml((textElements[2] as JLabel).text)).isEqualTo("No warnings found")
    }
  }

  @Test
  fun testAllWarningsForTaskInlined() {
    val taskData = mockTask(":module1", "task1", "myPlugin", 100, criticalPathDurationMs = 1000)
    val task2 = mockTask(":module1", "task2", "myPlugin2", 100, criticalPathDurationMs = 1000)
    taskData.issues = listOf(
      TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(taskData),
      TaskIssueUiDataContainer.AlwaysRunUpToDateOverride(taskData),
      TaskIssueUiDataContainer.TaskSetupIssue(taskData, task2, "")
    )
    val data = MockUiData(tasksList = listOf(taskData), criticalPathDurationMs = 1000)
    val model = TasksDataPageModelImpl(data)
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)

    val detailsPage = factory.createDetailsPage(TasksPageId.task(taskData, TasksDataPageModel.Grouping.UNGROUPED))

    Truth.assertThat(
      TreeWalker(detailsPage).descendants()
        .filter { it.name?.startsWith("warning-") ?: false }
        .map { it.name }
        .toList()
    ).containsExactly("warning-ALWAYS_RUN_TASKS", "warning-ALWAYS_RUN_TASKS", "warning-TASK_SETUP_ISSUE")
  }

  @Test
  fun testBuildSrcPluginTaskDoesNotHaveReportLink() {
    val taskData = mockTask(":module1", "task1", "myPlugin", 100, criticalPathDurationMs = 1000)
      .apply {
        sourceType = PluginSourceType.BUILD_SRC
        issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(this))
      }

    val data = MockUiData(tasksList = listOf(taskData), criticalPathDurationMs = 1000)
    val model = TasksDataPageModelImpl(data)
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)

    val detailsPage = factory.createDetailsPage(TasksPageId.task(taskData, TasksDataPageModel.Grouping.UNGROUPED))

    Truth.assertThat(TreeWalker(detailsPage).descendants().filter { it is HyperlinkLabel && it.text == "Generate report" })
      .isEmpty()
  }

  @Test
  fun testTaskOnExtendedCriticalPathMessage() {
    val taskData = mockTask(":module1", "task1", "myPlugin", 100, criticalPathDurationMs = 1000)
      .apply {
        onLogicalCriticalPath = false
      }

    val data = MockUiData(tasksList = listOf(taskData), criticalPathDurationMs = 1000)
    val model = TasksDataPageModelImpl(data)
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)

    val detailsPage = factory.createDetailsPage(TasksPageId.task(taskData, TasksDataPageModel.Grouping.UNGROUPED))

    Truth.assertThat(clearHtml(TreeWalker(detailsPage).descendants().filterIsInstance<JEditorPane>()[1].text))
      .startsWith("This task occasionally determines build duration because of parallelism constraints introduced by number of cores or other tasks in the same module.")
  }

  @Test
  fun testTaskWhenNoPluginInfoBecauseOfConfigCache() {
    val taskData = mockTask(":module1", "task1", "Unknown plugin", 100, pluginUnknownBecauseOfCC = true)
    val data = MockUiData(tasksList = listOf(taskData), criticalPathDurationMs = 1000)
    val model = TasksDataPageModelImpl(data)
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)

    val detailsPage = factory.createDetailsPage(TasksPageId.task(taskData, TasksDataPageModel.Grouping.UNGROUPED))
    val expectedHelpText = "Gradle did not provide plugin information for this task due to Configuration cache being enabled and its entry being reused."
    Truth.assertThat(clearHtml(TreeWalker(detailsPage).descendants().filterIsInstance<JEditorPane>()[1].text))
      .contains("Plugin: N/A <icon alt=\"$expectedHelpText\" src=\"AllIcons.General.ContextHelp\"><br>")
  }

  @Test
  fun testCreatePluginPageWithoutWarnings() {
    val data = MockUiData(tasksList = listOf(mockTask(":module1", "task1", "myPlugin", 100)))
    val model = TasksDataPageModelImpl(data)
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)
    model.selectGrouping(TasksDataPageModel.Grouping.BY_PLUGIN)
    val pluginData = data.criticalPathPlugins.plugins.first { it.name == "myPlugin" }

    val detailsPage = factory.createDetailsPage(TasksPageId.plugin(pluginData))

    Truth.assertThat(detailsPage.name).isEqualTo("myPlugin")
    TreeWalker(detailsPage).descendants().filterIsInstance<JEditorPane>().first().text.let {
      Truth.assertThat(clearHtml(it)).isEqualTo("""
        <b>myPlugin</b><br>
        Total duration: 0.1s<br>
        Number of tasks: 1 task<br>
        <br>
      """.trimIndent())
    }

    TreeWalker(detailsPage).descendants().filterIsInstance<JPanel>().first { it.name == "plugin-warnings" }.let {
      TreeWalker(it).descendants().filterIsInstance<JEditorPane>().first().text.let { headerText ->
        Truth.assertThat(clearHtml(headerText)).isEqualTo("""
          <b>Warnings</b><br>
          No warnings detected for this plugin.
        """.trimIndent())
      }
      TreeWalker(it).descendants().filterIsInstance<JPanel>().first { it.name == "plugin-warnings-list" }.let { warningsListPanel ->
        Truth.assertThat(warningsListPanel.components).isEmpty()
      }
    }
  }

  @Test
  fun testCreatePluginPageWithThreeWarnings() {
    val data = MockUiData(tasksList = "myPlugin".let { pluginName ->
      (1..3).map {
        mockTask(":module$it", "task1", pluginName, 100L).apply {
          issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(this))
        }
      }
    })
    val model = TasksDataPageModelImpl(data)
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)
    model.selectGrouping(TasksDataPageModel.Grouping.BY_PLUGIN)
    val pluginData = data.criticalPathPlugins.plugins.first { it.name == "myPlugin" }

    val detailsPage = factory.createDetailsPage(TasksPageId.plugin(pluginData))

    TreeWalker(detailsPage).descendants().filterIsInstance<JPanel>().first { it.name == "plugin-warnings" }.let {
      TreeWalker(it).descendants().filterIsInstance<JEditorPane>().first().text.let { headerText ->
        Truth.assertThat(clearHtml(headerText)).isEqualTo("""
          <b>Warnings</b><br>
          3 tasks with warnings associated with this plugin.<br>
        """.trimIndent())
      }
      TreeWalker(it).descendants().filterIsInstance<JPanel>().first { it.name == "plugin-warnings-list" }.let { warningsListPanel ->
        Truth.assertThat(warningsListPanel.components).hasLength(3)
      }
    }
  }

  @Test
  fun testCreatePluginPageWith20Warnings() {
    val data = MockUiData(tasksList = (1..20).map {
      mockTask(":module$it", "task1", "myPlugin", 100L).apply {
        issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(this))
      }
    })
    val model = TasksDataPageModelImpl(data)
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)
    model.selectGrouping(TasksDataPageModel.Grouping.BY_PLUGIN)
    val pluginData = data.criticalPathPlugins.plugins.first { it.name == "myPlugin" }

    val detailsPage = factory.createDetailsPage(TasksPageId.plugin(pluginData))

    TreeWalker(detailsPage).descendants().filterIsInstance<JPanel>().first { it.name == "plugin-warnings" }.let {
      TreeWalker(it).descendants().filterIsInstance<JEditorPane>().first().text.let { headerText ->
        Truth.assertThat(clearHtml(headerText)).isEqualTo("""
          <b>Warnings</b><br>
          20 tasks with warnings associated with this plugin.<br>
          Top 10 tasks shown below, you can find the full list in the tree on the left.<br>
        """.trimIndent())
      }
      TreeWalker(it).descendants().filterIsInstance<JPanel>().first { it.name == "plugin-warnings-list" }.let { warningsListPanel ->
        Truth.assertThat(warningsListPanel.components).hasLength(10)
      }
    }
  }

  @Test
  fun testCreateEmptySelectionPage() {
    val model = TasksDataPageModelImpl(MockUiData())
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)

    val detailsPage = factory.createDetailsPage(TasksPageId.emptySelection(TasksDataPageModel.Grouping.UNGROUPED))

    Truth.assertThat(detailsPage.name).isEqualTo("empty-details")
    Truth.assertThat((detailsPage.components.single() as JLabel).text).isEqualTo("Select page for details")
  }

  private fun clearHtml(html: String): String = UIUtil.getHtmlBody(html)
    .trimIndent()
    .replace("\n", "")
    .replace("<br>", "<br>\n")
    .trim()
}