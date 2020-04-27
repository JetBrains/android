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

import com.android.build.attribution.ui.DescriptionWithHelpLinkLabel
import com.android.build.attribution.ui.data.AnnotationProcessorUiData
import com.android.build.attribution.ui.data.AnnotationProcessorsReport
import com.android.build.attribution.ui.data.PluginSourceType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskIssuesGroup
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.model.AnnotationProcessorDetailsNodeDescriptor
import com.android.build.attribution.ui.model.AnnotationProcessorsRootNodeDescriptor
import com.android.build.attribution.ui.model.TaskWarningDetailsNodeDescriptor
import com.android.build.attribution.ui.model.TaskWarningTypeNodeDescriptor
import com.android.build.attribution.ui.model.WarningsTreePresentableNodeDescriptor
import com.android.build.attribution.ui.panels.generateReportLinkLabel
import com.android.build.attribution.ui.panels.htmlTextLabel
import com.android.build.attribution.ui.panels.reasonsToRunList
import com.android.build.attribution.ui.percentageString
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.build.attribution.ui.warningIcon
import com.android.build.attribution.ui.warningsCountString
import com.android.tools.adtui.TabularLayout
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * This class creates task detail pages from the node provided.
 */
class WarningsViewDetailPagesFactory(
  val actionHandlers: ViewActionHandlers
) {

  fun createDetailsPage(nodeDescriptor: WarningsTreePresentableNodeDescriptor): JComponent = when (nodeDescriptor) {
    is TaskWarningTypeNodeDescriptor -> createTaskWarningTypeDetailsPage(nodeDescriptor.warningTypeData)
    is TaskWarningDetailsNodeDescriptor -> createWarningDetailsPage(nodeDescriptor.issueData)
    is AnnotationProcessorsRootNodeDescriptor -> createAnnotationProcessorsRootDetailsPage(nodeDescriptor.annotationProcessorsReport)
    is AnnotationProcessorDetailsNodeDescriptor -> createAnnotationProcessorDetailsPage(nodeDescriptor.annotationProcessorData)
  }


  private fun createTaskWarningTypeDetailsPage(warningTypeData: TaskIssuesGroup) = JPanel().apply {
    layout = TabularLayout("Fit")
    var row = 0
    fun addRow(component: JComponent, span: Int = 1) = add(component, TabularLayout.Constraint(row++, 0, colSpan = span))

    addRow(JBLabel(warningTypeData.type.uiName).withFont(JBUI.Fonts.label().asBold()))
    val timeContribution = warningTypeData.timeContribution
    addRow(JBLabel("Duration: ${timeContribution.durationString()} / ${timeContribution.percentageString()}"))
    addRow(JPanel().apply { border = JBUI.Borders.emptyTop(20) })
    addRow(JBLabel(warningsCountString(warningTypeData.warningCount)).withFont(JBUI.Fonts.label().asBold()))
    addRow(JPanel().apply {
      layout = TabularLayout("Fit,30px,Fit")
      warningTypeData.issues.forEachIndexed { index, issue ->
        add(JBLabel(issue.task.taskPath), TabularLayout.Constraint(index, 0))
        add(JBLabel(issue.task.executionTime.durationString()), TabularLayout.Constraint(index, 2))
      }
    })
  }

  private fun createWarningDetailsPage(issueData: TaskIssueUiData) = JPanel().apply {
    var row = 0
    fun position(y: Int, x: Int = 1, span: Int = 1) = TabularLayout.Constraint(y, x, colSpan = span)
    fun addRow(component: JComponent, x: Int = 1, span: Int = 1) = add(component, TabularLayout.Constraint(row++, x, colSpan = span))
    layout = TabularLayout("Fit,Fit,*")
    add(JBLabel(warningIcon()).withBorder(JBUI.Borders.emptyRight(5)), position(0, 0))
    addRow(JBLabel(issueData.task.taskPath).withFont(JBUI.Fonts.label().asBold()))
    addRow(JBLabel("Duration: ${issueData.task.executionTime.durationString()} / ${issueData.task.executionTime.percentageString()}"))
    addRow(JBLabel("Sub-project: ${issueData.task.module}"))
    addRow(JBLabel("Plugin: ${issueData.task.pluginName}"))
    addRow(JBLabel("Type: ${issueData.task.taskType}"))
    addRow(JPanel().apply { border = JBUI.Borders.emptyTop(10) })
    addRow(DescriptionWithHelpLinkLabel(issueData.explanation, issueData.helpLink, actionHandlers::helpLinkClicked), span = 2)
    addRow(JPanel().apply { border = JBUI.Borders.emptyTop(10) })
    addRow(JBLabel("Recommendation").withFont(JBUI.Fonts.label().asBold()))
    addRow(htmlTextLabel(issueData.buildSrcRecommendation), span = 2)
    if (issueData.task.sourceType != PluginSourceType.BUILD_SRC) {
      addRow(generateReportLinkLabel(issueData.task, actionHandlers::generateReportClicked))
    }
    addRow(JPanel().apply { border = JBUI.Borders.emptyTop(10) })
    addRow(JBLabel("Reason task ran").withFont(JBUI.Fonts.label().asBold()))
    addRow(reasonsToRunList(issueData.task), span = 2)
  }

  private fun createAnnotationProcessorsRootDetailsPage(annotationProcessorsReport: AnnotationProcessorsReport) = JPanel().apply {
    layout = TabularLayout("Fit")
    var row = 0
    fun addRow(component: JComponent, span: Int = 1) = add(component, TabularLayout.Constraint(row++, 0, colSpan = span))
    addRow(JBLabel("Non-incremental Annotation Processors").withFont(JBUI.Fonts.label().asBold()))
    addRow(JPanel().apply { border = JBUI.Borders.emptyTop(10) })
    annotationProcessorsReport.nonIncrementalProcessors.forEach { addRow(JBLabel(it.className)) }
  }

  private fun createAnnotationProcessorDetailsPage(annotationProcessorData: AnnotationProcessorUiData) = JPanel().apply {
    layout = TabularLayout("Fit,3px,Fit,*")
    var row = 0
    fun addRow(component: JComponent, span: Int = 1) = add(component, TabularLayout.Constraint(row++, 3, colSpan = span))
    fun JBLabel.with2pxShift() = withBorder(JBUI.Borders.emptyLeft(2))

    add(JBLabel(warningIcon()), TabularLayout.Constraint(0, 0))
    addRow(JBLabel(annotationProcessorData.className).withFont(JBUI.Fonts.label().asBold()).with2pxShift())
    addRow(JPanel().apply { border = JBUI.Borders.emptyTop(10) })
    addRow(JBLabel(
      "This annotation processor is non-incremental and causes the JavaCompile task to always run non-incrementally."
    ).with2pxShift())
    addRow(JBLabel("Consider switching to using an incremental annotation processor.").with2pxShift())
    addRow(HyperlinkLabel("Learn more").apply {
      addHyperlinkListener { actionHandlers.helpLinkClicked() }
      setHyperlinkTarget("https://d.android.com/r/tools/build-attribution/non-incremental-ap")
    })
    addRow(JPanel().apply { border = JBUI.Borders.emptyTop(10) })
    addRow(JBLabel("Recommendation").withFont(JBUI.Fonts.label().asBold()).with2pxShift())
    addRow(JBLabel("Ensure that you are using the most recent version of this annotation processor.").with2pxShift())
  }
}