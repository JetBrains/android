/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.build.attribution.analyzers.AGPUpdateRequired
import com.android.build.attribution.analyzers.ConfigurationCacheCompatibilityTestFlow
import com.android.build.attribution.analyzers.ConfigurationCachingCompatibilityProjectResult
import com.android.build.attribution.analyzers.ConfigurationCachingTurnedOff
import com.android.build.attribution.analyzers.ConfigurationCachingTurnedOn
import com.android.build.attribution.analyzers.IncompatiblePluginWarning
import com.android.build.attribution.analyzers.IncompatiblePluginsDetected
import com.android.build.attribution.analyzers.NoDataFromSavedResult
import com.android.build.attribution.analyzers.NoIncompatiblePlugins
import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.build.attribution.ui.HtmlLinksHandler
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.build.attribution.ui.durationStringHtml
import com.android.build.attribution.ui.htmlTextLabelWithFixedLines
import com.android.build.attribution.ui.insertBRTags
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.SwingConstants

class ConfigurationCacheRootWarningDetailsView(
  private val uiData: ConfigurationCachingCompatibilityProjectResult,
  private val projectConfigurationTime: TimeWithPercentage,
  private val actionHandlers: ViewActionHandlers
) {
  val pagePanel: JPanel = JPanel().apply {
    layout = VerticalLayout(10, SwingConstants.LEFT)
    when (uiData) {
      is AGPUpdateRequired -> this.createAGPUpdateRequiredPanel(uiData, projectConfigurationTime)
      is NoIncompatiblePlugins -> this.createNoIncompatiblePluginsPanel(uiData, projectConfigurationTime)
      is IncompatiblePluginsDetected -> this.createIncompatiblePluginsDetectedPanel(uiData, projectConfigurationTime)
      is ConfigurationCacheCompatibilityTestFlow -> this.createConfigurationCacheTestFlowPanel(uiData)
      ConfigurationCachingTurnedOn -> Unit
      ConfigurationCachingTurnedOff -> Unit
      NoDataFromSavedResult -> Unit
    }
  }

  private fun JPanel.createAGPUpdateRequiredPanel(uiData: AGPUpdateRequired, projectConfigurationTime: TimeWithPercentage) {
    val appliedAGPPluginsList = uiData.appliedPlugins.joinToString(
      prefix = "Android Gradle plugins applied in this build:<ul>",
      postfix = "</ul>",
      separator = ""
    ) { "<li>${it.displayName}</li>" }
    val linksHandler = HtmlLinksHandler(actionHandlers)
    val contentHtml = """
        <b>Android Gradle plugin update required to make Configuration cache available</b>
        ${configurationCachingDescriptionHeader(projectConfigurationTime, linksHandler)}
        Android Gradle plugin supports Configuration cache from ${uiData.recommendedVersion}. Current version is ${uiData.currentVersion}.
        
        $appliedAGPPluginsList
      """.trimIndent().insertBRTags()
    add(htmlTextLabelWithFixedLines(contentHtml, linksHandler).alignWithButton())
    add(JButton("Update Android Gradle plugin").apply { addActionListener { actionHandlers.runAgpUpgrade() } })
  }

  private fun JPanel.createIncompatiblePluginsDetectedPanel(uiData: IncompatiblePluginsDetected, configurationTime: TimeWithPercentage) {

    val incompatiblePluginsCountLine = uiData.incompatiblePluginWarnings.size.let {
      when (it) {
        0 -> null
        1 -> "1 plugin is not known to have a compatible version yet, please contact plugin providers for details."
        else -> "$it plugins are not known to have a compatible version yet, please contact plugin providers for details."
      }
    }

    val upgradablePluginsCountLine = uiData.upgradePluginWarnings.size.let {
      when (it) {
        0 -> null
        1 -> "1 plugin can be updated to the compatible version."
        else -> "$it plugins can be updated to the compatible version."
      }
    }
    val pluginsCountLines = sequenceOf(upgradablePluginsCountLine, incompatiblePluginsCountLine).filterNotNull()
      .joinToString(prefix = "<ul>", postfix = "</ul>", separator = "") { "<li>$it</li>" }
    val linksHandler = HtmlLinksHandler(actionHandlers)
    val contentHtml = """
        <b>Some plugins are not compatible with Configuration cache</b>
        ${configurationCachingDescriptionHeader(configurationTime, linksHandler)}
        Some of the plugins applied are known to be not compatible with Configuration cache in versions used in this build.
        $pluginsCountLines
        You can find details on each plugin on corresponding sub-pages.
      """.trimIndent().insertBRTags()
    add(htmlTextLabelWithFixedLines(contentHtml, linksHandler))
  }

  private fun JPanel.createNoIncompatiblePluginsPanel(uiData: NoIncompatiblePlugins, configurationTime: TimeWithPercentage) {
    val linksHandler = HtmlLinksHandler(actionHandlers)
    val contentHtml = """
        <b>Try to turn Configuration cache on</b>
        ${configurationCachingDescriptionHeader(configurationTime, linksHandler)}
        The known plugins applied in this build are compatible with Configuration cache.
      """.trimIndent().insertBRTags()
    val runTestBuildActionButton = JButton("Try Configuration cache in a build").apply {
      addActionListener { actionHandlers.runTestConfigurationCachingBuild() }
    }
    val unknownPluginsNoteHtml = if (uiData.configurationCacheIsStableFeature) """
      Note: There could be unknown plugins that aren't compatible and are discovered after
      you build with Configuration cache turned on.
      """.trimIndent().insertBRTags()
    else """
      Note: <b>Configuration cache is currently an experimental Gradle feature.</b> There could be unknown plugins that aren't compatible and are discovered after
      you build with Configuration cache turned on.
      """.trimIndent().insertBRTags()
    val unknownPluginsListHtml = uiData.unrecognizedPlugins.joinToString(
      prefix = "<b>List of applied plugins we were not able to recognise:</b><ul>",
      postfix = "</ul>",
      separator = ""
    ) { "<li>${it.displayName}</li>" }
    add(htmlTextLabelWithFixedLines(contentHtml, linksHandler).alignWithButton())
    add(htmlTextLabelWithFixedLines(unknownPluginsNoteHtml).alignWithButton())
    add(runTestBuildActionButton)
    if (uiData.unrecognizedPlugins.isNotEmpty())
      add(htmlTextLabelWithFixedLines(unknownPluginsListHtml).alignWithButton())
  }

  private fun JPanel.createConfigurationCacheTestFlowPanel(data: ConfigurationCacheCompatibilityTestFlow) {
    val linksHandler = HtmlLinksHandler(actionHandlers)
    val configurationCacheLink = linksHandler.externalLink("Configuration cache", BuildAnalyzerBrowserLinks.CONFIGURATION_CACHING)
    val contentHtml = """
      <b>Test builds with Configuration cache finished successfully</b>
      With $configurationCacheLink, Gradle can skip the configuration phase entirely when nothing that affects the build configuration has changed.
      
      Gradle successfully serialized the task graph and reused it with Configuration cache on.
      """.trimIndent().insertBRTags()
    val addToPropertiesActionButton = JButton("Turn on Configuration cache in gradle.properties").apply {
      addActionListener { actionHandlers.turnConfigurationCachingOnInProperties(data.configurationCacheIsStableFeature) }
    }
    add(htmlTextLabelWithFixedLines(contentHtml, linksHandler).alignWithButton())
    add(addToPropertiesActionButton)
  }
}

