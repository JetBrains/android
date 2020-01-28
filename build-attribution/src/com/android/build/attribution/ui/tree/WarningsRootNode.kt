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

import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.panels.AbstractBuildAttributionInfoPanel
import com.android.build.attribution.ui.panels.headerLabel
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.treeStructure.SimpleNode
import javax.swing.Icon
import javax.swing.JComponent

class WarningsRootNode(
  private val reportData: BuildAttributionReportUiData,
  parent: ControllersAwareBuildAttributionNode
) : AbstractBuildAttributionNode(parent, "Warnings (${reportData.totalIssuesCount})") {

  // TODO(mlazeba): change to new type when added and merged b/144767316
  override val pageType = BuildAttributionUiEvent.Page.PageType.UNKNOWN_PAGE
  override val presentationIcon: Icon? = null
  override val issuesCountsSuffix: String? = null
  override val timeSuffix: String? = null

  override fun buildChildren(): Array<SimpleNode> {
    val nodes = mutableListOf<SimpleNode>()
    reportData.issues.forEach {
      nodes.add(TaskIssuesRoot(it, this))
    }
    if (reportData.annotationProcessors.issueCount > 0) {
      nodes.add(AnnotationProcessorsRoot(reportData.annotationProcessors, this))
    }

    return nodes.toTypedArray()
  }

  override fun createComponent(): AbstractBuildAttributionInfoPanel = object : AbstractBuildAttributionInfoPanel() {

    override fun createHeader(): JComponent {
      return headerLabel("Warnings")
    }

    override fun createBody(): JComponent {
      val listPanel = JBPanel<JBPanel<*>>(VerticalLayout(6))
      val totalWarningsCount = reportData.totalIssuesCount
      listPanel.add(JBLabel().apply {
        text = if (children.isEmpty())
          "No warnings detected for this build."
        else
          "$totalWarningsCount ${StringUtil.pluralize("warning", totalWarningsCount)} " +
          "of the following ${StringUtil.pluralize("type", children.size)} were detected for this build:"
        setAllowAutoWrapping(true)
        setCopyable(true)
        isFocusable = false
      })
      children.forEach {
        if (it is AbstractBuildAttributionNode) {
          val name = it.nodeName
          val link = HyperlinkLabel("${name} (${it.issuesCountsSuffix})")
          link.addHyperlinkListener { _ -> nodeSelector.selectNode(it) }
          listPanel.add(link)
        }
      }
      return listPanel
    }
  }

  fun findIssueRoot(type: TaskIssueType): TaskIssuesRoot? =
    children.asSequence().filterIsInstance<TaskIssuesRoot>().firstOrNull { it.issuesGroup.type == type }
}