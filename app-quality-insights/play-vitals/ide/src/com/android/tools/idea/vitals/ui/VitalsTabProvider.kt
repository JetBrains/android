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
package com.android.tools.idea.vitals.ui

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppInsightsModel
import com.android.tools.idea.insights.ui.AppInsightsTabPanel
import com.android.tools.idea.insights.ui.AppInsightsTabProvider
import com.google.gct.login.GoogleLogin
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText
import icons.StudioIcons
import java.awt.Graphics
import java.time.Clock
import javax.swing.JPanel
import kotlinx.coroutines.launch

class VitalsTabProvider : AppInsightsTabProvider {
  override val tabDisplayName = "Play Vitals"

  // TODO(b/271918057): use real icon.
  override val tabIcon = StudioIcons.Avd.DEVICE_PLAY_STORE

  override fun populateTab(project: Project, tabPanel: AppInsightsTabPanel) {
    tabPanel.setComponent(placeholderContent())
    val configManager = project.service<VitalsConfigurationManager>()
    AndroidCoroutineScope(tabPanel, AndroidDispatchers.uiThread).launch {
      configManager.configuration.collect { appInsightsModel ->
        when (appInsightsModel) {
          AppInsightsModel.Unauthenticated -> {
            tabPanel.setComponent(loggedOutErrorStateComponent())
          }
          is AppInsightsModel.Authenticated -> {
            tabPanel.setComponent(
              VitalsTab(appInsightsModel.controller, project, Clock.systemDefaultZone())
            )
          }
        }
      }
    }
  }

  override fun isApplicable() = StudioFlags.PLAY_VITALS_ENABLED.get()

  // TODO(b/274775776): implement 0 state screen
  private fun placeholderContent(): JPanel =
    object : JPanel() {
      private val text =
        object : StatusText() {
            override fun isStatusVisible() = true
          }
          .also {
            it.appendLine("Initializing Play Vitals", SimpleTextAttributes.GRAYED_ATTRIBUTES, null)
            it.attachTo(this)
          }

      override fun paint(g: Graphics?) {
        super.paint(g)
        text.paint(this, g)
      }
    }

  // TODO(b/274775776): revisit this 0 state screen
  @Suppress("DialogTitleCapitalization")
  private fun loggedOutErrorStateComponent(): JPanel {
    val loggedOutText =
      object : StatusText() {
          override fun isStatusVisible() = true
        }
        .apply {
          // TODO(b/271918057): use real icon.
          appendLine(
            StudioIcons.Avd.DEVICE_PLAY_STORE,
            "",
            SimpleTextAttributes.REGULAR_ATTRIBUTES,
            null
          )
          appendLine(
            "See real-world app quality insights here",
            SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES,
            null
          )
          appendLine("Log in", SimpleTextAttributes.LINK_ATTRIBUTES) {
            GoogleLogin.instance.logIn()
          }
          appendText(" to Android Studio to connect to your Play Store account.")
          appendLine("")
          appendLine(
            AllIcons.General.ContextHelp,
            "More Info",
            SimpleTextAttributes.LINK_ATTRIBUTES
          ) {
            BrowserUtil.browse(
              " https://d.android.com/r/studio-ui/app-quality-insights/crashlytics/help "
            )
          }
        }

    return object : JPanel() {
      init {
        loggedOutText.attachTo(this)
      }

      override fun paint(g: Graphics?) {
        super.paint(g)
        loggedOutText.paint(this, g)
      }
    }
  }
}
