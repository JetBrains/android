/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.build.attribution.ui.data.PluginSourceType
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.mockTask
import com.android.build.attribution.ui.panels.taskDetailsPage
import com.android.build.attribution.ui.panels.taskDetailsPanelHtml
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.buildanalyzer.common.TaskCategory
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Dimension
import javax.swing.JEditorPane
import javax.swing.text.Document

class TaskDetailsPageTest {

  @get:Rule
  val edtRule = EdtRule()

  private val mockHandlers = Mockito.mock(ViewActionHandlers::class.java)

  @Before
  fun setUp() {
    StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS.override(false)
  }

  @After
  fun clearOverride() {
    StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS.clearOverride()
  }

  @Test
  fun testTaskPageOnLogicalCriticalPathWithReasons() {
    val taskData = mockTask(":module1", "task1", "myPlugin", 100, criticalPathDurationMs = 1000).apply {
      onLogicalCriticalPath = true
      onExtendedCriticalPath = true
      reasonsToRun = listOf("""
        Reason one
        And it is multiline
      """.trimIndent(),
                            "Second reason")
    }

    val htmlBody = taskDetailsPanelHtml(taskData, mockHandlers, HtmlLinksHandler(mockHandlers)).clearHtml()
    assertThat(htmlBody).isEqualTo("""
      <B>:module1:task1</B><BR/>
      This task frequently determines build duration because of dependencies<BR/>
      between its inputs/outputs and other tasks.<BR/>
      <BR/>
      <B>Duration:</B>  0.1s / 10.0%<BR/>
      Sub-project: :module1<BR/>
      Plugin: myPlugin<BR/>
      Type: CompilationType<BR/>
      <BR/>
      <B>Warnings</B><BR/>
      No warnings found<BR/>
      <BR/>
      <B>Reason task ran</B><BR/>
      Reason one<BR/>
      And it is multiline<BR/>
      Second reason<BR/>
      """.trimIndent())
  }

  @Test
  fun testTaskPageOnExtendedCriticalPathWithWarnings() {
    val taskData = mockTask(":module1", "task1", "myPlugin", 100, criticalPathDurationMs = 1000).apply {
      onLogicalCriticalPath = false
      onExtendedCriticalPath = true
      issues = listOf(
        TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(this),
        TaskIssueUiDataContainer.AlwaysRunUpToDateOverride(this),
      )
    }

    val htmlBody = taskDetailsPanelHtml(taskData, mockHandlers, HtmlLinksHandler(mockHandlers)).clearHtml()
    assertThat(htmlBody).isEqualTo("""
      <B>:module1:task1</B><BR/>
      This task occasionally determines build duration because of parallelism constraints<BR/>
      introduced by number of cores or other tasks in the same module.<BR/>
      <BR/>
      <B>Duration:</B>  0.1s / 10.0%<BR/>
      Sub-project: :module1<BR/>
      Plugin: myPlugin<BR/>
      Type: CompilationType<BR/>
      <BR/>
      <B>Warnings</B><BR/>
      Consider filing a bug to report this issue to the plugin developer. <a href='generateReport'>Generate report</a>
      <table><tr><td><icon alt='Warning' src='AllIcons.General.BalloonWarning'></td><td><B>Always-Run Tasks</B></td></tr>
      <tr><td></td><td>This task runs on every build because it declares no outputs,<BR/>
      which it must do in order to support incremental builds.<BR/>
      <a href='NO_OUTPUTS_DECLARED_ISSUE'>Learn more</a><icon src='AllIcons.Ide.External_link_arrow'></td></tr>
      <tr><td></td><td><B>Recommendation:</B> Annotate the task output fields with one of:<BR/>
      OutputDirectory, OutputDirectories, OutputFile, OutputFiles</td></tr>
      <tr><td><icon alt='Warning' src='AllIcons.General.BalloonWarning'></td><td><B>Always-Run Tasks</B></td></tr>
      <tr><td></td><td>This task might be setting its up-to-date check to always return <code>false</code>,<BR/>
      which means that it must regenerate its output during every build.<BR/>
      For example, the task might set the following: <code>outputs.upToDateWhen { false }</code>.<BR/>
      To optimize task execution with up-to-date checks, remove the <code>upToDateWhen</code> enclosure.<BR/>
      <a href='UP_TO_DATE_EQUALS_FALSE_ISSUE'>Learn more</a><icon src='AllIcons.Ide.External_link_arrow'></td></tr>
      <tr><td></td><td><B>Recommendation:</B> Ensure that you don't automatically override up-to-date checks.</td></tr>
      </table>
      <B>Reason task ran</B><BR/>
      No info
      """.trimIndent())
  }

