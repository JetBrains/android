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
import com.android.build.attribution.ui.model.BuildAnalyzerViewModel
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.tools.adtui.TabularLayout
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import javax.swing.JPanel
import javax.swing.SwingConstants

class BuildOverviewPageView(
  val model: BuildAnalyzerViewModel,
  val actionHandlers: ViewActionHandlers
) : BuildAnalyzerDataPageView {

  private val buildInformationPanel = JPanel().apply {
    layout = VerticalLayout(0, SwingConstants.LEFT)
    val buildSummary = model.reportUiData.buildSummary
    val buildFinishedTime = DateFormatUtil.formatDateTime(buildSummary.buildFinishedTimestamp)
    add(JBLabel("Build finished on ${buildFinishedTime}").withFont(JBUI.Fonts.label().asBold()))
    add(JBLabel("Total build duration was ${buildSummary.totalBuildDuration.durationString()}."))
    add(JBLabel("Includes:").withBorder(JBUI.Borders.emptyTop(20)))
    add(JBLabel("Build configuration: ${buildSummary.configurationDuration.durationString()}"))
    add(JBLabel("Critical path tasks execution: ${buildSummary.criticalPathDuration.durationString()}"))
  }

  private val linksPanel = JPanel().apply {
    name = "links"
    layout = VerticalLayout(10, SwingConstants.LEFT)
    add(JBLabel("Common views into this build").withFont(JBUI.Fonts.label().asBold()))
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

  override val component: JPanel = JPanel().apply {
    name = "build-overview"
    border = JBUI.Borders.empty(20)
    layout = TabularLayout("Fit,50px,Fit")

    add(buildInformationPanel, TabularLayout.Constraint(0, 0))
    add(linksPanel, TabularLayout.Constraint(0, 2))
  }

  override val additionalControls: JPanel = JPanel().apply { name = "build-overview-additional-controls" }
}