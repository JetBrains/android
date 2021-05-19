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
  fun testCreatePluginPageWithoutWarnings() {
    val data = MockUiData(tasksList = listOf(mockTask(":module1", "task1", "plugin", 100)))
    val model = TasksDataPageModelImpl(data)
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)
    model.selectGrouping(TasksDataPageModel.Grouping.BY_PLUGIN)
    val pluginData = data.criticalPathPlugins.plugins.first { it.name == "plugin" }

    val detailsPage = factory.createDetailsPage(TasksPageId.plugin(pluginData))

    Truth.assertThat(detailsPage.name).isEqualTo("plugin")
    TreeWalker(detailsPage).descendants().filterIsInstance<JEditorPane>().first().text.let {
      Truth.assertThat(clearHtml(it)).isEqualTo("""
        <b>plugin</b><br>
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
      TreeWalker(it).descendants().filterIsInstance<JPanel>().first {  it.name == "plugin-warnings-list" }.let { warningsListPanel ->
        Truth.assertThat(warningsListPanel.components).isEmpty()
      }
    }
  }

  @Test
  fun testCreatePluginPageWithThreeWarnings() {
    val data = MockUiData(tasksList = "plugin".let { pluginName ->
      (1..3).map {
        mockTask(":module$it", "task1", pluginName, 100L).apply {
          issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(this))
        }
      }
    })
    val model = TasksDataPageModelImpl(data)
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)
    model.selectGrouping(TasksDataPageModel.Grouping.BY_PLUGIN)
    val pluginData = data.criticalPathPlugins.plugins.first { it.name == "plugin" }

    val detailsPage = factory.createDetailsPage(TasksPageId.plugin(pluginData))

    TreeWalker(detailsPage).descendants().filterIsInstance<JPanel>().first { it.name == "plugin-warnings" }.let {
      TreeWalker(it).descendants().filterIsInstance<JEditorPane>().first().text.let { headerText ->
        Truth.assertThat(clearHtml(headerText)).isEqualTo("""
          <b>Warnings</b><br>
          3 tasks with warnings associated with this plugin.<br>
        """.trimIndent())
      }
      TreeWalker(it).descendants().filterIsInstance<JPanel>().first {  it.name == "plugin-warnings-list" }.let { warningsListPanel ->
        Truth.assertThat(warningsListPanel.components).hasLength(3)
      }
    }
  }

  @Test
  fun testCreatePluginPageWith20Warnings() {
    val data = MockUiData(tasksList = (1..20).map {
      mockTask(":module$it", "task1", "plugin", 100L).apply {
        issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(this))
      }
    })
    val model = TasksDataPageModelImpl(data)
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)
    model.selectGrouping(TasksDataPageModel.Grouping.BY_PLUGIN)
    val pluginData = data.criticalPathPlugins.plugins.first { it.name == "plugin" }

    val detailsPage = factory.createDetailsPage(TasksPageId.plugin(pluginData))

    TreeWalker(detailsPage).descendants().filterIsInstance<JPanel>().first { it.name == "plugin-warnings" }.let {
      TreeWalker(it).descendants().filterIsInstance<JEditorPane>().first().text.let { headerText ->
        Truth.assertThat(clearHtml(headerText)).isEqualTo("""
          <b>Warnings</b><br>
          20 tasks with warnings associated with this plugin.<br>
          Top 10 tasks shown below, you can find the full list in the tree on the left.<br>
        """.trimIndent())
      }
      TreeWalker(it).descendants().filterIsInstance<JPanel>().first {  it.name == "plugin-warnings-list" }.let { warningsListPanel ->
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
    .replace("\n","")
    .replace("<br>","<br>\n")
    .trim()
}