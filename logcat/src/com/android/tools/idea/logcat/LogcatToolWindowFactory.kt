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
package com.android.tools.idea.logcat

import com.android.adblib.AdbSession
import com.android.tools.adtui.toolwindow.splittingtabs.SplittingTabsToolWindowFactory
import com.android.tools.idea.adb.processnamemonitor.ProcessNameMonitor
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.isAndroidEnvironment
import com.android.tools.idea.logcat.LogcatExperimentalSettings.Companion.getInstance
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig.Custom
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig.Preset
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.devices.DeviceFactory
import com.android.tools.idea.logcat.filters.LogcatFilterColorSettingsPage
import com.android.tools.idea.logcat.messages.AndroidLogcatFormattingOptions
import com.android.tools.idea.logcat.messages.LogcatColorSettingsPage
import com.android.tools.idea.logcat.messages.LogcatColors
import com.android.tools.idea.logcat.util.AndroidProjectDetectorImpl
import com.android.tools.idea.logcat.util.getDefaultFilter
import com.android.tools.idea.run.ShowLogcatListener
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.options.colors.ColorSettingsPages
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.Content
import com.intellij.util.text.UniqueNameGenerator
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting

internal class LogcatToolWindowFactory(
  private val adbSessionFactory: (Project) -> AdbSession = { AdbLibService.getInstance(it).session },
) : SplittingTabsToolWindowFactory(), DumbAware {

  private val logcatColors: LogcatColors = LogcatColors()

  // When ShowLogcatListener is activated, we do not want to create a new Logcat tab if the tool was empty
  private var insideShowLogcatListener = false

  init {
    // TODO(b/236246692): Register from XML when Logcat V2 is mainstream.
    if (isLogcatV2Enabled()) {
      ColorSettingsPages.getInstance().apply {
        registerPage(LogcatColorSettingsPage())
        registerPage(LogcatFilterColorSettingsPage())
      }
    }
  }

  override fun init(toolWindow: ToolWindow) {
    super.init(toolWindow)
    val project = (toolWindow as ToolWindowEx).project
    project.messageBus.connect(toolWindow.disposable)
      .subscribe(ShowLogcatListener.TOPIC, ShowLogcatListener { serialNumber, applicationId ->
        showLogcat(toolWindow, serialNumber, applicationId)
      })

    ProcessNameMonitor.getInstance(project).start()
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    super.createToolWindowContent(project, toolWindow)
    toolWindow.isAvailable = true
  }

  private fun showLogcat(toolWindow: ToolWindowEx, serialNumber: String, applicationId: String?) {

    AndroidCoroutineScope(toolWindow.disposable).launch {
      val name = if (applicationId == null) serialNumber else "$applicationId ($serialNumber)"

      val device = kotlin.runCatching { DeviceFactory(adbSessionFactory(toolWindow.project)).createDevice(serialNumber) }.getOrNull()
      withContext(uiThread) {
        insideShowLogcatListener = true
        try {
          val content = toolWindow.findTab(name)
          when {
            content != null -> {
              toolWindow.contentManager.setSelectedContent(content)
              toolWindow.activate(null)
            }

            device != null -> {
              toolWindow.createLogcatTab(name, device, applicationId)
              toolWindow.activate(null)
            }

            else -> {
              val title = LogcatBundle.message("logcat.dialog.title")
              val message = LogcatBundle.message("logcat.device.offline", serialNumber)
              val button = IdeBundle.message("button.ok")
              @Suppress("UnstableApiUsage")
              MessageDialogBuilder.Message(title, message).buttons(button).asWarning().show()
            }
          }
        }
        finally {
          insideShowLogcatListener = false
        }
      }
    }
  }

  private fun ToolWindowEx.createLogcatTab(name: String, device: Device, applicationId: String?) {
    val filter = when (applicationId) {
      null -> getDefaultFilter(project, AndroidProjectDetectorImpl())
      else -> "package:$applicationId"
    }
    val config = LogcatPanelConfig(device, getDefaultFormattingConfig(), filter, isSoftWrap = false)
    createNewTab(this, name, LogcatPanelConfig.toJson(config))
  }

  override fun shouldCreateNewTabWhenEmpty() = !insideShowLogcatListener

  override fun isApplicable(project: Project) = isAndroidEnvironment(project) && isLogcatV2Enabled()

  override fun generateTabName(tabNames: Set<String>) =
    UniqueNameGenerator.generateUniqueName("Logcat", "", "", " (", ")") { !tabNames.contains(it) }

  override fun createChildComponent(project: Project, popupActionGroup: ActionGroup, clientState: String?) =
    LogcatMainPanel(project, popupActionGroup, logcatColors, LogcatPanelConfig.fromJson(clientState), adbSessionFactory)
      .also {
        logcatPresenters.add(it)
        Disposer.register(it) { logcatPresenters.remove(it) }
      }

  companion object {
    @VisibleForTesting
    internal val logcatPresenters = mutableListOf<LogcatPresenter>()
  }

}

private fun ToolWindowEx.findTab(name: String): Content? {
  val count = contentManager.contentCount
  for (i in 0 until count) {
    val content = contentManager.getContent(i)
    if (content?.tabName == name) {
      return content
    }
  }
  return null
}

private fun isLogcatV2Enabled() = getInstance().logcatV2Enabled

private fun getDefaultFormattingConfig(): LogcatPanelConfig.FormattingConfig {
  val formattingOptions = AndroidLogcatFormattingOptions.getDefaultOptions()
  val style = formattingOptions.getStyle()
  return if (style == null) Custom(formattingOptions) else Preset(style)
}