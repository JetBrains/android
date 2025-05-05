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
import com.android.tools.idea.insights.AppInsightsConfigurationManager
import com.android.tools.idea.insights.AppInsightsModel
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.analytics.AppInsightsTrackerImpl
import com.android.tools.idea.insights.ui.AppInsightsTabPanel
import com.android.tools.idea.insights.ui.AppInsightsTabProvider
import com.android.tools.idea.insights.ui.ServiceDeprecatedPanel
import com.android.tools.idea.vitals.VitalsInsightsProvider
import com.android.tools.idea.vitals.VitalsLoginFeature
import com.google.gct.login2.GoogleLoginService
import com.google.gct.login2.LoginFeature
import com.google.gct.login2.createHelpTooltip
import com.google.gct.login2.toText
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.StatusText
import icons.StudioIllustrations
import java.awt.Graphics
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.time.Clock
import javax.swing.JButton
import javax.swing.JPanel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VitalsTabProvider : AppInsightsTabProvider {
  override val displayName = VitalsInsightsProvider.displayName

  override val icon = StudioIllustrations.Common.PLAY_CONSOLE_ICON

  override fun populateTab(
    project: Project,
    tabPanel: AppInsightsTabPanel,
    activeTabFlow: Flow<Boolean>,
  ) {
    val scope = AndroidCoroutineScope(tabPanel)
    val deprecationData = getConfigurationManager(project).deprecationData
    val tracker = AppInsightsTrackerImpl(project, AppInsightsTracker.ProductType.PLAY_VITALS)
    if (deprecationData.isUnsupported()) {
      tabPanel.setComponent(
        ServiceDeprecatedPanel(scope, activeTabFlow, tracker, deprecationData) {
          UpdateChecker.updateAndShowResult(project)
        }
      )
      return
    }
    tabPanel.setComponent(placeholderContent())
    scope.launch(AndroidDispatchers.diskIoThread) {
      val configManager = project.service<VitalsConfigurationService>().manager
      val tracker = AppInsightsTrackerImpl(project, AppInsightsTracker.ProductType.PLAY_VITALS)
      withContext(AndroidDispatchers.uiThread) {
        // Combine with active user flow to get the logged out -> logged in + not authorized update
        val loginService = service<GoogleLoginService>()
        var shouldRefresh = false
        val flow =
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
              tabPanel.setComponent(
                if (StudioFlags.USE_1P_LOGIN_UI.get()) {
                  loggedOut1pPanel()
                } else {
                  loggedOutErrorStateComponent()
                }
              )
              shouldRefresh = true
            }
            is AppInsightsModel.Authenticated -> {
              if (shouldRefresh) {
                shouldRefresh = false
                appInsightsModel.controller.refresh()
              }
              tabPanel.setComponent(
                VitalsTab(
                  appInsightsModel.controller,
                  project,
                  Clock.systemDefaultZone(),
                  AppInsightsTrackerImpl(project, AppInsightsTracker.ProductType.PLAY_VITALS),
                  activeTabFlow,
                )
              )
            }
            is AppInsightsModel.InitializationFailed -> {
              tabPanel.setComponent(initializationFailedComponent(configManager))
              shouldRefresh = true
            }
            else -> {}
          }
        }
      }
    }
  }

  override fun isApplicable(): Boolean = true

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
              LoginFeature.feature<VitalsLoginFeature>().logInBlocking()
            }
            appendText(" Android Studio to connect to your Play Console account.")
          } else {
            appendLine("Log in", SimpleTextAttributes.LINK_ATTRIBUTES) {
              LoginFeature.feature<VitalsLoginFeature>().logInBlocking()
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

  private fun loggedOut1pPanel() =
    object : JPanel(GridBagLayout()) {
      init {
        val feature = LoginFeature.feature<VitalsLoginFeature>()
        val gbc =
          GridBagConstraints().apply {
            gridx = 0
            gridy = 0
          }
        add(JBLabel(StudioIllustrations.Common.PLAY_CONSOLE), gbc)

        gbc.apply { gridy += 1 }
        add(
          JBLabel("See insights from Play Console with Android Vitals").apply {
            font = JBFont.label().asBold()
          },
          gbc,
        )

        val moreInfoPanel =
          JPanel(HorizontalLayout(4)).apply {
            border = JBUI.Borders.empty(8, 0)
            val labelText =
              if (GoogleLoginService.instance.isLoggedIn()) {
                "Authorize Android Studio to connect your Play Console account."
              } else {
                "Sign in to connect your Play Console account."
              }
            add(JBLabel(labelText).apply { foreground = NamedColorUtil.getInactiveTextColor() })
            add(
              ActionLink("More info") {
                  BrowserUtil.browse("https://d.android.com/r/studio-ui/debug/aqi-android-vitals")
                }
                .apply {
                  setExternalLinkIcon()
                  isFocusable = false
                  border = JBUI.Borders.empty()
                }
            )
          }
        gbc.apply { gridy += 1 }
        add(moreInfoPanel, gbc)

        gbc.apply { gridy += 1 }
        val buttonText =
          if (GoogleLoginService.instance.isLoggedIn()) {
            "Authorize Android Vitals"
          } else {
            "Sign in to Google"
          }
        add(
          JButton(buttonText).apply {
            addActionListener { feature.logInBlocking() }
            isFocusable = false
          },
          gbc,
        )

        val scopePanel =
          JPanel(HorizontalLayout(4)).apply {
            border = JBUI.Borders.empty(8, 0)

            add(
              JBLabel("${feature.oAuthScopes.toText()} will be requested.").apply {
                foreground = NamedColorUtil.getInactiveTextColor()
                isFocusable = false
              }
            )
            add(
              JBLabel(AllIcons.General.ContextHelp).apply {
                feature.createHelpTooltip().installOn(this)
              }
            )
          }
        gbc.apply { gridy += 1 }
        add(scopePanel, gbc)
      }
    }

  private fun initializationFailedComponent(
    configurationManager: AppInsightsConfigurationManager
  ): JPanel {
    val failureText =
      object : StatusText() {
          override fun isStatusVisible() = true
        }
        .apply {
          appendLine("Failed to query for accessible Android Vitals apps.")
          appendLine("Refresh", SimpleTextAttributes.LINK_ATTRIBUTES) { e ->
            configurationManager.refreshConfiguration()
          }
        }

    return object : JPanel() {
      init {
        failureText.attachTo(this)
      }

      override fun paint(g: Graphics?) {
        super.paint(g)
        failureText.paint(this, g)
      }
    }
  }
}
