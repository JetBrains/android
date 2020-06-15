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

import com.android.build.attribution.ui.controllers.TreeNodeSelector
import com.android.build.attribution.ui.data.ConfigurationUiData
import com.android.build.attribution.ui.data.PluginConfigurationUiData
import com.android.build.attribution.ui.data.ProjectConfigurationUiData
import com.android.build.attribution.ui.durationString
import com.android.build.attribution.ui.emptyIcon
import com.android.build.attribution.ui.issuesCountString
import com.android.build.attribution.ui.panels.AbstractBuildAttributionInfoPanel
import com.android.build.attribution.ui.panels.headerLabel
import com.android.build.attribution.ui.percentageString
import com.android.build.attribution.ui.warningIcon
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent

class PluginConfigurationTimeRoot(
  private val configurationData: ConfigurationUiData,
  parent: ControllersAwareBuildAttributionNode
) : AbstractBuildAttributionNode(parent, "Plugin Configuration Time") {

  override val presentationIcon: Icon? = null

  override val issuesCountsSuffix: String? = issuesCountString(configurationData.totalIssueCount, 0)

  override val timeSuffix: String? = configurationData.totalConfigurationTime.durationString()

  override val pageType = BuildAttributionUiEvent.Page.PageType.CONFIGURATION_TIME_ROOT

  override fun createComponent() = object : AbstractBuildAttributionInfoPanel() {
    override fun createHeader(): JComponent {
      return headerLabel("Plugin Configuration Time")
    }

    override fun createBody(): JComponent = JBPanel<JBPanel<*>>(VerticalLayout(6)).apply {
      add(JBLabel("Plugin configuration times by project:"))
      add(createProjectsTable(children, nodeSelector))
    }
  }

  override fun buildChildren(): Array<SimpleNode> {
    return configurationData.projects
      .map { projectData -> ProjectNode(projectData, this) }
      .toTypedArray()
  }

}

private class ProjectNode(
  val projectData: ProjectConfigurationUiData,
  parent: PluginConfigurationTimeRoot
) : AbstractBuildAttributionNode(parent, projectData.project) {

  override val presentationIcon: Icon? = StudioIcons.Shell.Filetree.ANDROID_MODULE
  override val issuesCountsSuffix: String? = issuesCountString(projectData.issueCount, 0)
  override val timeSuffix: String? = projectData.configurationTime.durationString()
  override val pageType = BuildAttributionUiEvent.Page.PageType.CONFIGURATION_TIME_PROJECT
  override fun createComponent() = object : AbstractBuildAttributionInfoPanel() {
    override fun createHeader(): JComponent = headerLabel(projectData.project + " Configuration Time")

    override fun createBody(): JComponent = JBPanel<JBPanel<*>>(VerticalLayout(6)).apply {
      add(JBLabel(
        """
This project took ${projectData.configurationTime.durationString()} (${projectData.configurationTime.percentageString()} 
of total plugin configuration time) to configure the following plugins:
"""
      ))
      add(createPluginsTable(children, nodeSelector))
    }
  }

  override fun buildChildren(): Array<SimpleNode> {
    val childrenHasIcon = projectData.plugins.stream().anyMatch { plugin -> plugin.slowsConfiguration }
    return projectData.plugins
      .map { data -> PluginConfigurationNode(data, childrenHasIcon, this) }
      .toTypedArray()
  }
}

