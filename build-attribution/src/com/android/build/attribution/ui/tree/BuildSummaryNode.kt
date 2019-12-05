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

import com.android.build.attribution.ui.data.BuildSummary
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.panels.AbstractBuildAttributionInfoPanel
import com.android.build.attribution.ui.panels.headerLabel
import com.android.utils.HtmlBuilder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.util.text.DateFormatUtil
import javax.swing.Icon
import javax.swing.JComponent

class BuildSummaryNode(
  val buildSummary: BuildSummary,
  parent: ControllersAwareBuildAttributionNode
) : AbstractBuildAttributionNode(parent, "Build:") {

  override val presentationIcon: Icon? = null
  override val issuesCountsSuffix = "finished at ${buildFinishedTime()}"
  override val timeSuffix = buildSummary.totalBuildDuration.durationString()
  override fun buildChildren() = emptyArray<SimpleNode>()
  override fun createComponent(): AbstractBuildAttributionInfoPanel = object : AbstractBuildAttributionInfoPanel() {

    override fun createHeader(): JComponent {
      return headerLabel("Build finished at ${buildFinishedTime()}")
    }

    override fun createBody(): JComponent {
      return JBPanel<JBPanel<*>>(VerticalLayout(6)).apply {
        add(JBLabel().apply {
          setCopyable(true)
          setAllowAutoWrapping(true)
          text = HtmlBuilder()
            .openHtmlBody()
            .add("Total build duration was ${buildSummary.totalBuildDuration.durationString()}, it includes:")
            .newline()
            .add("Build configuration: ${buildSummary.configurationDuration.durationString()}")
            .newline()
            .add("Critical path tasks execution: ${buildSummary.criticalPathDuration.durationString()}")
            .closeHtmlBody().html
        })
      }
    }
  }

  private fun buildFinishedTime(): String = DateFormatUtil.formatDateTime(buildSummary.buildFinishedTimestamp)
}