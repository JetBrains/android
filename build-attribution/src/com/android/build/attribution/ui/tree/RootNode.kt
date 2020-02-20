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
package com.android.build.attribution.ui.tree

import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.controllers.TaskIssueReporter
import com.android.build.attribution.ui.controllers.TreeNodeSelector
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.panels.TreeLinkListener
import com.intellij.ui.treeStructure.SimpleNode

class RootNode(
  private val reportData: BuildAttributionReportUiData,
  override val analytics: BuildAttributionUiAnalytics,
  override val issueReporter: TaskIssueReporter,
  override val nodeSelector: TreeNodeSelector
) : ControllersAwareBuildAttributionNode(null) {
  val taskIssueLinkListener = object : TreeLinkListener<TaskIssueUiData> {
    override fun clickedOn(target: TaskIssueUiData) {
      children.asSequence()
        .filterIsInstance<WarningsRootNode>()
        .first()
        .findIssueRoot(target.type)?.findNodeForIssue(target)?.let { nodeSelector.selectNode(it) }
    }
  }

  override fun buildChildren(): Array<SimpleNode> {
    val nodes = mutableListOf<SimpleNode>()
    nodes.add(BuildSummaryNode(reportData.buildSummary, this))
    nodes.add(CriticalPathPluginsRoot(reportData.criticalPathPlugins, this))
    nodes.add(CriticalPathTasksRoot(reportData.criticalPathTasks, this,
                                    taskIssueLinkListener))
    // TODO(b/148275039): Re-enable plugin configuration
    // nodes.add(PluginConfigurationTimeRoot(reportData.configurationTime, this))
    nodes.add(WarningsRootNode(reportData, this))
    return nodes.toTypedArray()
  }
}