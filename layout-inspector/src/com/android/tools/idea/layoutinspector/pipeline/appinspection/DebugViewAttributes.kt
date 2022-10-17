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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.annotations.concurrency.Slow
import com.android.ddmlib.AndroidDebugBridge
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.layoutinspector.pipeline.adb.AbortAdbCommandRunnable
import com.android.tools.idea.layoutinspector.pipeline.adb.AdbUtils
import com.android.tools.idea.layoutinspector.pipeline.adb.executeShellCommand
import com.android.tools.idea.project.AndroidNotification
import com.google.common.html.HtmlEscapers
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Command that will be run on the device through adb shell.
 * Used to put, delete and get the settings value.
 */
private sealed class Command(val setting: String) {
  class Put(setting: String, val value: String) : Command(setting)
  class Delete(setting: String) : Command(setting)
  class Get(setting: String) : Command(setting)

  fun get(): String {
    return when (this) {
      is Put -> "settings put global ${this.setting} ${this.value}"
      is Delete -> "settings delete global ${this.setting}"
      is Get -> "settings get global ${this.setting}"
    }
  }
}

private const val PER_DEVICE_SETTING = "debug_view_attributes"
private const val PER_APP_SETTING = "debug_view_attributes_application_package"

/**
 * Helper class that handles setting debug settings on the device via ADB.
 *
 * These debug settings, when set, tell the system to expose some debug data (e.g. Composables)
 * that it normally would hide.
 *
 * The View inspector works without the flag.
 * The Compose inspector without the flag works in a limited capacity, it can only show images
 * and the bounds of the root views, which on its own is not very useful.
 */
class DebugViewAttributes(
  private val project: Project,
  private val usePerDeviceSettings: Boolean = false
) {

  /**
   * The type of debug_view_attributes to be used
   */
  private val setting: String
    get() {
      return if (usePerDeviceSettings) PER_DEVICE_SETTING else PER_APP_SETTING
    }

  private var abortDeleteRunnable: AbortAdbCommandRunnable? = null

  /**
   * Enable debug view attributes for the current process.
   *
   * Ignore failures since we are able to inspect the process without debug view attributes.
   * @return true if the global attributes were changed.
   */
  @Slow
  fun set(process: ProcessDescriptor): Boolean {
    // flag was already set - no need to set twice
    if (abortDeleteRunnable != null) return false

    var errorMessage: String
    var settingsUpdated = false

    val putValue = if (usePerDeviceSettings) "1" else process.name
    val putCommand = Command.Put(setting, putValue)

    try {
      val adb = AdbUtils.getAdbFuture(project).get() ?: return false
      if (!shouldSetFlag(adb, process.device, process.name)) {
        return false
      }
      errorMessage = executePut(adb, process.device, putCommand)

      if (errorMessage.isEmpty()) {
        settingsUpdated = true

        // Later, we'll try to clear the setting via `clear`, but we also register additional logic to trigger automatically
        // (a trap command) if the user forcefully closes the connection under us (e.g. closing the emulator or
        // pulling their USB cable).
        abortDeleteRunnable = AbortAdbCommandRunnable(
          adb,
          process.device,
          // This works by spawning a subshell which hangs forever (waiting for a read that never gets satisfied)
          // but triggers the delete request when that shell is forcefully exited.
          "sh -c 'trap \"${Command.Delete(setting).get()}\" EXIT; read'"
        ).also {
          AndroidExecutors.getInstance().workerThreadExecutor.execute(it)
        }
      }
    }
    catch (ex: Exception) {
      Logger.getInstance(DebugViewAttributes::class.java).warn(ex)
      errorMessage = ex.message ?: ex.javaClass.simpleName
    }
    if (errorMessage.isNotEmpty()) {
      val encoder = HtmlEscapers.htmlEscaper()
      val text = encoder.escape("Unable to set the global setting:") + "<br/>" +
                 encoder.escape("\"${putCommand.setting}\"") + "<br/>" +
                 encoder.escape("to: \"${putCommand.value}\"") + "<br/><br/>" +
                 encoder.escape("Error: $errorMessage")
      AndroidNotification.getInstance(project).showBalloon("Could not enable resolution traces",
                                                           text, NotificationType.WARNING)
    }
    return settingsUpdated
  }

  /**
   * Disable debug view attributes for the current process that were set when we connected.
   *
   * Return true if the debug view attributes were successfully disabled.
   */
  @Slow
  fun clear(process: ProcessDescriptor) {
    // the flag was not set using this class, we should not turn it off
    if (abortDeleteRunnable == null) return

    try {
      val adb = AdbUtils.getAdbFuture(project).get() ?: return
      if (shouldExecuteDelete(adb, process.device, process.name)) {
        executeDelete(adb, process.device)
      }
    }
    catch (_: Exception) { }
    finally {
      abortDeleteRunnable?.stop()
      abortDeleteRunnable = null
    }
  }

  private fun shouldSetFlag(adb: AndroidDebugBridge, device: DeviceDescriptor, processName: String): Boolean {
    // never turn on the setting if the per-device setting is on.
    if (isPerDeviceSettingOn(adb, device)) {
      return false
    }

    if (!usePerDeviceSettings) {
      // don't turn on the per-app setting, if it's already on.
      if(isPerAppSettingOn(adb, device, processName)) {
        return false
      }
    }

    return true
  }

  private fun shouldExecuteDelete(adb: AndroidDebugBridge, device: DeviceDescriptor, processName: String): Boolean {
    return if (usePerDeviceSettings) {
      isPerDeviceSettingOn(adb, device)
    }
    else {
      isPerAppSettingOn(adb, device, processName)
    }
  }

  /**
   * Turns on the [setting] flag.
   * @return empty string in case of success, or error message otherwise.
   */
  private fun executePut(adb: AndroidDebugBridge, device: DeviceDescriptor, putCommand: Command.Put): String {
    return adb.executeShellCommand(device, putCommand.get())
  }

  private fun executeDelete(adb: AndroidDebugBridge, device: DeviceDescriptor) {
    adb.executeShellCommand(device, Command.Delete(setting).get())
  }

  private fun isPerAppSettingOn(adb: AndroidDebugBridge, device: DeviceDescriptor, processName: String): Boolean {
    // A return value of process.name means: the debug_view_attributes are already turned on for this process.
    val app = adb.executeShellCommand(device, Command.Get(PER_APP_SETTING).get())
    return app == processName
  }

  private fun isPerDeviceSettingOn(adb: AndroidDebugBridge, device: DeviceDescriptor): Boolean {
    // A return value of "null" or "0" means: "debug_view_attributes" is not currently turned on for all processes on the device.
    return adb.executeShellCommand(device, Command.Get(PER_DEVICE_SETTING).get()) !in listOf("null", "0")
  }
}