class ConfigurationCachePluginWarningDetailsView(
  private val data: IncompatiblePluginWarning,
  private val projectConfigurationTime: TimeWithPercentage,
  private val actionHandlers: ViewActionHandlers
) {
  val pagePanel: JPanel = JPanel().apply {
    layout = VerticalLayout(10, SwingConstants.LEFT)
    val linksHandler = HtmlLinksHandler(actionHandlers)

    val contentHtml = if (data.requiredVersion != null) """
        <b>${data.plugin.displayName}: update required</b>
        ${configurationCachingDescriptionHeader(projectConfigurationTime, linksHandler)}
        Update this plugin to ${data.requiredVersion} or higher to make Configuration cache available.
        
        Plugin version: ${data.currentVersion}
        Plugin dependency: ${data.pluginInfo.pluginArtifact}
      """.trimIndent().insertBRTags()
    else """
        <b>${data.plugin.displayName}: not compatible</b>
        ${configurationCachingDescriptionHeader(projectConfigurationTime, linksHandler)}
        The version of this plugin used in this build is not compatible with Configuration cache
        and we don’t know the version when it becomes compatible.
        
        Plugin version: ${data.currentVersion}
        Plugin dependency: ${data.pluginInfo.pluginArtifact}
      """.trimIndent().insertBRTags()
    add(htmlTextLabelWithFixedLines(contentHtml, linksHandler).alignWithButton())
    if (data.requiredVersion != null) {
      add(JButton("Go to plugin version declaration").apply { addActionListener { actionHandlers.updatePluginClicked(data) } })
    }
  }
}

private fun configurationCachingDescriptionHeader(configurationTime: TimeWithPercentage, linksHandler: HtmlLinksHandler): String {
  val configurationCacheLink = linksHandler.externalLink("Configuration cache", BuildAnalyzerBrowserLinks.CONFIGURATION_CACHING)
  return "<p>" +
         "You could save about ${configurationTime.durationStringHtml()} by turning $configurationCacheLink on.<br/>" +
         "With Configuration cache, Gradle can skip the configuration phase entirely when nothing that affects the build configuration has changed." +
         "</p>"
}

private fun JEditorPane.alignWithButton() = apply {
  // Add left margin to align with button vertically
  border = JBUI.Borders.emptyLeft(3)
}
