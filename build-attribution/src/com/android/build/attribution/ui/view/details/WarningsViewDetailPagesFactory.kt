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
import com.android.build.attribution.analyzers.ConfigurationCachingCompatibilityProjectResult
import com.android.build.attribution.analyzers.ConfigurationCachingTurnedOn
import com.android.build.attribution.analyzers.IncompatiblePluginWarning
import com.android.build.attribution.analyzers.IncompatiblePluginsDetected
import com.android.build.attribution.analyzers.NoIncompatiblePlugins
import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.build.attribution.ui.data.AnnotationProcessorUiData
import com.android.build.attribution.ui.data.AnnotationProcessorsReport
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.build.attribution.ui.durationStringHtml
import com.android.build.attribution.ui.htmlTextLabelWithFixedLines
import com.android.build.attribution.ui.htmlTextLabelWithLinesWrap
import com.android.build.attribution.ui.model.AnnotationProcessorDetailsNodeDescriptor
import com.android.build.attribution.ui.model.AnnotationProcessorsRootNodeDescriptor
import com.android.build.attribution.ui.model.ConfigurationCachingRootNodeDescriptor
import com.android.build.attribution.ui.model.ConfigurationCachingWarningNodeDescriptor
import com.android.build.attribution.ui.model.PluginGroupingWarningNodeDescriptor
import com.android.build.attribution.ui.model.TaskUnderPluginDetailsNodeDescriptor
import com.android.build.attribution.ui.model.TaskWarningDetailsNodeDescriptor
import com.android.build.attribution.ui.model.TaskWarningTypeNodeDescriptor
import com.android.build.attribution.ui.model.WarningsDataPageModel
import com.android.build.attribution.ui.model.WarningsPageId
import com.android.build.attribution.ui.model.WarningsTreePresentableNodeDescriptor
import com.android.build.attribution.ui.panels.taskDetailsPage
import com.android.build.attribution.ui.percentageStringHtml
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.build.attribution.ui.warningIcon
import com.android.build.attribution.ui.warningsCountString
import com.android.tools.adtui.TabularLayout
import com.intellij.ide.BrowserUtil
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkEvent

/**
 * This class creates task detail pages from the node provided.
 */
