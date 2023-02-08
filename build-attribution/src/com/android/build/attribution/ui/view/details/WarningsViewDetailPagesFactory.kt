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

import com.android.build.attribution.analyzers.AGPUpdateRequired
import com.android.build.attribution.analyzers.ConfigurationCacheCompatibilityTestFlow
import com.android.build.attribution.analyzers.ConfigurationCachingCompatibilityProjectResult
import com.android.build.attribution.analyzers.ConfigurationCachingTurnedOn
import com.android.build.attribution.analyzers.IncompatiblePluginWarning
import com.android.build.attribution.analyzers.IncompatiblePluginsDetected
import com.android.build.attribution.analyzers.NoIncompatiblePlugins
import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks.CONFIGURATION_CACHING
import com.android.build.attribution.ui.HtmlLinksHandler
import com.android.build.attribution.ui.createTaskCategoryIssueMessage
import com.android.build.attribution.ui.data.AnnotationProcessorUiData
import com.android.build.attribution.ui.data.AnnotationProcessorsReport
import com.android.build.attribution.ui.data.CriticalPathTaskCategoryUiData
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.build.attribution.ui.durationStringHtml
import com.android.build.attribution.ui.htmlTextLabelWithFixedLines
import com.android.build.attribution.ui.insertBRTags
import com.android.build.attribution.ui.model.AnnotationProcessorDetailsNodeDescriptor
import com.android.build.attribution.ui.model.AnnotationProcessorsRootNodeDescriptor
import com.android.build.attribution.ui.model.ConfigurationCachingRootNodeDescriptor
import com.android.build.attribution.ui.model.ConfigurationCachingWarningNodeDescriptor
import com.android.build.attribution.ui.model.JetifierUsageWarningRootNodeDescriptor
import com.android.build.attribution.ui.model.PluginGroupingWarningNodeDescriptor
import com.android.build.attribution.ui.model.TaskCategoryWarningNodeDescriptor
import com.android.build.attribution.ui.model.TaskUnderPluginDetailsNodeDescriptor
import com.android.build.attribution.ui.model.TaskWarningDetailsNodeDescriptor
import com.android.build.attribution.ui.model.TaskWarningTypeNodeDescriptor
import com.android.build.attribution.ui.model.WarningsDataPageModel
import com.android.build.attribution.ui.model.WarningsPageId
import com.android.build.attribution.ui.model.WarningsTreePresentableNodeDescriptor
import com.android.build.attribution.ui.panels.taskDetailsPage
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.build.attribution.ui.warningIcon
import com.android.build.attribution.ui.warningsCountString
import com.android.build.attribution.ui.withPluralization
import com.android.buildanalyzer.common.TaskCategoryIssue
import com.android.tools.adtui.TabularLayout
import com.android.utils.HtmlBuilder
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * This class creates task detail pages from the node provided.
 */
