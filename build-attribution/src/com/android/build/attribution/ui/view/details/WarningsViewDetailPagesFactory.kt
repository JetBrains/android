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
import com.android.build.attribution.ui.data.AnnotationProcessorUiData
import com.android.build.attribution.ui.data.AnnotationProcessorsReport
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskIssuesGroup
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.model.AnnotationProcessorDetailsNodeDescriptor
import com.android.build.attribution.ui.model.AnnotationProcessorsRootNodeDescriptor
import com.android.build.attribution.ui.model.TaskWarningDetailsNodeDescriptor
import com.android.build.attribution.ui.model.TaskWarningTypeNodeDescriptor
import com.android.build.attribution.ui.model.WarningsTreePresentableNodeDescriptor
import com.android.build.attribution.ui.htmlTextLabelWithFixedLines
import com.android.build.attribution.ui.panels.taskDetailsPanel
import com.android.build.attribution.ui.percentageString
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.build.attribution.ui.warningIcon
import com.android.build.attribution.ui.warningsCountString
import com.android.tools.adtui.TabularLayout
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
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
  }.apply {
    name = nodeDescriptor.pageId.id
  }


  private fun createTaskWarningTypeDetailsPage(warningTypeData: TaskIssuesGroup) = JPanel().apply {
    layout = BorderLayout()

    val timeContribution = warningTypeData.timeContribution
    val text = """
      <b>${warningTypeData.type.uiName}</b><br/>
      Duration: ${timeContribution.durationString()} / ${timeContribution.percentageString()}<br/>
      <br/>
      <b>${warningsCountString(warningTypeData.warningCount)}</b>
    """.trimIndent()
    add(htmlTextLabelWithFixedLines(text), BorderLayout.NORTH)
    add(JPanel().apply {
      layout = TabularLayout("Fit,30px,Fit")
      warningTypeData.issues.forEachIndexed { index, issue ->
        add(JBLabel(issue.task.taskPath), TabularLayout.Constraint(index, 0))
        add(JBLabel(issue.task.executionTime.durationString()), TabularLayout.Constraint(index, 2))
      }
    }, BorderLayout.CENTER)
  }

  private fun createWarningDetailsPage(issueData: TaskIssueUiData) = JPanel().apply {
    layout = BorderLayout()
    add(htmlTextLabelWithFixedLines("<b>${issueData.task.taskPath}</b>"), BorderLayout.NORTH)
    add(taskDetailsPanel(
      issueData.task,
      helpLinkListener = actionHandlers::helpLinkClicked,
      generateReportClickedListener = actionHandlers::generateReportClicked
    ), BorderLayout.CENTER)
  }

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
    """.trimIndent()
    val recommendationText = """
      <br/>
      <b>Recommendation</b><br/>
      Ensure that you are using the most recent version of this annotation processor.<br/>
    """.trimIndent()
    val linkPanel = JPanel().apply {
      layout = FlowLayout(FlowLayout.LEFT, 0, 0)
      add(HyperlinkLabel("Learn more").apply {
        val target = BuildAnalyzerBrowserLinks.NON_INCREMENTAL_ANNOTATION_PROCESSORS
        addHyperlinkListener { actionHandlers.helpLinkClicked(target) }
        setHyperlinkTarget(target.urlTarget)
      })
    }

    add(JBLabel(warningIcon()), TabularLayout.Constraint(0, 0))
    add(htmlTextLabelWithFixedLines(headerText).with2pxShift(), TabularLayout.Constraint(0, 2))
    add(htmlTextLabelWithFixedLines(descriptionText).with2pxShift(), TabularLayout.Constraint(1, 2))
    add(linkPanel, TabularLayout.Constraint(2, 2))
    add(htmlTextLabelWithFixedLines(recommendationText).with2pxShift(), TabularLayout.Constraint(3, 2))
  }
}