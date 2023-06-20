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
package com.android.tools.idea.logcat.actions

import com.android.adblib.AdbSession
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.selector
import com.android.adblib.serialNumber
import com.android.adblib.shellCommand
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.appProcessTracker
import com.android.adblib.tools.debugging.jdwpProcessTracker
import com.android.adblib.tools.debugging.properties
import com.android.adblib.tools.debugging.sendDdmsExit
import com.android.adblib.withTextCollector
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.LogcatPresenter.Companion.CONNECTED_DEVICE
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.message.LogcatHeader
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.messages.LOGCAT_MESSAGE_KEY
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import icons.StudioIcons
import kotlinx.coroutines.launch
import javax.swing.Icon

/** A base class for actions that perform app termination */
internal sealed class TerminateAppActions(text: String, icon: Icon) : DumbAwareAction(text, null, icon) {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = false
    e.presentation.isVisible = false
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    val logcatHeader = getLogcatMessage(e)?.header ?: return

    val device = e.getData(CONNECTED_DEVICE) ?: return
    e.presentation.isVisible = isVisible(device.sdk)

    val process = findProcess(project, logcatHeader, device) ?: return
    e.presentation.isEnabled = isEnabled(logcatHeader)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    val logcatHeader = getLogcatMessage(e)?.header ?: return
    val device = e.getData(CONNECTED_DEVICE) ?: return
    val process = findProcess(project, logcatHeader, device) ?: return

    actionPerformed(AdbLibService.getSession(project), process, logcatHeader.applicationId)
  }

  private fun findProcess(project: Project, logcatHeader: LogcatHeader, device: Device): JdwpProcess? {
    val adbSession = AdbLibService.getSession(project)
    val connectedDevices = adbSession.connectedDevicesTracker.connectedDevices.value
    val connectedDevice = connectedDevices.find { it.serialNumber == device.serialNumber } ?: return null

    val processes = when (device.sdk >= 31) {
      true -> connectedDevice.appProcessTracker.appProcessFlow.value.mapNotNull { it.jdwpProcess }
      false -> connectedDevice.jdwpProcessTracker.processesFlow.value
    }
    return processes.find { it.pid == logcatHeader.pid || it.properties.processName == logcatHeader.processName }
  }

  open fun isEnabled(logcatHeader: LogcatHeader): Boolean = true

  open fun isVisible(sdk: Int): Boolean = true

  abstract fun actionPerformed(adbSession: AdbSession, process: JdwpProcess, packageName: String)

  /**
   * An action that uses `adb shell am force-stop` to terminate an app
   *
   * This action is enabled if a debuggable process with the same pid or process name is found on the device.
   *
   * Note that the `system_process` process can be debuggable but cannot be force stopped so this action is disabled for it.
   *
   * Also note that if a process application id cannot be resolved for some reason, the action is disabled for it because the action
   * requires an application id.
   */
  class ForceStopAppAction : TerminateAppActions(
    LogcatBundle.message("logcat.terminate.app.force.stop"),
    StudioIcons.DeviceProcessMonitor.FORCE_STOP
  ) {
    override fun isEnabled(logcatHeader: LogcatHeader): Boolean {
      return logcatHeader.applicationId != "system_process" && !logcatHeader.applicationId.startsWith("pid-")
    }

    override fun actionPerformed(adbSession: AdbSession, process: JdwpProcess, packageName: String) {
      adbSession.scope.launch {
        adbSession.deviceServices.shellCommand(process.device.selector, "am force-stop $packageName")
          .withTextCollector()
          .executeAsSingleOutput {}
      }
    }
  }

  /**
   * An action that uses kills a [JdwpProcess] directly.
   *
   * This action is enabled if a debuggable process with the same pid or process name is found on the device.
   */
  class KillAppAction : TerminateAppActions(
    LogcatBundle.message("logcat.terminate.app.kill"),
    StudioIcons.DeviceProcessMonitor.KILL_PROCESS
  ) {
    override fun actionPerformed(adbSession: AdbSession, process: JdwpProcess, packageName: String) {
      process.scope.launch {
        process.withJdwpSession {
          sendDdmsExit(1)
        }
      }
    }
  }

  /**
   * An action that uses `adb shell am crash` to terminate an app
   *
   * This action is enabled if a debuggable process with the same pid or process name is found on the device.
   *
   * This action is ony shown for devices with API level > 26 because the `am crash` command is not available below that.
   *
   * Note that the `system_process` process can be debuggable but cannot be crashed so this action is disabled for it.
   *
   * Also note that if a process application id cannot be resolved for some reason, the action is disabled for it because the action
   * requires an application id.
   *
   * TODO(b/274818920): Use a dedicated icon
   */
  class CrashAppAction : TerminateAppActions(
    LogcatBundle.message("logcat.terminate.app.crash"),
    StudioIcons.AppQualityInsights.ISSUE
  ) {
    override fun isEnabled(logcatHeader: LogcatHeader): Boolean {
      return logcatHeader.applicationId != "system_process" && !logcatHeader.applicationId.startsWith("pid-")
    }

    override fun isVisible(sdk: Int): Boolean {
      return sdk >= 26
    }

    override fun actionPerformed(adbSession: AdbSession, process: JdwpProcess, packageName: String) {
      adbSession.scope.launch {
        adbSession.deviceServices.shellCommand(process.device.selector, "am crash $packageName")
          .withTextCollector()
          .executeAsSingleOutput {}
      }
    }
  }
}

private fun getLogcatMessage(e: AnActionEvent): LogcatMessage? {
  val editor = e.getData(CommonDataKeys.EDITOR) as EditorEx? ?: return null
  val offset = editor.caretModel.offset

  var result: LogcatMessage? = null
  editor.document.processRangeMarkersOverlappingWith(offset, offset) {
    result = it.getUserData(LOGCAT_MESSAGE_KEY)
    result == null
  }
  return result
}
