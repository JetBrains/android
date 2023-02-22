/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.flags.StudioFlags
import com.google.gct.login.GoogleLogin
import com.google.services.firebase.insights.AppInsightsModel
import com.google.services.firebase.insights.AppInsightsService
import com.google.services.firebase.logs.FirebaseTracker
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowBalloonShowOptions
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.StatusText
import icons.FirebaseIcons
import icons.StudioIcons
import java.awt.Graphics
import java.time.Clock
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.util.application.invokeLater

// This must match the [toolwindow] id in plugin.xml
internal const val APP_INSIGHTS_ID = "App Quality Insights"
internal const val CONTENT_DISPLAY_NAME = "Firebase Crashlytics"
internal const val AQI_POPUP_SHOWN_KEY = "com.google.services.firebase.aqiPopupShown"

class AppInsightsToolWindowFactory : DumbAware, ToolWindowFactory {
  companion object {
    fun setActiveTab(project: Project) {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(APP_INSIGHTS_ID)
      toolWindow
        ?.contentManager
        ?.setSelectedContent(toolWindow.contentManager.findContent(CONTENT_DISPLAY_NAME))
    }

    fun show(project: Project, callback: Runnable? = null) =
      ToolWindowManagerEx.getInstanceEx(project).getToolWindow(APP_INSIGHTS_ID)?.show(callback)

    fun maybeShowAqiBalloon(project: Project) {
      if (PropertiesComponent.getInstance(project).getBoolean(AQI_POPUP_SHOWN_KEY)) {
        return
      }
      try {
        invokeLater {
          if (project.isDisposed) return@invokeLater
          ToolWindowManager.getInstance(project)
            .notifyByBalloon(
              ToolWindowBalloonShowOptions(
                toolWindowId = APP_INSIGHTS_ID,
                type = MessageType.INFO,
                icon = StudioIcons.AppQualityInsights.ISSUE,
                htmlBody =
                  "Click to see your Crashlytics issues here.<br/><a href=\"\">Don't show again</a>",
                listener = { e ->
                  if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    PropertiesComponent.getInstance(project).setValue(AQI_POPUP_SHOWN_KEY, true)
                  }
                }
              )
            )
        }
      } catch (_: AlreadyDisposedException) {
        // The tool window and most like the project were already disposed when the balloon is
        // shown.
      }
    }