  @Test
  fun testBuildSrcPluginTaskWithWarningsDoesNotHaveReportLink() {
    val taskData = mockTask(":module1", "task1", "myPlugin", 100, criticalPathDurationMs = 1000).apply {
      onLogicalCriticalPath = false
      onExtendedCriticalPath = true
      sourceType = PluginSourceType.BUILD_SRC
      issues = listOf(
        TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(this),
        TaskIssueUiDataContainer.AlwaysRunUpToDateOverride(this),
      )
    }

    val htmlBody = taskDetailsPanelHtml(taskData, mockHandlers, HtmlLinksHandler(mockHandlers)).clearHtml()
    assertThat(htmlBody).isEqualTo("""
      <B>:module1:task1</B><BR/>
      This task occasionally determines build duration because of parallelism constraints<BR/>
      introduced by number of cores or other tasks in the same module.<BR/>
      <BR/>
      <B>Duration:</B>  0.1s / 10.0%<BR/>
      Sub-project: :module1<BR/>
      Plugin: myPlugin<BR/>
      Type: CompilationType<BR/>
      <BR/>
      <B>Warnings</B><BR/>
      
      <table><tr><td><icon alt='Warning' src='AllIcons.General.BalloonWarning'></td><td><B>Always-Run Tasks</B></td></tr>
      <tr><td></td><td>This task runs on every build because it declares no outputs,<BR/>
      which it must do in order to support incremental builds.<BR/>
      <a href='NO_OUTPUTS_DECLARED_ISSUE'>Learn more</a><icon src='AllIcons.Ide.External_link_arrow'></td></tr>
      <tr><td></td><td><B>Recommendation:</B> Annotate the task output fields with one of:<BR/>
      OutputDirectory, OutputDirectories, OutputFile, OutputFiles</td></tr>
      <tr><td><icon alt='Warning' src='AllIcons.General.BalloonWarning'></td><td><B>Always-Run Tasks</B></td></tr>
      <tr><td></td><td>This task might be setting its up-to-date check to always return <code>false</code>,<BR/>
      which means that it must regenerate its output during every build.<BR/>
      For example, the task might set the following: <code>outputs.upToDateWhen { false }</code>.<BR/>
      To optimize task execution with up-to-date checks, remove the <code>upToDateWhen</code> enclosure.<BR/>
      <a href='UP_TO_DATE_EQUALS_FALSE_ISSUE'>Learn more</a><icon src='AllIcons.Ide.External_link_arrow'></td></tr>
      <tr><td></td><td><B>Recommendation:</B> Ensure that you don't automatically override up-to-date checks.</td></tr>
      </table>
      <B>Reason task ran</B><BR/>
      No info
      """.trimIndent())
  }

  @Test
  fun testTaskPageNotOnCriticalPath() {
    val taskData = mockTask(":module1", "task1", "myPlugin", 50, criticalPathDurationMs = 1000).apply {
      onLogicalCriticalPath = false
      onExtendedCriticalPath = false
    }

    val htmlBody = taskDetailsPanelHtml(taskData, mockHandlers, HtmlLinksHandler(mockHandlers)).clearHtml()
    assertThat(htmlBody).isEqualTo("""
      <B>:module1:task1</B><BR/>
      <BR/>
      <B>Duration:</B>  &lt;0.1s / 5.0%<BR/>
      Sub-project: :module1<BR/>
      Plugin: myPlugin<BR/>
      Type: CompilationType<BR/>
      <BR/>
      <B>Warnings</B><BR/>
      No warnings found<BR/>
      <BR/>
      <B>Reason task ran</B><BR/>
      No info
      """.trimIndent())
  }