private class PluginConfigurationNode(
  val pluginData: PluginConfigurationUiData,
  needEmptyIconShift: Boolean,
  parent: AbstractBuildAttributionNode
) : AbstractBuildAttributionNode(parent, pluginData.pluginName) {

  override val presentationIcon: Icon? = when {
    pluginData.slowsConfiguration -> warningIcon()
    needEmptyIconShift -> emptyIcon()
    else -> null
  }

  override val issuesCountsSuffix: String? = issuesCountString(pluginData.nestedIssueCount, 0)

  override val timeSuffix: String? = pluginData.configurationTime.durationString()

  override val pageType = BuildAttributionUiEvent.Page.PageType.CONFIGURATION_TIME_PLUGIN

  override fun createComponent() = object : AbstractBuildAttributionInfoPanel() {
    override fun createHeader(): JComponent {
      return headerLabel(pluginData.pluginName)
    }

    override fun createBody(): JComponent = JBPanel<JBPanel<*>>(VerticalLayout(6)).apply {
      add(JBLabel(
        """
        During build configuration, this plugin required ${pluginData.configurationTime.durationString()},
        or ${pluginData.configurationTime.percentageString()} of the total build configuration phase.
        """.trimIndent()
      ))

      add(HyperlinkLabel("Learn more").apply {
        addHyperlinkListener { analytics.helpLinkClicked() }
        setHyperlinkTarget("https://d.android.com/r/tools/build-attribution/optimize-configuration-phase")
      })

      if (children.isNotEmpty()) {
        add(JBLabel("This plugin also triggers configuration of:"))
        add(createPluginsTable(children, nodeSelector))
      }
    }
  }

  override fun buildChildren(): Array<SimpleNode> {
    val childrenHaveIcon = pluginData.nestedPlugins.stream().anyMatch { plugin -> plugin.slowsConfiguration }
    return pluginData.nestedPlugins
      .map { data -> PluginConfigurationNode(data, childrenHaveIcon, this) }
      .toTypedArray()
  }
}

//todo looks ugly, but the plan is to re-use stack charts code from critical path here when have time
private fun createProjectsTable(projects: Array<SimpleNode>, nodeController: TreeNodeSelector): JComponent {
  val tablePanel = JBPanel<JBPanel<*>>(GridBagLayout())
  val c = GridBagConstraints()
  c.gridy = 0
  for (node in projects) {
    val projectNode = node as ProjectNode

    c.gridx = 0
    c.weightx = 0.0
    c.anchor = GridBagConstraints.LINE_START
    c.insets = JBUI.emptyInsets()
    tablePanel.add(JBLabel(projectNode.projectData.configurationTime.durationString()), c)
    c.gridx = 1
    c.insets = JBUI.insets(0, 9, 0, 0)
    c.anchor = GridBagConstraints.LINE_END
    tablePanel.add(JBLabel(projectNode.projectData.configurationTime.percentageString()), c)
    c.gridx = 2
    c.anchor = GridBagConstraints.LINE_START
    c.weightx = 1.0
    val link = HyperlinkLabel(projectNode.projectData.project)
    link.addHyperlinkListener(object : HyperlinkAdapter() {
      override fun hyperlinkActivated(e: HyperlinkEvent) {
        nodeController.selectNode(node)
      }
    })
    tablePanel.add(link, c)

    c.gridy++
  }
  return tablePanel
}

private fun createPluginsTable(plugins: Array<SimpleNode>, nodeController: TreeNodeSelector): JComponent {
  val tablePanel = JBPanel<JBPanel<*>>(GridBagLayout())
  val c = GridBagConstraints()
  c.gridy = 0
  for (node in plugins) {
    val pluginNode = node as PluginConfigurationNode

    c.gridx = 0
    c.weightx = 0.0
    c.anchor = GridBagConstraints.LINE_START
    c.insets = JBUI.emptyInsets()
    tablePanel.add(JBLabel(pluginNode.pluginData.configurationTime.durationString()), c)
    c.gridx = 1
    c.insets = JBUI.insets(0, 9, 0, 0)
    c.anchor = GridBagConstraints.LINE_END
    tablePanel.add(JBLabel(pluginNode.pluginData.configurationTime.percentageString()), c)
    c.gridx = 2
    c.anchor = GridBagConstraints.LINE_START
    c.weightx = 1.0
    val link = HyperlinkLabel(pluginNode.pluginData.pluginName)
    link.addHyperlinkListener(object : HyperlinkAdapter() {
      override fun hyperlinkActivated(e: HyperlinkEvent) {
        nodeController.selectNode(node)
      }
    })
    tablePanel.add(link, c)

    c.gridy++
  }
  return tablePanel
}
