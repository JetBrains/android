/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.ui.tree

import com.android.build.attribution.ui.data.AnnotationProcessorUiData
import com.android.build.attribution.ui.data.AnnotationProcessorsReport
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.issuesCountString
import com.android.build.attribution.ui.panels.AbstractBuildAttributionInfoPanel
import com.android.build.attribution.ui.panels.AnnotationProcessorIssueInfoPanel
import com.android.build.attribution.ui.panels.headerLabel
import com.android.build.attribution.ui.warningIcon
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.treeStructure.SimpleNode
import javax.swing.Icon
import javax.swing.JComponent

class AnnotationProcessorsRoot(
  private val annotationProcessorsReport: AnnotationProcessorsReport,
  parent: ControllersAwareBuildAttributionNode
) : AbstractBuildAttributionNode(parent, "Non-incremental Annotation Processors") {

  override val presentationIcon: Icon? = null

  override val issuesCountsSuffix: String? = issuesCountString(annotationProcessorsReport.issueCount, 0)

  override val timeSuffix: String? = null

  override val pageType = BuildAttributionUiEvent.Page.PageType.ANNOTATION_PROCESSORS_ROOT

  override fun createComponent(): AbstractBuildAttributionInfoPanel = object : AbstractBuildAttributionInfoPanel() {

    override fun createHeader(): JComponent = headerLabel("Non-incremental Annotation Processors")

    override fun createBody(): JComponent {
      val listPanel = JBPanel<JBPanel<*>>(VerticalLayout(6))
      children.forEach {
        val text = (it as? AbstractBuildAttributionNode)?.nodeName ?: it.name
        val link = HyperlinkLabel(text)
        link.addHyperlinkListener { _ -> nodeSelector.selectNode(it) }
        listPanel.add(link)
      }
      return listPanel
    }
  }

  override fun buildChildren(): Array<SimpleNode> = annotationProcessorsReport.nonIncrementalProcessors
    .map { processor -> AnnotationProcessorNode(processor, this) }
    .toTypedArray()
}

private class AnnotationProcessorNode(
  private val annotationProcessor: AnnotationProcessorUiData,
  parent: AnnotationProcessorsRoot
) : AbstractBuildAttributionNode(parent, annotationProcessor.className) {

  override val presentationIcon: Icon? = warningIcon()

  override val issuesCountsSuffix: String? = null

  override val timeSuffix: String? = durationString(annotationProcessor.compilationTimeMs)

  override val pageType = BuildAttributionUiEvent.Page.PageType.ANNOTATION_PROCESSOR_PAGE

  override fun createComponent(): AbstractBuildAttributionInfoPanel = object : AbstractBuildAttributionInfoPanel() {

    override fun createHeader(): JComponent = headerLabel(annotationProcessor.className)

    override fun createBody(): JComponent = AnnotationProcessorIssueInfoPanel(annotationProcessor, analytics)
  }

  override fun buildChildren(): Array<SimpleNode> = emptyArray()
}
