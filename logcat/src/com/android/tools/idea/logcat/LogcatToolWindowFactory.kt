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

import com.android.processmonitor.monitor.ProcessNameMonitor
import com.android.tools.adtui.toolwindow.splittingtabs.SplittingTabsToolWindowFactory
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig.Custom
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig.Preset
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.devices.DeviceFinder
import com.android.tools.idea.logcat.messages.AndroidLogcatFormattingOptions
import com.android.tools.idea.logcat.messages.LogcatColors
import com.android.tools.idea.logcat.service.LogcatService
import com.android.tools.idea.logcat.util.AndroidProjectDetectorImpl
import com.android.tools.idea.logcat.util.getDefaultFilter
import com.android.tools.idea.run.ClearLogcatListener
import com.android.tools.idea.run.ShowLogcatListener
import com.android.tools.idea.run.ShowLogcatListener.DeviceInfo
import com.android.tools.idea.run.ShowLogcatListener.DeviceInfo.EmulatorDeviceInfo
import com.android.tools.idea.run.ShowLogcatListener.DeviceInfo.PhysicalDeviceInfo
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.Content
import com.intellij.util.text.UniqueNameGenerator
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString

internal class LogcatToolWindowFactory : SplittingTabsToolWindowFactory(), DumbAware {

  private val logcatColors: LogcatColors = LogcatColors()

  // When ShowLogcatListener is activated, we do not want to create a new Logcat tab if the tool was
  // empty
  private var insideShowLogcatListener = false

  override fun init(toolWindow: ToolWindow) {
    super.init(toolWindow)
    val project = (toolWindow as ToolWindowEx).project
    val messageBusConnection = project.messageBus.connect(toolWindow.disposable)
    messageBusConnection.subscribe(
      ShowLogcatListener.TOPIC,
      object : ShowLogcatListener {
        override fun showLogcat(deviceInfo: DeviceInfo, applicationId: String?) {
          showLogcat(toolWindow, deviceInfo, applicationId)
        }

        override fun showLogcatFile(path: Path, displayName: String?) {
          openLogcatFile(toolWindow, path, displayName)
        }
      },
    )
    messageBusConnection.subscribe(
      ClearLogcatListener.TOPIC,
      ClearLogcatListener { serialNumber ->
        if (logcatPresenters.none { it.getConnectedDevice()?.serialNumber == serialNumber }) {
          AndroidCoroutineScope(toolWindow.disposable).launch {
            LogcatService.getInstance(project).clearLogcat(serialNumber)
          }
        }
      },
    )

    ApplicationManager.getApplication().executeOnPooledThread {
      project.getService(ProcessNameMonitor::class.java).start()
    }
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    super.createToolWindowContent(project, toolWindow)
    toolWindow.isAvailable = true
  }

  private fun showLogcat(toolWindow: ToolWindowEx, deviceInfo: DeviceInfo, applicationId: String?) {
    AndroidCoroutineScope(toolWindow.disposable).launch {
      val name = if (applicationId == null) deviceInfo.id else "$applicationId (${deviceInfo.id})"
      val device =
        toolWindow.project.service<DeviceFinder>().findDevice(deviceInfo.serialNumber)
          ?: deviceInfo.toOfflineDevice()
      withContext(uiThread) {
        insideShowLogcatListener = true
        try {
          val content = toolWindow.findTab(name)
          if (content != null) {
            toolWindow.contentManager.setSelectedContent(content)
          } else {
            toolWindow.createLogcatTab(name, device, applicationId)
          }
          toolWindow.activate(null)
        } finally {
          insideShowLogcatListener = false
        }
      }
    }
  }

  private fun ToolWindowEx.createLogcatTab(name: String, device: Device, applicationId: String?) {
    val filter =
      when (applicationId) {
        null -> getDefaultFilter(project, AndroidProjectDetectorImpl())
        else -> "package:$applicationId"
      }
    val config =
      LogcatPanelConfig(
        device = device,
        file = null,
        formattingConfig = getDefaultFormattingConfig(),
        filter = filter,
        filterMatchCase = false,
        isSoftWrap = false,
      )
    createNewTab(this, name, LogcatPanelConfig.toJson(config))
  }

  private fun openLogcatFile(toolWindow: ToolWindowEx, path: Path, displayName: String?) {
    invokeLater {
      insideShowLogcatListener = true
      try {
        val config =
          LogcatPanelConfig(
            device = null,
            file = path.pathString,
            formattingConfig = getDefaultFormattingConfig(),
            filter = "",
            filterMatchCase = false,
            isSoftWrap = false,
          )

        createNewTab(
          toolWindow,
          displayName ?: path.fileName.name,
          LogcatPanelConfig.toJson(config),
        )
        toolWindow.activate(null)
      } finally {
        insideShowLogcatListener = false
      }
    }
  }

  override fun shouldCreateNewTabWhenEmpty() = !insideShowLogcatListener

  override fun generateTabName(tabNames: Set<String>) =
    UniqueNameGenerator.generateUniqueName("Logcat", "", "", " (", ")") { !tabNames.contains(it) }

  override fun createChildComponent(
    project: Project,
    popupActionGroup: DefaultActionGroup,
    clientState: String?,
  ) =
    LogcatMainPanel(
        project,
        popupActionGroup,
        logcatColors,
        LogcatPanelConfig.fromJson(clientState),
      )
      .also {
        logcatPresenters.add(it)
        Disposer.register(it) { logcatPresenters.remove(it) }
      }

  companion object {
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

private fun getDefaultFormattingConfig(): LogcatPanelConfig.FormattingConfig {
  val formattingOptions = AndroidLogcatFormattingOptions.getDefaultOptions()
  val style = formattingOptions.getStyle()
  return if (style == null) Custom(formattingOptions) else Preset(style)
}

private fun DeviceInfo.toOfflineDevice(): Device {
  return when (this) {
    is PhysicalDeviceInfo ->
      Device.createPhysical(serialNumber, false, release, sdk, manufacturer, model, featureLevel)
    is EmulatorDeviceInfo ->
      Device.createEmulator(serialNumber, false, release, sdk, avdName, avdPath, featureLevel)
  }
}
