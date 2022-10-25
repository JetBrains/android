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
package com.android.build.attribution.ui.view

import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.build.attribution.ui.HtmlLinksHandler
import com.android.build.attribution.ui.data.DownloadsSummaryUIData
import com.android.build.attribution.ui.durationStringHtml
import com.android.build.attribution.ui.helpIcon
import com.android.build.attribution.ui.htmlTextLabelWithFixedLines
import com.android.build.attribution.ui.model.BuildOverviewPageModel
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.build.attribution.ui.model.shouldShowWarning
import com.android.build.attribution.ui.percentageStringHtml
import com.android.build.attribution.ui.warningIcon
import com.android.tools.adtui.TabularLayout
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.Formats
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtil.pluralize
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class BuildOverviewPageView(
  val model: BuildOverviewPageModel,
  val actionHandlers: ViewActionHandlers
) : BuildAnalyzerDataPageView {
  val linksHandler = HtmlLinksHandler(actionHandlers)

  private val buildInformationPanel = JPanel().apply {
    name = "info"
    layout = VerticalLayout(0, SwingConstants.LEFT)
    val infoPanelHtml = generateInfoPanelHtml()
    add(htmlTextLabelWithFixedLines(infoPanelHtml, linksHandler))
  }

  fun generateInfoPanelHtml(): String {
    val buildSummary = model.reportUiData.buildSummary
    val buildFinishedTime = DateFormatUtil.formatDateTime(buildSummary.buildFinishedTimestamp)

    val optionalConfigurationCacheLink = if (model.reportUiData.confCachingData.shouldShowWarning())
      linksHandler.actionLink("Optimize this", "configuration-cache") {
        actionHandlers.openConfigurationCacheWarnings()
      }.let { " - $it" }
    else ""
    return """
<b>Build finished on ${buildFinishedTime}</b><br/>
Total build duration was ${buildSummary.totalBuildDuration.durationStringHtml()}<br/>
<br/>
Includes:<br/>
Build configuration: ${buildSummary.configurationDuration.durationStringHtml()}$optionalConfigurationCacheLink<br/>
Critical path tasks execution: ${buildSummary.criticalPathDuration.durationStringHtml()}<br/>
${optionalDownloadsOverviewSection(model.downloadsSummaryUiData)}
    """.trim()
  }

  private fun optionalDownloadsOverviewSection(downloadsData: DownloadsSummaryUIData?):String {
    if (downloadsData == null || downloadsData.isEmpty) return ""
    val tooltipDetails = StringUtil.escapeXmlEntities("""
<html>
This build had ${downloadsData.sumOfRequests} network ${pluralize("request", downloadsData.sumOfRequests)},<br/>
downloaded in total ${Formats.formatFileSize(downloadsData.sumOfDataBytes)} in ${durationStringHtml(downloadsData.sumOfTimeMs)}.
</html>
    """.trim())
    val filesDownloadLink = linksHandler.actionLink("Files download", "downloads-view") {
      actionHandlers.changeViewToDownloadsLinkClicked()
    }
    return "$filesDownloadLink: ${durationStringHtml(downloadsData.sumOfTimeMs)} ${helpIcon(tooltipDetails)}<br/>"
  }

  private val linksPanel = JPanel().apply {
    name = "links"
    layout = VerticalLayout(10, SwingConstants.LEFT)
    add(htmlTextLabelWithFixedLines("<b>Common views into this build</b>").apply {
      // Add 2px left margin to align with links.
      border = JBUI.Borders.emptyLeft(2)
    })
    add(HyperlinkLabel("Tasks impacting build duration").apply {
      addHyperlinkListener { actionHandlers.changeViewToTasksLinkClicked(TasksDataPageModel.Grouping.BY_TASK_CATEGORY) }
    })
    add(HyperlinkLabel("Plugins with tasks impacting build duration").apply {
      addHyperlinkListener { actionHandlers.changeViewToTasksLinkClicked(TasksDataPageModel.Grouping.BY_PLUGIN) }
    })
    add(HyperlinkLabel("All warnings").apply {
      addHyperlinkListener { actionHandlers.changeViewToWarningsLinkClicked() }
    })
  }

  private val garbageCollectionIssuePanel = JPanel().apply {
    name = "memory"
    layout = VerticalLayout(5)
    val gcTime = model.reportUiData.buildSummary.garbageCollectionTime
    val panelHeader = "<b>Gradle Daemon Memory Utilization</b>"
    val descriptionText: String = buildString {
      append(
        "${gcTime.percentageStringHtml()} (${gcTime.durationStringHtml()}) of your build’s time was dedicated to garbage collection during this build.<br/>")
      if (model.shouldWarnAboutGC) {
        append("To reduce the amount of time spent on garbage collection, please consider increasing the Gradle daemon heap size.<br/>")
      }
      append("You can change the Gradle daemon heap size on the memory settings page.")
    }
    val action = object : AbstractAction("Edit memory settings") {
      override fun actionPerformed(e: ActionEvent?) = actionHandlers.openMemorySettings()
    }
    val controlsPanel = JPanel().apply {
      layout = HorizontalLayout(10)
      add(JButton(action), HorizontalLayout.LEFT)
    }
    val icon = if (model.shouldWarnAboutGC) warningIcon() else AllIcons.General.Information

    val defaultGCUsageWarning = htmlTextLabelWithFixedLines("", linksHandler)
    val fineTuneYourJvmLink = linksHandler.externalLink("Fine tune your JVM", BuildAnalyzerBrowserLinks.CONFIGURE_GC)
    val dontShowThisAgainLink = linksHandler.actionLink("Don't show this again", "suppress") {
      val confirmationResult = Messages.showOkCancelDialog(
        "Click OK to hide this warning in future builds.",
        "Confirm Warning Suppression",
        Messages.getOkButton(),
        Messages.getCancelButton(),
        Messages.getInformationIcon()
      )
      if (confirmationResult == Messages.OK) {
        actionHandlers.dontShowAgainNoGCSettingWarningClicked()
        defaultGCUsageWarning.isVisible = false
      }
    }
    val htmlContent = """
      |The default garbage collector was used in this build running with JDK ${model.reportUiData.buildSummary.javaVersionUsed}.<br/>
      |Note that the default GC was changed starting with JDK 9. This could impact your build performance by as much as 10%.<br/>
      |<b>Recommendation:</b> $fineTuneYourJvmLink.<br/>
      |$dontShowThisAgainLink.
    """.trimMargin()
    SwingHelper.setHtml(defaultGCUsageWarning, htmlContent, null)
    defaultGCUsageWarning.name = "no-gc-setting-warning"

    add(htmlTextLabelWithFixedLines(panelHeader))
    add(JPanel().apply {
      layout = BorderLayout(5, 5)
      add(JLabel(icon).apply { verticalAlignment = SwingConstants.TOP }, BorderLayout.WEST)
      add(htmlTextLabelWithFixedLines(descriptionText), BorderLayout.CENTER)
    })
    add(controlsPanel)
    if (model.shouldWarnAboutNoGCSetting) add(defaultGCUsageWarning)
  }

  override val component: JPanel = JPanel().apply {
    name = "build-overview"
    layout = BorderLayout()
    val content = JPanel().apply {
      border = JBUI.Borders.empty(20)
      layout = TabularLayout("Fit,50px,Fit,50px,Fit")

      add(buildInformationPanel, TabularLayout.Constraint(0, 0))
      add(linksPanel, TabularLayout.Constraint(0, 2))
      add(garbageCollectionIssuePanel, TabularLayout.Constraint(0, 4))
    }
    val scrollPane = JBScrollPane().apply {
      border = JBUI.Borders.empty()
      setViewportView(content)
    }
    add(scrollPane, BorderLayout.CENTER)
  }

  override val additionalControls: JPanel = JPanel().apply { name = "build-overview-additional-controls" }
}