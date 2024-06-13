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
package com.android.tools.idea.gradle.project

import com.android.sdklib.AndroidVersion
import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.android.tools.idea.serverflags.protos.StudioVersionRecommendation
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.UpgradeAndroidStudioDialogStats
import com.intellij.CommonBundle
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import org.jetbrains.android.util.AndroidBundle
import java.awt.Desktop
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.event.HyperlinkEvent

class AndroidSdkCompatibilityDialog(
  val project: Project,
  val recommendedVersion: StudioVersionRecommendation,
  val potentialFallbackVersion: StudioVersionRecommendation?,
  val modulesViolatingSupportRules: List<Pair<String, AndroidVersion>>
) : DialogWrapper(project, true, IdeModalityType.MODELESS) {

  init {
    title = if(recommendedVersion.versionReleased.not() && potentialFallbackVersion == null) {
      AndroidBundle.message("project.upgrade.studio.notification.no.recommendation.title")
    } else {
      AndroidBundle.message("project.upgrade.studio.notification.title")
    }
    isResizable = false
    setCancelButtonText(CommonBundle.getCloseButtonText())
    init()
  }

  override fun createCenterPanel(): JComponent {
    val dialogContent = if (recommendedVersion.versionReleased.not()) {
      if (potentialFallbackVersion != null) {
        AndroidBundle.message(
          "project.upgrade.studio.notification.body.different.channel.recommendation",
          ApplicationInfo.getInstance().fullVersion,
          potentialFallbackVersion.buildDisplayName
        )
      } else {
        AndroidBundle.message(
          "project.upgrade.studio.notification.body.no.recommendation",
          ApplicationInfo.getInstance().fullVersion,
        )
      }
    } else {
      AndroidBundle.message(
        "project.upgrade.studio.notification.body.same.channel.recommendation",
        ApplicationInfo.getInstance().fullVersion,
        recommendedVersion.buildDisplayName
      )
    }

    val documentationLinkLine = JPanel(HorizontalLayout(0)).apply {
      val (preLink, link) = Pair("For more details, please refer to the ", "Android Studio documentation")
      val browserLink = BrowserLink(link, ANDROID_STUDIO_DOC_LINK)
      add(JLabel(preLink), HorizontalLayout.LEFT)
      add(browserLink, HorizontalLayout.LEFT)
    }

    val panel: JPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(htmlTextLabelWithFixedLines(dialogContent, "main-content"))
      add(JLabel(" ")) // Add an empty line spacing
      add(documentationLinkLine)
      add(JLabel(" ")) // Add an empty line spacing
      add(htmlTextLabelWithFixedLines(getAffectedModules(modulesViolatingSupportRules), "affected-modules"))
    }

    return JBUI.Panels.simplePanel(10, 10).apply {
      addToTop(panel)
      preferredSize = JBDimension(550, 325)
    }
  }

  fun htmlTextLabelWithFixedLines(htmlBodyContent: String, panelName: String): JEditorPane =
    SwingHelper.createHtmlViewer(false, null, JBColor.background(), null).apply {
      name = panelName
      border = JBUI.Borders.empty()
      isEditable = false
      SwingHelper.setHtml(this, htmlBodyContent, null)
      caretPosition = 0
      addHyperlinkListener { e ->
        if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(e.url.toURI());
          }
        }
      }
    }

  override fun createActions(): Array<Action> {
    return arrayOf(cancelAction, DontAskAgainAction())
  }

  override fun doCancelAction() {
    logEvent(project, UpgradeAndroidStudioDialogStats.UserAction.CANCEL)
    super.doCancelAction()
  }

  private fun logEvent(project: Project, action: UpgradeAndroidStudioDialogStats.UserAction) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .withProjectId(project)
        .setKind(AndroidStudioEvent.EventKind.UPGRADE_ANDROID_STUDIO_DIALOG)
        .setUpgradeAndroidStudioDialog(
          UpgradeAndroidStudioDialogStats.newBuilder().apply {
            userAction = action
          }
        )
    )
  }

  private fun getAffectedModules(modules: List<Pair<String, AndroidVersion>>): String {
    val modulesToShow = modules.take(AndroidSdkCompatibilityChecker.MAX_NUM_OF_MODULES)
    val remainingModules = modules.drop(AndroidSdkCompatibilityChecker.MAX_NUM_OF_MODULES)

    val content = StringBuilder()
    content.append(
      "Affected modules: " + modulesToShow.joinToString {
        "<br/>'${it.first}' (compileSdk=${it.second.apiStringWithoutExtension})"
      }
    )

    if (remainingModules.isNotEmpty()) {
      content.append(" (and ${remainingModules.size} more)")
    }
    return content.toString()
  }

  private inner class DontAskAgainAction: AbstractAction(DO_NOT_ASK_FOR_PROJECT_BUTTON_TEXT) {
    override fun actionPerformed(e: ActionEvent?) {
      logEvent(project, UpgradeAndroidStudioDialogStats.UserAction.DO_NOT_ASK_AGAIN)
      AndroidSdkCompatibilityChecker.StudioUpgradeReminder(project).apply {
        doNotAskAgainProjectLevel = true
        doNotAskAgainIdeLevel = true
      }
      close(OK_EXIT_CODE)
    }
  }

  companion object {
    const val DO_NOT_ASK_FOR_PROJECT_BUTTON_TEXT = "Don't ask for this project"
    const val ANDROID_STUDIO_DOC_LINK = "https://developer.android.com/studio/releases#api-level-support"
  }
}