  @Test
  fun testTaskWhenNoPluginInfoBecauseOfConfigCache() {
    val taskData = mockTask(":module1", "task1", "Unknown plugin", 100, criticalPathDurationMs = 1000,
                            pluginUnknownBecauseOfCC = true).apply {
      onLogicalCriticalPath = false
      onExtendedCriticalPath = false
    }

    val htmlBody = taskDetailsPanelHtml(taskData, mockHandlers, HtmlLinksHandler(mockHandlers)).clearHtml()

    val expectedHelpText = "Gradle did not provide plugin information for this task due to Configuration cache being enabled and its entry being reused."

    assertThat(htmlBody).isEqualTo("""
      <B>:module1:task1</B><BR/>
      <BR/>
      <B>Duration:</B>  0.1s / 10.0%<BR/>
      Sub-project: :module1<BR/>
      Plugin: N/A <icon alt='$expectedHelpText' src='AllIcons.General.ContextHelp'><BR/>
      Type: CompilationType<BR/>
      <BR/>
      <B>Warnings</B><BR/>
      No warnings found<BR/>
      <BR/>
      <B>Reason task ran</B><BR/>
      No info
      """.trimIndent())
  }

  @Test
  @RunsInEdt
  fun testGenerateReportLinkClicked() {
    val taskData = mockTask(":module1", "task1", "myPlugin", 100, criticalPathDurationMs = 1000).apply {
      onLogicalCriticalPath = false
      onExtendedCriticalPath = true
      issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(this))
    }

    val page = taskDetailsPage(taskData, mockHandlers)
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

    ui.clickOnLink("Generate report")
    Mockito.verify(mockHandlers).generateReportClicked(taskData)
  }

  @Test
  @Ignore("Currently does not work because can not open browser in test, needs refactoring, will address in the following CL")
  @RunsInEdt
  fun testLearnMoreLinkClicked() {
    val taskData = mockTask(":module1", "task1", "myPlugin", 100, criticalPathDurationMs = 1000).apply {
      onLogicalCriticalPath = false
      onExtendedCriticalPath = true
      issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(this))
    }

    val page = taskDetailsPage(taskData, mockHandlers)
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
  fun testTaskPageShowsTaskCategoryWhenFlagEnabled() {
    StudioFlags.BUILD_ANALYZER_CATEGORY_ANALYSIS.override(true)
    val taskData = mockTask(":module1", "task1", "myPlugin", 50, criticalPathDurationMs = 1000, taskCategory = TaskCategory.ANDROID_RESOURCES).apply {
      onLogicalCriticalPath = false
      onExtendedCriticalPath = false
    }

    val htmlBody = taskDetailsPanelHtml(taskData, mockHandlers, HtmlLinksHandler(mockHandlers)).clearHtml()
    assertThat(htmlBody).isEqualTo("""
      <B>:module1:task1</B><BR/>
      <BR/>
      <B>Duration:</B>  &lt;0.1s / 5.0%<BR/>
      Sub-project: :module1<BR/>
      Plugin: myPlugin<BR/>
      Type: CompilationType<BR/>
      Task Execution Categories: Android Resources<BR/>
      <BR/>
      <B>Warnings</B><BR/>
      No warnings found<BR/>
      <BR/>
      <B>Reason task ran</B><BR/>
      No info
      """.trimIndent())
  }

  private fun String.clearHtml(): String = trimIndent()
    .replace("<BR/>", "<BR/>\n")
    .replace("<table>", "\n<table>")
    .replace("</table>", "</table>\n")
    .replace("</tr>", "</tr>\n")
    .trim()

}