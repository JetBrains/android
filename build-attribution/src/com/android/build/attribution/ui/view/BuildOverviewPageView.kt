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

import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.htmlTextLabelWithFixedLines
import com.android.build.attribution.ui.model.BuildAnalyzerViewModel
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.build.attribution.ui.percentageString
import com.android.build.attribution.ui.warningIcon
import com.android.tools.adtui.TabularLayout
import com.intellij.icons.AllIcons
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class BuildOverviewPageView(
  val model: BuildAnalyzerViewModel,
  val actionHandlers: ViewActionHandlers
) : BuildAnalyzerDataPageView {

  private val buildInformationPanel = JPanel().apply {
    name = "info"
    layout = VerticalLayout(0, SwingConstants.LEFT)
    val buildSummary = model.reportUiData.buildSummary
    val buildFinishedTime = DateFormatUtil.formatDateTime(buildSummary.buildFinishedTimestamp)
    val text = """
      <b>Build finished on ${buildFinishedTime}</b><br/>
      Total build duration was ${buildSummary.totalBuildDuration.durationString()}.<br/>
      <br/>
      Includes:<br/>
      Build configuration: ${buildSummary.configurationDuration.durationString()}<br/>
      Critical path tasks execution: ${buildSummary.criticalPathDuration.durationString()}<br/>
    """.trimIndent()
    add(htmlTextLabelWithFixedLines(text))
  }

  private val linksPanel = JPanel().apply {
    name = "links"
    layout = VerticalLayout(10, SwingConstants.LEFT)
    add(htmlTextLabelWithFixedLines("<b>Common views into this build</b>").apply {
      // Add 2px left margin to align with links.
      border = JBUI.Borders.emptyLeft(2)
    })
    add(HyperlinkLabel("Tasks impacting build duration").apply {
      addHyperlinkListener { actionHandlers.changeViewToTasksLinkClicked(TasksDataPageModel.Grouping.UNGROUPED) }
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
    layout = BorderLayout(5, 5)
    val gcTime = model.reportUiData.buildSummary.garbageCollectionTime
    val panelHeader = "<b>Gradle Daemon Memory Utilization</b>"
    val descriptionText = """
      ${gcTime.percentageString()} of your build’s time was dedicated to garbage collection during this build (${gcTime.durationString()}).<br/>
      To reduce the amount of time spent on garbage collection, please consider increasing the Gradle daemon heap size.<br/>
      You can do this on the memory settings page.
    """.trimIndent()
    val action = object : AbstractAction("Edit memory settings") {
      override fun actionPerformed(e: ActionEvent?) = actionHandlers.openMemorySettings()
    }
    val controlsPanel = JPanel().apply {
      layout = HorizontalLayout(10)
      add(JButton(action), HorizontalLayout.LEFT)
    }
    val icon = if (model.shouldWarnAboutGC) warningIcon() else AllIcons.General.Information

    add(htmlTextLabelWithFixedLines(panelHeader), BorderLayout.NORTH)
    add(JLabel(icon).apply { verticalAlignment = SwingConstants.TOP }, BorderLayout.WEST)
    add(htmlTextLabelWithFixedLines(descriptionText), BorderLayout.CENTER)
    add(controlsPanel, BorderLayout.SOUTH)
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