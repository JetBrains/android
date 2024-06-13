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
import com.android.tools.idea.insights.VITALS_KEY
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.analytics.AppInsightsTrackerImpl
import com.android.tools.idea.insights.ui.AppInsightsTabPanel
import com.android.tools.idea.insights.ui.AppInsightsTabProvider
import com.android.tools.idea.vitals.VitalsLoginFeature
import com.google.gct.login2.GoogleLoginService
import com.google.gct.login2.LoginFeature
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText
import icons.StudioIcons
import icons.StudioIllustrations
import java.awt.Graphics
import java.time.Clock
import javax.swing.JPanel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VitalsTabProvider : AppInsightsTabProvider {
  override val displayName = VITALS_KEY.displayName

  override val icon = StudioIcons.Avd.DEVICE_PLAY_STORE

  override fun populateTab(project: Project, tabPanel: AppInsightsTabPanel) {
    tabPanel.setComponent(placeholderContent())
    AndroidCoroutineScope(tabPanel, AndroidDispatchers.diskIoThread).launch {
      val configManager = project.service<VitalsConfigurationService>().manager
      val tracker = AppInsightsTrackerImpl(project, AppInsightsTracker.ProductType.PLAY_VITALS)
      withContext(AndroidDispatchers.uiThread) {
        // Combine with active user flow to get the logged out -> logged in + not authorized update
        val loginService = service<GoogleLoginService>()
        val flow =
          if (loginService.useOldVersion) configManager.configuration
          else
            configManager.configuration.combine(loginService.activeUserFlow) { config, _ -> config }
        flow.collect { appInsightsModel ->
          when (appInsightsModel) {
            AppInsightsModel.Unauthenticated -> {
              tracker.logZeroState(
                AppQualityInsightsUsageEvent.AppQualityInsightsZeroStateDetails.newBuilder()
                  .apply {
                    emptyState =
                      AppQualityInsightsUsageEvent.AppQualityInsightsZeroStateDetails.EmptyState
                        .NO_LOGIN
                  }
                  .build()
              )
              tabPanel.setComponent(loggedOutErrorStateComponent())
            }
            is AppInsightsModel.Authenticated -> {
              tabPanel.setComponent(
                VitalsTab(
                  appInsightsModel.controller,
                  project,
                  Clock.systemDefaultZone(),
                  AppInsightsTrackerImpl(project, AppInsightsTracker.ProductType.PLAY_VITALS),
                )
              )
            }
            else -> {}
          }
        }
      }
    }
  }

  override fun isApplicable(): Boolean = StudioFlags.PLAY_VITALS_ENABLED.get()

  override fun getConfigurationManager(project: Project) =
    project.service<VitalsConfigurationService>().manager

  private fun placeholderContent(): JPanel =
    object : JPanel() {
      private val text =
        object : StatusText() {
            override fun isStatusVisible() = true
          }
          .also {
            it.appendLine(
              "Waiting for initial sync...",
              SimpleTextAttributes.GRAYED_ATTRIBUTES,
              null,
            )
            it.attachTo(this)
          }

      override fun paint(g: Graphics?) {
        super.paint(g)
        text.paint(this, g)
      }
    }

  @Suppress("DialogTitleCapitalization")
  private fun loggedOutErrorStateComponent(): JPanel {
    val loggedOutText =
      object : StatusText() {
          override fun isStatusVisible() = true
        }
        .apply {
          appendLine(
            StudioIllustrations.Common.PLAY_CONSOLE,
            "",
            SimpleTextAttributes.REGULAR_ATTRIBUTES,
            null,
          )
          appendLine(
            "See insights from Play Console with Android Vitals",
            SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES,
            null,
          )
          if (GoogleLoginService.instance.isLoggedIn()) {
            appendLine("Authorize", SimpleTextAttributes.LINK_ATTRIBUTES) {
              LoginFeature.feature<VitalsLoginFeature>().logInAsync()
            }
            appendText(" Android Studio to connect to your Play Console account.")
          } else {
            appendLine("Log in", SimpleTextAttributes.LINK_ATTRIBUTES) {
              LoginFeature.feature<VitalsLoginFeature>().logInAsync()
            }
            appendText(" to Android Studio to connect to your Play Console account.")
          }
          appendLine("")
          appendLine(
            AllIcons.General.ContextHelp,
            "More Info",
            SimpleTextAttributes.LINK_ATTRIBUTES,
          ) {
            BrowserUtil.browse("https://d.android.com/r/studio-ui/debug/aqi-android-vitals")
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
