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
package com.android.tools.idea.insights.ui

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowBalloonShowOptions
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.ui.content.ContentFactory
import icons.StudioIcons
import javax.swing.event.HyperlinkListener
import org.jetbrains.annotations.VisibleForTesting

class AppInsightsToolWindowFactory : DumbAware, ToolWindowFactory {
  companion object {
    fun show(project: Project, tabName: String, callback: (() -> Unit)?) {
      val toolWindowManager = ToolWindowManager.getInstance(project).getToolWindow(APP_INSIGHTS_ID)
      toolWindowManager?.show {
        toolWindowManager.contentManager.setSelectedContent(
          toolWindowManager.contentManager.findContent(tabName)
        )
        callback?.invoke()
      }
    }

    fun showBalloon(
      project: Project,
      type: MessageType,
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
                type = type,
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
    StudioFlags.APP_INSIGHTS_ENABLED.get()

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    createTabs(project, toolWindow)
  }

  @VisibleForTesting
  fun createTabs(project: Project, toolWindow: ToolWindow) {
    val contentFactory = ContentFactory.getInstance()

    AppInsightsTabProvider.EP_NAME.extensionList
      .filter { it.isApplicable() }
      .forEach { tabProvider ->
        val tabPanel = AppInsightsTabPanel()
        tabProvider.populateTab(project, tabPanel)
        val tabContent =
          contentFactory.createContent(tabPanel, tabProvider.tabDisplayName, false).apply {
            putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
            icon = tabProvider.tabIcon
          }
        tabContent.setDisposer(tabPanel)
        toolWindow.contentManager.addContent(tabContent)
      }

    toolWindow.setDefaultContentUiType(ToolWindowContentUiType.TABBED)
    toolWindow.show()
    toolWindow.stripeTitle = "App Quality Insights"
  }

  override fun init(toolWindow: ToolWindow) {
    toolWindow.setToHideOnEmptyContent(false)
  }
}
