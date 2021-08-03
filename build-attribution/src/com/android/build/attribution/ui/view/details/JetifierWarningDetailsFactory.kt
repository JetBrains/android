/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.attribution.analyzers.JetifierCanBeRemoved
import com.android.build.attribution.analyzers.JetifierNotUsed
import com.android.build.attribution.analyzers.JetifierRequiredForLibraries
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.analyzers.JetifierUsedCheckRequired
import com.android.build.attribution.ui.BuildAnalyzerBrowserLinks
import com.android.build.attribution.ui.HtmlLinksHandler
import com.android.build.attribution.ui.htmlTextLabelWithFixedLines
import com.android.build.attribution.ui.insertBRTags
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.intellij.ui.components.panels.VerticalLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingConstants

class JetifierWarningDetailsFactory(
  private val actionHandlers: ViewActionHandlers
) {

  fun createPage(data: JetifierUsageAnalyzerResult): JPanel = when (data) {
    JetifierUsedCheckRequired -> createCheckRequiredPage()
    JetifierCanBeRemoved -> createJetifierNotRequiredPage()
    is JetifierRequiredForLibraries -> createJetifierRequiredForLibrariesPage(data)
    JetifierNotUsed -> JPanel()
  }

  private fun createCheckRequiredPage() = JPanel().apply {
    layout = VerticalLayout(10, SwingConstants.LEFT)
    val linksHandler = HtmlLinksHandler(actionHandlers)
    val learnMoreLink = linksHandler.externalLink("Learn more", BuildAnalyzerBrowserLinks.JETIIFER_MIGRATE)
    val contentHtml = """
          <b>Confirm need for Jetifier flag in your project</b>
          Your project’s gradle.settings file includes ‘enableJetifier’. This flag is needed
          to enable AndroidX for libraries that don’t support it natively. $learnMoreLink.
  
          Your project may no longer need this flag and could save build time by removing it.
          Click the button below to verify if enableJetifier is needed.
        """.trimIndent().insertBRTags()
    add(htmlTextLabelWithFixedLines(contentHtml, linksHandler))
    add(JButton("Check Jetifier").apply { addActionListener { actionHandlers.runCheckJetifierTask() } })
  }

  private fun createJetifierNotRequiredPage() = JPanel().apply {
    layout = VerticalLayout(10, SwingConstants.LEFT)
    val linksHandler = HtmlLinksHandler(actionHandlers)
    val removeJetifierLink = linksHandler.actionLink("Remove enableJetifier", "remove") {
      actionHandlers.turnJetifierOffInProperties()
    }
    val contentHtml = """
      <b>Remove Jetifier flag</b>
      Your project’s gradle.settings includes enableJetifier. This flag is not needed by your project
      and removing it will improve build performance.
      
      $removeJetifierLink
      """.trimIndent().insertBRTags()
    add(htmlTextLabelWithFixedLines(contentHtml, linksHandler))
  }

  private fun createJetifierRequiredForLibrariesPage(data: JetifierRequiredForLibraries) = JPanel().apply {
    layout = VerticalLayout(10, SwingConstants.LEFT)
    val contentHtml = """
      <b>Jetifier flag is needed by some libraries in your project</b>
      The following libraries rely on the ‘enableJetifier’ flag to work with AndroidX.
      Please consider upgrading to more recent versions of those libraries or contact
      the library authors to request native AndroidX support, if it’s not available yet.
      """.trimIndent().insertBRTags()
    // TODO (b/194299417): replace with a tree component and present in a reversed way
    val librariesListHtml = data.checkJetifierResult.dependenciesDependingOnSupportLibs.asSequence()
      .joinToString(separator = "", prefix = "<ul>", postfix = "</ul>") {
        val reversedPath = it.value.dependencyPath.elements.reversed().run { if (size > 1) drop(1) else this }
        "<li>${reversedPath.plus(it.value.projectPath).joinToString(" -> ")}</li>"
      }
    add(htmlTextLabelWithFixedLines(contentHtml))
    add(JButton("Refresh").apply { addActionListener { actionHandlers.runCheckJetifierTask() } })
    add(htmlTextLabelWithFixedLines(librariesListHtml))
  }
}