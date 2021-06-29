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
package com.android.tools.idea.logcat.v2

import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.wearpairing.await
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLoadingPanel
import kotlinx.coroutines.launch
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.jetbrains.android.util.AndroidBundle
import java.awt.BorderLayout

class LogcatToolWindowFactory : ToolWindowFactory, DumbAware {

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val loadingPanel = JBLoadingPanel(BorderLayout(), project)

    val adb = AndroidSdkUtils.getAdb(project) ?: run {
      thisLogger().warn("Unable to find adb binary")
      notify(project, toolWindow, content = AndroidBundle.message("android.logcat.adb.initialize.not.found"))
      return
    }

    val contentManager = toolWindow.contentManager
    val loadingContent = contentManager.factory.createContent(loadingPanel, "", false)
    loadingContent.isCloseable = false
    contentManager.addContent(loadingContent)
    loadingPanel.setLoadingText(AndroidBundle.message("android.logcat.adb.initialize.starting"))
    loadingPanel.startLoading()

    AndroidCoroutineScope(project).launch(uiThread) {
      try {
        LogcatView(project, toolWindow, AdbService.getInstance().getDebugBridge(adb).await())
        thisLogger().debug("Successfully obtained debug bridge")
      }
      catch (t: Throwable) {
        thisLogger().warn("Unable to obtain debug bridge", t)
        notify(
          project,
          toolWindow,
          content = AdbService.getDebugBridgeDiagnosticErrorMessage(t, adb),
          title = AndroidBundle.message("android.logcat.adb.initialize.failure"))
      }
      finally {
        loadingPanel.stopLoading()
        contentManager.removeContent(loadingContent, true)
      }
    }
  }

  @Suppress("UnstableApiUsage")
  private fun notify(project: Project,
                     toolWindow: ToolWindow,
                     @NotificationContent content: String,
                     @NotificationContent title: String = "") {
    NotificationGroupManager.getInstance().getNotificationGroup(toolWindow.id)
      .createNotification(title, content, NotificationType.WARNING)
      .notify(project)
  }

  override fun shouldBeAvailable(project: Project): Boolean = StudioFlags.LOGCAT_V2_ENABLE.get()
}
