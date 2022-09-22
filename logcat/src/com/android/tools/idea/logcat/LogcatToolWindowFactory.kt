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
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.toolwindow.splittingtabs.SplittingTabsToolWindowFactory
import com.android.tools.idea.adb.processnamemonitor.ProcessNameMonitor
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.isAndroidEnvironment
import com.android.tools.idea.logcat.LogcatExperimentalSettings.Companion.getInstance
import com.android.tools.idea.logcat.devices.DeviceFactory
import com.android.tools.idea.logcat.filters.LogcatFilterColorSettingsPage
import com.android.tools.idea.logcat.messages.LogcatColorSettingsPage
import com.android.tools.idea.logcat.messages.LogcatColors
import com.android.tools.idea.run.ShowLogcatListener
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.options.colors.ColorSettingsPages
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
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
      .subscribe(ShowLogcatListener.TOPIC, ShowLogcatListener { serialNumber, _ -> showLogcat(toolWindow, serialNumber) })

    ProcessNameMonitor.getInstance(project).start()
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    super.createToolWindowContent(project, toolWindow)
    toolWindow.isAvailable = true
  }

  private fun showLogcat(toolWindow: ToolWindowEx, serialNumber: String) {
    AndroidCoroutineScope(toolWindow.disposable).launch {
      val device = DeviceFactory(AdbLibService.getSession(toolWindow.project)).createDevice(serialNumber)
      withContext(uiThread) {
        toolWindow.activate {
          val contentManager = toolWindow.contentManager
          val count = contentManager.contentCount
          for (i in 0 until count) {
            val content = contentManager.getContent(i)
            content?.findLogcatPresenters()?.forEach {
              if (it.getConnectedDevice()?.serialNumber == serialNumber) {
                contentManager.setSelectedContent(content, true)
                return@activate
              }
            }
          }
          createNewTab(toolWindow, device.name).findLogcatPresenters().firstOrNull()?.selectDevice(serialNumber)
        }

      }
    }
  }

  private val logcatColors: LogcatColors = LogcatColors()

  override fun isApplicable(project: Project) = isAndroidEnvironment(project) && isLogcatV2Enabled()

  override fun generateTabName(tabNames: Set<String>) =
    UniqueNameGenerator.generateUniqueName("Logcat", "", "", " (", ")") { !tabNames.contains(it) }

  override fun createChildComponent(project: Project, popupActionGroup: ActionGroup, clientState: String?) =
    LogcatMainPanel(project, popupActionGroup, logcatColors, LogcatPanelConfig.fromJson(clientState), adbSessionFactory(project))
      .also {
        logcatPresenters.add(it)
        Disposer.register(it) { logcatPresenters.remove(it) }
      }

  companion object {
    @VisibleForTesting
    internal val logcatPresenters = mutableListOf<LogcatPresenter>()
  }

}

private fun isLogcatV2Enabled() = getInstance().logcatV2Enabled

private fun Content.findLogcatPresenters(): List<LogcatPresenter> = TreeWalker(component).descendants().filterIsInstance<LogcatPresenter>()
