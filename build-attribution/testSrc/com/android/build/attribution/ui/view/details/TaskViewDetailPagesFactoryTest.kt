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

import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.build.attribution.ui.HtmlLinksHandler
import com.android.build.attribution.ui.MockUiData
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.mockTask
import com.android.build.attribution.ui.model.EntryDetailsNodeDescriptor
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.build.attribution.ui.model.TasksDataPageModelImpl
import com.android.build.attribution.ui.model.TasksPageId
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.buildanalyzer.common.TaskCategory
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Dimension
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.text.Document

class TaskViewDetailPagesFactoryTest {

  @get:Rule
  val edtRule = EdtRule()

  private val mockHandlers = Mockito.mock(ViewActionHandlers::class.java)

  @After
  fun clearOverride() {
    StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS.clearOverride()
  }

  @Test
  fun testCreateTaskPageWithoutWarning() {
    val taskData = mockTask(":module1", "task1", "myPlugin", 100, criticalPathDurationMs = 1000)
    val data = MockUiData(tasksList = listOf(taskData), criticalPathDurationMs = 1000)
    val model = TasksDataPageModelImpl(data)
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)

    val detailsPage = factory.createDetailsPage(TasksPageId.task(taskData, TasksDataPageModel.Grouping.BY_TASK_CATEGORY))
    assertThat(TreeWalker(detailsPage).descendants().filterIsInstance<JEditorPane>()).hasSize(1)
    // Just checking that page is created as expected, Content of the html for this page is tested in TaskDetailsPageTest.kt
  }

  @Test
  fun testCreatePluginPageWithoutWarnings() {
    val data = MockUiData(tasksList = listOf(mockTask(":module1", "task1", "myPlugin", 100)))
    val model = TasksDataPageModelImpl(data)
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)
    model.selectGrouping(TasksDataPageModel.Grouping.BY_PLUGIN)
    val pluginData = data.criticalPathPlugins.entries.first { it.name == "myPlugin" }
    val descriptor = model.getNodeDescriptorById(TasksPageId.plugin(pluginData)) as EntryDetailsNodeDescriptor

    val htmlBody = factory.entryDetailsHtml(descriptor, HtmlLinksHandler(mockHandlers)).clearHtml()
    assertThat(htmlBody).isEqualTo("""
      <B>myPlugin</B><BR/>
      Total duration: 0.1s<BR/>
      Number of tasks: 1 task<BR/>
      <BR/>
      <B>Warnings</B><BR/>
      No warnings detected for this plugin.
    """.trimIndent())
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
    val pluginData = data.criticalPathPlugins.entries.first { it.name == "myPlugin" }
    val descriptor = model.getNodeDescriptorById(TasksPageId.plugin(pluginData)) as EntryDetailsNodeDescriptor

    val htmlBody = factory.entryDetailsHtml(descriptor, HtmlLinksHandler(mockHandlers)).clearHtml()

    assertThat(htmlBody).isEqualTo("""
<B>myPlugin</B><BR/>
Total duration: 0.3s<BR/>
Number of tasks: 3 tasks<BR/>
<BR/>
<B>Warnings</B><BR/>
3 tasks with warnings associated with this plugin.<BR/>

${expectedTaskSection(":module1:task1")}

${expectedTaskSection(":module2:task1")}

${expectedTaskSection(":module3:task1")}
    """.trim())
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
    val pluginData = data.criticalPathPlugins.entries.first { it.name == "myPlugin" }
    val descriptor = model.getNodeDescriptorById(TasksPageId.plugin(pluginData)) as EntryDetailsNodeDescriptor

    val htmlBody = factory.entryDetailsHtml(descriptor, HtmlLinksHandler(mockHandlers)).clearHtml()
    assertThat(htmlBody).isEqualTo("""
<B>myPlugin</B><BR/>
Total duration: 2.0s<BR/>
Number of tasks: 20 tasks<BR/>
<BR/>
<B>Warnings</B><BR/>
20 tasks with warnings associated with this plugin.<BR/>
Top 10 warnings shown below, you can find the full list in the tree on the left.<BR/>

${expectedTaskSection(":module1:task1")}

${expectedTaskSection(":module2:task1")}

${expectedTaskSection(":module3:task1")}

${expectedTaskSection(":module4:task1")}

${expectedTaskSection(":module5:task1")}

${expectedTaskSection(":module6:task1")}

${expectedTaskSection(":module7:task1")}

${expectedTaskSection(":module8:task1")}

${expectedTaskSection(":module9:task1")}

${expectedTaskSection(":module10:task1")}
    """.trim())
  }

  private fun expectedTaskSection(taskPath: String) = """
<table><tr><td><icon alt='Warning' src='AllIcons.General.BalloonWarning'></td><td><a href='$taskPath'>$taskPath</a></td></tr>
<tr><td></td><td>Type: CompilationType<BR/>
Duration: 0.1s</td></tr>
</table>
This task runs on every build because it declares no outputs,<BR/>
which it must do in order to support incremental builds.<BR/>
<a href='NO_OUTPUTS_DECLARED_ISSUE'>Learn more</a><icon src='AllIcons.Ide.External_link_arrow'><BR/>
    """.trim()

  @Test
  @RunsInEdt
  fun testTaskNavigationLinkClicked() {
    val data = MockUiData(tasksList = "myPlugin".let { pluginName ->
      listOf(mockTask(":module1", "task1", pluginName, 100L).apply {
        issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(this))
      })
    })
    val model = TasksDataPageModelImpl(data)
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)
    model.selectGrouping(TasksDataPageModel.Grouping.BY_PLUGIN)
    val pluginData = data.criticalPathPlugins.entries.first { it.name == "myPlugin" }
    val page = factory.createDetailsPage(TasksPageId.plugin(pluginData))
    val pane = TreeWalker(page).descendants().filterIsInstance<JEditorPane>().single()

    page.size = Dimension(600, 600)
    val ui = FakeUi(page)
    ui.layoutAndDispatchEvents()

    fun FakeUi.clickOnLink(linkText: String) {
      val document: Document = pane.getDocument()
      val text = document.getText(0, document.length)
      val index: Int = text.indexOf(linkText)
      val location = pane.modelToView2D(index).bounds.location
      location.translate(2, 2)
      clickRelativeTo(pane, location.x, location.y)
    }

    ui.clickOnLink(":module1:task1")
    Mockito.verify(mockHandlers).tasksDetailsLinkClicked(TasksPageId.task(data.tasksList[0], TasksDataPageModel.Grouping.BY_PLUGIN))
  }

  @Test
  @Ignore("Currently does not work because can not open browser in test, needs refactoring, will address in the following CL")
  @RunsInEdt
  fun testLearnMoreLinkClicked() {
    val data = MockUiData(tasksList = "myPlugin".let { pluginName ->
      listOf(mockTask(":module1", "task1", pluginName, 100L).apply {
          issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(this))
        })
    })
    val model = TasksDataPageModelImpl(data)
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)
    model.selectGrouping(TasksDataPageModel.Grouping.BY_PLUGIN)
    val pluginData = data.criticalPathPlugins.entries.first { it.name == "myPlugin" }
    val page = factory.createDetailsPage(TasksPageId.plugin(pluginData))
    val pane = TreeWalker(page).descendants().filterIsInstance<JEditorPane>().single()

    page.size = Dimension(600, 600)
    val ui = FakeUi(page)
    ui.layoutAndDispatchEvents()

    fun FakeUi.clickOnLink(linkText: String) {
      val document: Document = pane.getDocument()
      val text = document.getText(0, document.length)
      val index: Int = text.indexOf(linkText)
      val location = pane.modelToView2D(index).bounds.location
      location.translate(2, 2)
      clickRelativeTo(pane, location.x, location.y)
    }

    ui.clickOnLink("Learn more")
    Mockito.verify(mockHandlers).helpLinkClicked(BuildAnalyzerBrowserLinks.NO_OUTPUTS_DECLARED_ISSUE)
  }

  @Test
  fun testCreateEmptySelectionPage() {
    val model = TasksDataPageModelImpl(MockUiData())
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)

    val detailsPage = factory.createDetailsPage(TasksPageId.emptySelection(TasksDataPageModel.Grouping.UNGROUPED))

    assertThat(detailsPage.name).isEqualTo("empty-details")
    assertThat((detailsPage.components.single() as JLabel).text).isEqualTo("Select page for details")
  }

  @Test
  fun testCreateTaskCategoryPageWithoutWarning() {
    StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS.override(true)
    val data = MockUiData(tasksList = listOf(mockTask(":module1", "task1", "myPlugin", 100, taskCategory = TaskCategory.ANDROID_RESOURCES)))
    val model = TasksDataPageModelImpl(data)
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)
    model.selectGrouping(TasksDataPageModel.Grouping.BY_TASK_CATEGORY)
    val taskCategoryData = data.criticalPathTaskCategories.entries.first{ it.name == "Android Resources" }
    val descriptor = model.getNodeDescriptorById(TasksPageId.taskCategory(taskCategoryData)) as EntryDetailsNodeDescriptor

    val htmlBody = factory.entryDetailsHtml(descriptor, HtmlLinksHandler(mockHandlers)).clearHtml()
    assertThat(htmlBody).isEqualTo("""
      <B>Android Resources</B><BR/>
      Tasks related to Android resources compilation, processing, linking and merging.<BR/>
      <BR/>
      Total duration: 0.1s<BR/>
      Number of tasks: 1 task<BR/>
      <BR/>
      <B>Warnings</B><BR/>
      No warnings detected for Android Resources category.
    """.trimIndent())
  }

  @Test
  fun testCreateTaskCategoryPageWithWarning() {
    StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS.override(true)
    val data = MockUiData(tasksList = listOf(mockTask(":module1", "task1", "myPlugin", 100, taskCategory = TaskCategory.ANDROID_RESOURCES)),
                          createTaskCategoryWarning = true)
    val model = TasksDataPageModelImpl(data)
    val factory = TaskViewDetailPagesFactory(model, mockHandlers)
    model.selectGrouping(TasksDataPageModel.Grouping.BY_TASK_CATEGORY)
    val taskCategoryData = data.criticalPathTaskCategories.entries.first{ it.name == "Android Resources" }
    val descriptor = model.getNodeDescriptorById(TasksPageId.taskCategory(taskCategoryData)) as EntryDetailsNodeDescriptor

    val htmlBody = factory.entryDetailsHtml(descriptor, HtmlLinksHandler(mockHandlers)).clearHtml()
    assertThat(htmlBody).isEqualTo("""
      <B>Android Resources</B><BR/>
      Tasks related to Android resources compilation, processing, linking and merging.<BR/>
      <BR/>
      Total duration: 0.1s<BR/>
      Number of tasks: 1 task<BR/>
      <BR/>
      <B>Warnings</B><BR/>
      1 warning associated with Android Resources category.<BR/>
      
      <table><tr><td VALIGN=TOP><icon alt='Warning' src='AllIcons.General.BalloonWarning'></td><td VALIGN=TOP>Non-transitive R classes are currently disabled.<BR/>
      Enable non-transitive R classes for faster incremental compilation.<BR/>
      <a href='AndroidMigrateToNonTransitiveRClassesAction'>Click here to migrate your project to use non-transitive R classes</a>, or <a href='NON_TRANSITIVE_R_CLASS'>Learn more</a><icon src='AllIcons.Ide.External_link_arrow'></td></tr>
      </table>
    """.trimIndent())
  }

  private fun String.clearHtml(): String = trimIndent()
    .replace("<BR/>", "<BR/>\n")
    .replace("<table>", "\n<table>")
    .replace("</table>", "</table>\n")
    .replace("</tr>", "</tr>\n")
    .trim()
}