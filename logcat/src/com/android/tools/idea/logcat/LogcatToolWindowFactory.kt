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

import com.android.ddmlib.IDevice
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.toolwindow.splittingtabs.SplittingTabsToolWindowFactory
import com.android.tools.idea.adb.processnamemonitor.ProcessNameMonitor
import com.android.tools.idea.isAndroidEnvironment
import com.android.tools.idea.logcat.LogcatExperimentalSettings.Companion.getInstance
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
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.EventQueue

internal class LogcatToolWindowFactory @TestOnly internal constructor(
  private val processNameMonitorFactory: (Project) -> ProcessNameMonitor
) : SplittingTabsToolWindowFactory(), DumbAware {

  constructor() : this({ ProcessNameMonitor.getInstance(it) })

  init {
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
    project.messageBus.connect(project)
      .subscribe(ShowLogcatListener.TOPIC, ShowLogcatListener { device, _ -> showLogcat(toolWindow, device) })

    processNameMonitorFactory(project).start()
  }

  private fun showLogcat(toolWindow: ToolWindowEx, device: IDevice) {
    EventQueue.invokeLater {
      toolWindow.activate {
        val contentManager = toolWindow.contentManager
        val count = contentManager.contentCount
        for (i in 0 until count) {
          val content = contentManager.getContent(i)
          content?.findLogcatPresenters()?.forEach {
            if (it.getConnectedDevice() == device) {
              contentManager.setSelectedContent(content, true)
              return@activate
            }
          }
        }
        // TODO(aalbert): Getting a pretty name for a device is complicated since it requires fetching properties from device. Use serial
        //  number as a tab name for now.
        createNewTab(toolWindow, device.serialNumber).findLogcatPresenters().firstOrNull()?.selectDevice(device)
      }
    }
  }

  private val logcatColors: LogcatColors = LogcatColors()

  override fun isApplicable(project: Project) = isAndroidEnvironment(project) && isLogcatV2Enabled()

  override fun generateTabName(tabNames: Set<String>) =
    UniqueNameGenerator.generateUniqueName("Logcat", "", "", " (", ")") { !tabNames.contains(it) }

  override fun createChildComponent(project: Project, popupActionGroup: ActionGroup, clientState: String?) =
    LogcatMainPanel(project, popupActionGroup, logcatColors, LogcatPanelConfig.fromJson(clientState)).also {
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