    fun showErrorBalloon(
      project: Project,
      htmlMsg: String,
      hyperlinkListener: HyperlinkListener? = null
    ) {
      try {
        invokeLater {
          if (project.isDisposed) return@invokeLater
          ToolWindowManager.getInstance(project)
            .notifyByBalloon(
              ToolWindowBalloonShowOptions(
                toolWindowId = APP_INSIGHTS_ID,
                type = MessageType.ERROR,
                icon = StudioIcons.AppQualityInsights.ISSUE,
                htmlBody = htmlMsg,
                listener = hyperlinkListener
              )
            )
        }
      } catch (_: AlreadyDisposedException) {
        // The tool window and most like the project were already disposed when the balloon is
        // shown.
      }
    }
  }

  override fun isApplicable(project: Project) =
    StudioFlags.APP_INSIGHTS_ENABLED.get() && IdeInfo.getInstance().isAndroidStudio

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    PropertiesComponent.getInstance(project).setValue(AQI_POPUP_SHOWN_KEY, true)
    createToolWindowContent(project, toolWindow, project.service())
  }

  @VisibleForTesting
  fun createToolWindowContent(project: Project, toolWindow: ToolWindow, tracker: FirebaseTracker) {
    val insightsService = project.service<AppInsightsService>()
    val contentFactory = ContentFactory.getInstance()

    // Placeholder content to fill the gap we show nothing.
    val placeholderContent =
      placeholderContent(contentFactory).also { toolWindow.contentManager.addContent(it) }

    AndroidCoroutineScope(toolWindow.disposable).launch(AndroidDispatchers.uiThread) {
      var isLoggedIn = true
      insightsService.configuration.collect { appInsightsModel ->
        if (placeholderContent.isValid) {
          placeholderContent.manager?.removeContent(placeholderContent, true)
        }

        when (appInsightsModel) {
          AppInsightsModel.Unauthenticated -> {
            if (isLoggedIn) {
              toolWindow.contentManager.removeAllContents(true)
              toolWindow.contentManager.addContent(loggedOutErrorStateComponent(contentFactory))
              isLoggedIn = false
              tracker.logZeroState(
                AppQualityInsightsUsageEvent.AppQualityInsightsZeroStateDetails.newBuilder()
                  .apply {
                    emptyState =
                      AppQualityInsightsUsageEvent.AppQualityInsightsZeroStateDetails.EmptyState
                        .NO_LOGIN
                  }
                  .build()
              )
            }
          }
          is AppInsightsModel.Authenticated -> {
            if (!isLoggedIn) {
              toolWindow.contentManager.removeAllContents(true)
              isLoggedIn = true
            }
            populateTab(toolWindow, contentFactory, insightsService, project, tracker)
          }
        }
      }
    }
    toolWindow.setDefaultContentUiType(ToolWindowContentUiType.TABBED)
    toolWindow.show()
    toolWindow.stripeTitle = "App Quality Insights"
  }

  private fun populateTab(
    toolWindow: ToolWindow,
    contentFactory: ContentFactory,
    insightsService: AppInsightsService,
    project: Project,
    tracker: FirebaseTracker
  ) {
    val tab =
      AppInsightsTab(
        projectController = insightsService.getController(),
        clock = Clock.systemDefaultZone(),
        project = project,
        tracker = tracker,
        parentDisposable = toolWindow.disposable
      )

    val tabContent =
      contentFactory.createContent(tab, CONTENT_DISPLAY_NAME, false).apply {
        putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
        icon = FirebaseIcons.ACTION_ICON
      }
    toolWindow.contentManager.addContent(tabContent)
  }

  private fun placeholderContent(contentFactory: ContentFactory): Content {
    return contentFactory.createContent(
      object : JPanel() {
        private val text =
          object : StatusText() {
              override fun isStatusVisible() = true
            }
            .also {
              it.appendLine(
                "Waiting for initial sync...",
                SimpleTextAttributes.GRAYED_ATTRIBUTES,
                null
              )
              it.attachTo(this)
            }

        override fun paint(g: Graphics?) {
          super.paint(g)
          text.paint(this, g)
        }
      },
      null,
      false
    )
  }

  @Suppress("DialogTitleCapitalization")
  private fun loggedOutErrorStateComponent(contentFactory: ContentFactory): Content {
    val loggedOutText =
      object : StatusText() {
          override fun isStatusVisible() = true
        }
        .apply {
          appendLine(FirebaseIcons.FIREBASE_LOGO, "", SimpleTextAttributes.REGULAR_ATTRIBUTES, null)
          appendLine(
            "See real-world app quality insights here",
            SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES,
            null
          )
          appendLine("Log in", SimpleTextAttributes.LINK_ATTRIBUTES) {
            GoogleLogin.instance.logIn()
          }
          appendText(" to Android Studio to connect to your Firebase Account")
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

    return contentFactory.createContent(
      object : JPanel() {
        init {
          loggedOutText.attachTo(this)
        }

        override fun paint(g: Graphics?) {
          super.paint(g)
          loggedOutText.paint(this, g)
        }
      },
      null,
      false
    )
  }

  override fun init(toolWindow: ToolWindow) {
    toolWindow.setToHideOnEmptyContent(false)
    try {
      invokeLater {
        if (toolWindow.project.isDisposed) return@invokeLater
        AppInsightsService.getInstance(toolWindow.project)
      }
    } catch (_: AlreadyDisposedException) {
      // The tool window and most like the project were already disposed when the balloon is
      // shown.
    }
  }
}