class WarningsViewDetailPagesFactory(
  val model: WarningsDataPageModel,
  val actionHandlers: ViewActionHandlers
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

  fun createDetailsPage(nodeDescriptor: WarningsTreePresentableNodeDescriptor): JComponent = when (nodeDescriptor) {
    is TaskWarningTypeNodeDescriptor -> createTaskWarningTypeDetailsPage(nodeDescriptor.warningType, nodeDescriptor.presentedWarnings)
    is TaskWarningDetailsNodeDescriptor -> createWarningDetailsPage(nodeDescriptor.issueData.task)
    is PluginGroupingWarningNodeDescriptor -> createPluginDetailsPage(nodeDescriptor.pluginName, nodeDescriptor.presentedTasksWithWarnings)
    is TaskUnderPluginDetailsNodeDescriptor -> createWarningDetailsPage(nodeDescriptor.taskData)
    is AnnotationProcessorsRootNodeDescriptor -> createAnnotationProcessorsRootDetailsPage(nodeDescriptor.annotationProcessorsReport)
    is AnnotationProcessorDetailsNodeDescriptor -> createAnnotationProcessorDetailsPage(nodeDescriptor.annotationProcessorData)
    is ConfigurationCachingRootNodeDescriptor -> createConfigurationCachingRootDetailsPage(nodeDescriptor.data,
                                                                                           nodeDescriptor.projectConfigurationTime)
    is ConfigurationCachingWarningNodeDescriptor -> createConfigurationCachingWarningPage(nodeDescriptor.data,
                                                                                          nodeDescriptor.projectConfigurationTime)
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

  private fun createWarningDetailsPage(taskData: TaskUiData) = taskDetailsPage(
    taskData,
    helpLinkListener = actionHandlers::helpLinkClicked,
    generateReportClickedListener = actionHandlers::generateReportClicked
  )

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

  private fun createConfigurationCachingRootDetailsPage(uiData: ConfigurationCachingCompatibilityProjectResult,
                                                        projectConfigurationTime: TimeWithPercentage) = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)

    val contentHtml = when (uiData) {
      is AGPUpdateRequired -> """
        <b>AGP update required to make configuration cache available</b>
        ${configurationCachingDescriptionHeader(projectConfigurationTime)}
        
        AGP supports configuration cache from ${uiData.recommendedVersion}.
        Current version of AGP is ${uiData.currentVersion}.
        Please, update your AGP version.
      """.trimIndent().insertBRTags()
      is NoIncompatiblePlugins -> """
        <b>Try to turn configuration cache on</b>
        ${configurationCachingDescriptionHeader(projectConfigurationTime)}
        
        All plugins applied in this build that we know about are compatible with configuration cache.
        This means that you can try to turn the configuration cache on to benefit from it.
        Note that we check only for a limited number of popular plugins so there still might be non-compatible plugins in your build.
        In case of any problems a detailed html report will be generated by gradle to help you address them.
        
        ${
        if (uiData.unrecognizedPlugins.isNotEmpty()) uiData.unrecognizedPlugins.joinToString(
          prefix = "<b>List of applied plugins we were not able to recognise:</b><ul>",
          postfix = "</ul>",
          separator = ""
        ) { "<li>$it</li>" }
        else ""
      }
      """.trimIndent().insertBRTags()
      is IncompatiblePluginsDetected -> {
        val incompatiblePluginsCountLine = uiData.incompatiblePluginWarnings.size.let {
          when (it) {
            0 -> null
            1 -> "1 plugin is not known to have compatible version yet, please contact plugin providers for details."
            else -> "$it plugins are not known to have compatible version yet, please contact plugin providers for details."
          }
        }

        val upgradablePluginsCountLine = uiData.upgradePluginWarnings.size.let {
          when (it) {
            0 -> null
            1 -> "1 plugin can be updated to the compatible version."
            else -> "$it plugins can be updated to the compatible version."
          }
        }
        val pluginsCountLines = sequenceOf(upgradablePluginsCountLine, incompatiblePluginsCountLine).filterNotNull()
          .joinToString(prefix = "<ul>", postfix = "</ul>", separator = "") { "<li>$it</li>" }
        """
        <b>Some plugins are not compatible with configuration cache</b>
        ${configurationCachingDescriptionHeader(projectConfigurationTime)}
        
        Some of the plugins applied are known to be not compatible with configuration cache in versions used in this build.
        $pluginsCountLines
        You can find details on each plugin on corresponding sub-pages.
      """.trimIndent().insertBRTags()
      }
      // Can not happen as we don't create pages for this state.
      ConfigurationCachingTurnedOn -> ""
    }

    add(htmlTextLabelWithLinesWrap(contentHtml).setupConfigurationCachingDescriptionPane())
  }

  private fun createConfigurationCachingWarningPage(data: IncompatiblePluginWarning,
                                                    projectConfigurationTime: TimeWithPercentage) = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)

    val contentHtml = if (data.requiredVersion != null) """
        <b>${data.plugin.displayName}: update required</b>
        ${configurationCachingDescriptionHeader(projectConfigurationTime)}
        
        Update this plugin to ${data.requiredVersion} or higher to make configuration cache available.
        
        Plugin version: ${data.currentVersion}
        Plugin dependency: ${data.pluginInfo.pluginArtifact}
      """.trimIndent().insertBRTags()
    else """
        <b>${data.plugin.displayName}: not compatible</b>
        ${configurationCachingDescriptionHeader(projectConfigurationTime)}
        
        The version of this plugin used in this build is not compatible with configuration cache and we don’t know the version when it becomes compatible.
        
        Plugin version: ${data.currentVersion}
        Plugin dependency: ${data.pluginInfo.pluginArtifact}
      """.trimIndent().insertBRTags()

    add(htmlTextLabelWithLinesWrap(contentHtml).setupConfigurationCachingDescriptionPane())
  }

  private fun configurationCachingDescriptionHeader(configurationTime: TimeWithPercentage): String =
    "<p>" +
    "You could save ${configurationTime.durationStringHtml()} (${configurationTime.percentageStringHtml()} total build time) by turning " +
    "<a href='${BuildAnalyzerBrowserLinks.CONFIGURATION_CACHING.name}'>configuration cache</a> on. " +
    "With configuration cache, Gradle can skip the configuration phase entirely when nothing that affects the build configuration has changed." +
    "</p>"

  private fun String.insertBRTags(): String = replace("\n", "<br/>\n")

  private fun JEditorPane.setupConfigurationCachingDescriptionPane() = apply {
    maximumSize = JBDimension(600, Int.MAX_VALUE)
    minimumSize = JBDimension(300, 0)
    alignmentX = 0f
    addHyperlinkListener { e ->
      //TODO (mlazeba): extract duplicated logic, same in BuldOverview page for example. Time to introduce some link handler.
      if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        BuildAnalyzerBrowserLinks.valueOf(e.description).let {
          BrowserUtil.browse(it.urlTarget)
          actionHandlers.helpLinkClicked(it)
        }
      }
    }
  }
}