class WarningsViewDetailPagesFactory(
  private val model: WarningsDataPageModel,
  private val actionHandlers: ViewActionHandlers,
  private val disposable: Disposable
) {

  fun createDetailsPage(pageId: WarningsPageId): JComponent = if (pageId == WarningsPageId.emptySelection) {
    JPanel().apply {
      layout = BorderLayout()
      name = "empty-details"
      val messageLabel = JLabel("Select page for details").apply {
        verticalAlignment = SwingConstants.CENTER
        horizontalAlignment = SwingConstants.CENTER
      }
      add(messageLabel, BorderLayout.CENTER)
    }
  }
  else {
    //TODO (mlazeba): this kinda breaks the nice safety provided by sealed classes before... what can I do about it?
    //  The solution might be actually to get rid of PageId. What do I need it for? It is actually mimic the TreePath in some way.
    //  Otherwise pageId will not be unique in a tree. I think initially it was not meant to be unique in the tree.
    //  It defines the page, not the tree node. (There can be several nodes for the same page, e.g. task has 2 warnings and shown under both types)
    //  Ha, that basically means that I need page descriptor, not Node descriptor here!
    //  However that might be wrong pattern, might be better and easier to keep 1-1 relation here.
    model.getNodeDescriptorById(pageId)?.let { nodeDescriptor ->
      createDetailsPage(nodeDescriptor)
    } ?: JPanel()
  }

  @VisibleForTesting
  fun createDetailsPage(nodeDescriptor: WarningsTreePresentableNodeDescriptor): JComponent = when (nodeDescriptor) {
    is TaskWarningTypeNodeDescriptor -> createTaskWarningTypeDetailsPage(nodeDescriptor.warningType, nodeDescriptor.presentedWarnings)
    is TaskWarningDetailsNodeDescriptor -> createWarningDetailsPage(nodeDescriptor.issueData.task)
    is PluginGroupingWarningNodeDescriptor -> createPluginDetailsPage(nodeDescriptor.pluginName, nodeDescriptor.presentedTasksWithWarnings)
    is TaskUnderPluginDetailsNodeDescriptor -> createWarningDetailsPage(nodeDescriptor.taskData)
    is AnnotationProcessorsRootNodeDescriptor -> createAnnotationProcessorsRootDetailsPage(nodeDescriptor.annotationProcessorsReport)
    is AnnotationProcessorDetailsNodeDescriptor -> createAnnotationProcessorDetailsPage(nodeDescriptor.annotationProcessorData)
    is ConfigurationCachingRootNodeDescriptor ->
      ConfigurationCacheRootWarningDetailsView(nodeDescriptor.data, nodeDescriptor.projectConfigurationTime, actionHandlers).pagePanel
    is ConfigurationCachingWarningNodeDescriptor ->
      ConfigurationCachePluginWarningDetailsView(nodeDescriptor.data, nodeDescriptor.projectConfigurationTime, actionHandlers).pagePanel
    is JetifierUsageWarningRootNodeDescriptor -> JetifierWarningDetailsView(nodeDescriptor.data, actionHandlers, disposable).pagePanel
    is TaskCategoryWarningNodeDescriptor -> createTaskCategoryWarningDetailsPage(nodeDescriptor.taskCategoryData)
  }.apply {
    name = nodeDescriptor.pageId.id
  }

  private fun createPluginDetailsPage(pluginName: String, tasksWithWarnings: Map<TaskUiData, List<TaskIssueUiData>>) = JPanel().apply {
    layout = BorderLayout()
    val timeContribution = tasksWithWarnings.keys.sumByLong { it.executionTime.timeMs }
    val tableRows = tasksWithWarnings.map { (task, _) ->
      // TODO add warning count for the task to the table
      "<td>${task.taskPath}</td><td style=\"text-align:right;padding-left:10px\">${task.executionTime.durationStringHtml()}</td>"
    }
    val content = """
      <b>${pluginName}</b><br/>
      Duration: ${durationStringHtml(timeContribution)} <br/>
      <br/>
      <b>${warningsCountString(tasksWithWarnings.size)}</b>
      <table>
      ${tableRows.joinToString(separator = "\n") { "<tr>$it</tr>" }}
      </table>
    """.trimIndent()
    add(htmlTextLabelWithFixedLines(content), BorderLayout.NORTH)
  }

  private fun createTaskWarningTypeDetailsPage(warningType: TaskIssueType, warnings: List<TaskIssueUiData>) = JPanel().apply {
    layout = BorderLayout()
    val timeContribution = warnings.sumByLong { it.task.executionTime.timeMs }
    val tableRows = warnings.map { "<td>${it.task.taskPath}</td><td style=\"text-align:right;padding-left:10px\">${it.task.executionTime.durationStringHtml()}</td>" }
    val content = """
      <b>${warningType.uiName}</b><br/>
      Duration: ${durationStringHtml(timeContribution)} <br/>
      <br/>
      <b>${warningsCountString(warnings.size)}</b>
      <table>
      ${tableRows.joinToString(separator = "\n") { "<tr>$it</tr>" }}
      </table>
    """.trimIndent()
    add(htmlTextLabelWithFixedLines(content), BorderLayout.NORTH)
  }

  private fun createTaskCategoryWarningDetailsPage(
    taskCategoryData: CriticalPathTaskCategoryUiData) = JPanel().apply {
    layout = BorderLayout()
    val linksHandler = HtmlLinksHandler(actionHandlers)
    val content = taskCategoryWarningDetailsHtml(taskCategoryData, linksHandler)
    add(htmlTextLabelWithFixedLines(content, linksHandler), BorderLayout.NORTH)
  }

  private fun taskCategoryWarningDetailsHtml(taskCategoryData: CriticalPathTaskCategoryUiData, linksHandler: HtmlLinksHandler): String {
    return HtmlBuilder().apply {
      val tasksNumber = taskCategoryData.criticalPathTasks.size
      val taskCategoryWarnings = taskCategoryData.getTaskCategoryIssues(TaskCategoryIssue.Severity.WARNING, forWarningsPage = true)
      addBold(taskCategoryData.name).newline()
      add(taskCategoryData.taskCategoryDescription).newline()
      newline()
      add("Total duration: ").addHtml(durationStringHtml(taskCategoryData.criticalPathDuration.timeMs)).newline()
      add("Number of tasks:  ${ tasksNumber.withPluralization("task") }").newline()
      newline()
      addBold(taskCategoryWarnings.size.withPluralization("warning")).newline()
      createTaskCategoryIssueMessage(taskCategoryWarnings, linksHandler, actionHandlers)
    }.html
  }

  private fun createWarningDetailsPage(taskData: TaskUiData) = taskDetailsPage(taskData, actionHandlers)

  private fun createAnnotationProcessorsRootDetailsPage(annotationProcessorsReport: AnnotationProcessorsReport) = JPanel().apply {
    layout = BorderLayout()
    val listHtml = annotationProcessorsReport.nonIncrementalProcessors.joinToString(separator = "<br/>") { it.className }
    val pageHtml = """
      <b>Non-incremental Annotation Processors</b><br/>
      <br/>
      ${listHtml}
    """.trimIndent()
    add(htmlTextLabelWithFixedLines(pageHtml), BorderLayout.CENTER)
  }

  private fun createAnnotationProcessorDetailsPage(annotationProcessorData: AnnotationProcessorUiData) = JPanel().apply {
    layout = TabularLayout("Fit,3px,*")
    fun JComponent.with2pxShift() = this.apply { border = JBUI.Borders.emptyLeft(2) }

    val headerText = "<b>${annotationProcessorData.className}</b>"
    val descriptionText = """
      <br/>
      This annotation processor is non-incremental and causes the JavaCompile task to always run non-incrementally.<br/>
      Consider switching to using an incremental annotation processor.<br/>
      <br/>
      <b>Recommendation</b><br/>
    """.trimIndent()
    val linkPanel = JPanel().apply {
      layout = FlowLayout(FlowLayout.LEFT, 0, 0)
      add(HyperlinkLabel("Make sure that you are using an incremental version of this annotation processor.").apply {
        val target = BuildAnalyzerBrowserLinks.NON_INCREMENTAL_ANNOTATION_PROCESSORS
        addHyperlinkListener { actionHandlers.helpLinkClicked(target) }
        setHyperlinkTarget(target.urlTarget)
      })
    }

    add(JBLabel(warningIcon()), TabularLayout.Constraint(0, 0))
    add(htmlTextLabelWithFixedLines(headerText).with2pxShift(), TabularLayout.Constraint(0, 2))
    add(htmlTextLabelWithFixedLines(descriptionText).with2pxShift(), TabularLayout.Constraint(1, 2))
    add(linkPanel, TabularLayout.Constraint(2, 2))
  }
}