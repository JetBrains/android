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

import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.build.attribution.ui.HtmlLinksHandler
import com.android.build.attribution.ui.htmlTextLabelWithFixedLines
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.build.diagnostic.WindowsDefenderCheckService
import com.intellij.diagnostic.DiagnosticBundle
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.application.invokeLater
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class WindowsDefenderWarningPage(
  data: WindowsDefenderCheckService.WindowsDefenderWarningData,
  actionHandlers: ViewActionHandlers
): JPanel() {

  private val linksHandler = HtmlLinksHandler(actionHandlers)
  private val pageHandler = actionHandlers.windowsDefenderPageHandler()
  private val learnMoreLink = linksHandler.externalLink("Learn more", BuildAnalyzerBrowserLinks.WINDOWS_DEFENDER)
  val contentHtml = """
          <b>Anti-virus</b><br/>
          <br/>
          The IDE has detected Microsoft Defender with Real-Time Protection enabled.<br/>
          Antivirus software may be impacting your build performance by doing<br/>
          real-time scanning of directories used by Gradle. $learnMoreLink.<br/>
          <br/>
          It is recommended to make sure the following paths are added to the Defender folder exclusion list:<br/>
          <br/>
          ${data.interestingPaths.joinToString(separator = "<br/>\n")}
        """.trimIndent()

  val autoExcludeStatus = JLabel().apply {
    isVisible = false
    horizontalAlignment = SwingConstants.LEFT
  }

  val autoExcludeLink: ActionLink
  val autoExclusionLine = JPanel(HorizontalLayout(0)).apply {
    val (preLink, link, postLink) =
      Triple("You can ", "automatically check and exclude missing paths", " (note: Windows will ask for administrative privileges).")
    autoExcludeLink = ActionLink(link) {
      autoExcludeStatus.isVisible = true
      autoExcludeStatus.icon = AnimatedIcon.Default()
      autoExcludeStatus.text = "Running..."
      pageHandler.runAutoExclusionScript { success -> invokeLater {
        if (success) {
          autoExcludeStatus.icon = StudioIcons.Common.SUCCESS_INLINE
          autoExcludeStatus.text = DiagnosticBundle.message("defender.config.success")
        }
        else {
          autoExcludeStatus.icon = StudioIcons.Common.WARNING_INLINE
          autoExcludeStatus.text = DiagnosticBundle.message("defender.config.failed")
        }
      }}
    }
    add(JLabel(preLink), HorizontalLayout.LEFT)
    add(autoExcludeLink, HorizontalLayout.LEFT)
    add(JLabel(postLink), HorizontalLayout.LEFT)
  }

  val warningSuppressedMessage = JLabel(
    DiagnosticBundle.message("defender.config.restore",ActionsBundle.message("action.ResetWindowsDefenderNotification.text"))
  )
  val suppressWarningLink: ActionLink
  val suppressLine = JPanel(HorizontalLayout(0)).apply {
    val (preLink, link, postLink) =
      Triple("Once configured manually, you can ", "ignore this warning for this project", " to no longer see it.")
    suppressWarningLink = ActionLink(link) {
      pageHandler.ignoreCheckForProject()
      warningSuppressedMessage.isVisible = true
    }
    add(JLabel(preLink), HorizontalLayout.LEFT)
    add(suppressWarningLink, HorizontalLayout.LEFT)
    add(JLabel(postLink), HorizontalLayout.LEFT)
  }

  private val manualInstructionsLine = JPanel(HorizontalLayout(0)).apply {
    val (preLink, link) = Pair("You can configure active scanning manually following ", "these instructions")
    val manualInstructionsLink = BrowserLink(link, WindowsDefenderCheckService.manualInstructionsLink).apply {
      addActionListener { pageHandler.trackShowingManualInstructions() }
    }
    add(JLabel(preLink), HorizontalLayout.LEFT)
    add(manualInstructionsLink, HorizontalLayout.LEFT)
  }

  init {
    warningSuppressedMessage.isVisible = false

    layout = VerticalLayout(0, SwingConstants.LEFT)
    add(htmlTextLabelWithFixedLines(contentHtml, linksHandler).apply { border = JBUI.Borders.emptyLeft(2) })
    add(JLabel(" ")) // Add an empty line spacing before controls.
    add(autoExclusionLine)
    add(autoExcludeStatus)
    add(manualInstructionsLine)
    add(suppressLine)
    add(warningSuppressedMessage)
  }
}