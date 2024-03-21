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
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.StatusNotificationAction
import com.android.tools.idea.layoutinspector.pipeline.adb.AdbUtils
import com.android.tools.idea.layoutinspector.pipeline.adb.executeShellCommand
import com.android.tools.idea.project.AndroidNotification
import com.google.common.html.HtmlEscapers
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotificationPanel

/**
 * Command that will be run on the device through adb shell. Used to put, delete and get the
 * settings value.
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

/**
 * Helper class that handles setting debug settings on the device via ADB.
 *
 * Flags should be set and cleared using the same instance of this class.
 *
 * These debug settings, when set, tell the system to expose some debug data (e.g. Composables) that
 * it normally would hide.
 *
 * The View inspector works without the flag. The Compose inspector without the flag works in a
 * limited capacity, it can only show images and the bounds of the root views, which on its own is
 * not very useful.
 */
object DebugViewAttributes {

  /**
   * Enable debug view attributes for the current process.
   *
   * Ignore failures since we are able to inspect the process without debug view attributes.
   *
   * @return true if the global attributes were changed.
   */
  @Slow
  fun set(project: Project, device: DeviceDescriptor): Boolean {
    var errorMessage: String
    var settingsUpdated = false

    val putCommand = Command.Put(PER_DEVICE_SETTING, "1")

    try {
      val adb = AdbUtils.getAdbFuture(project).get() ?: return false
      if (!shouldSetFlag(adb, device)) {
        return false
      }
      errorMessage = executePut(adb, device, putCommand)

      if (errorMessage.isEmpty()) {
        settingsUpdated = true
      }
    } catch (ex: Exception) {
      Logger.getInstance(DebugViewAttributes::class.java).warn(ex)
      errorMessage = ex.message ?: ex.javaClass.simpleName
    }
    if (errorMessage.isNotEmpty()) {
      val encoder = HtmlEscapers.htmlEscaper()
      val text =
        encoder.escape("Unable to set the global setting:") +
          "<br/>" +
          encoder.escape("\"${putCommand.setting}\"") +
          "<br/>" +
          encoder.escape("to: \"${putCommand.value}\"") +
          "<br/><br/>" +
          "Go to Developer Options on your device and enable \"View Attribute Inspection\"" +
          "<br/><br/>" +
          encoder.escape("Error: $errorMessage")
      AndroidNotification.getInstance(project)
        .showBalloon("Could not enable resolution traces", text, NotificationType.WARNING)
    }
    return settingsUpdated
  }

  private fun shouldSetFlag(adb: AndroidDebugBridge, device: DeviceDescriptor): Boolean {
    return !isPerDeviceSettingOn(adb, device)
  }

  /**
   * Turns on the flag.
   *
   * @return empty string in case of success, or error message otherwise.
   */
  private fun executePut(
    adb: AndroidDebugBridge,
    device: DeviceDescriptor,
    putCommand: Command.Put,
  ): String {
    return adb.executeShellCommand(device, putCommand.get())
  }

  private fun isPerDeviceSettingOn(adb: AndroidDebugBridge, device: DeviceDescriptor): Boolean {
    // A return value of "null" or "0" means: "debug_view_attributes" is not currently turned on for
    // all processes on the device.
    return adb.executeShellCommand(device, Command.Get(PER_DEVICE_SETTING).get()) !in
      listOf("null", "0")
  }
}

private const val ACTIVITY_RESTART_KEY = "activity.restart"
// TODO(b/330406958): replace with redirect URL
// TODO(b/330406958): update documentation
private const val DEBUG_VIEW_ATTRIBUTES_DOCUMENTATION_URL =
  "https://developer.android.com/studio/debug/layout-inspector#activity-restart"

/** Show a banner explaining why the activity was restarted after setting debug view attributes. */
fun showActivityRestartedInBanner(notificationModel: NotificationModel) {
  val learnMoreAction =
    StatusNotificationAction(LayoutInspectorBundle.message("learn.more")) {
      BrowserUtil.browse(DEBUG_VIEW_ATTRIBUTES_DOCUMENTATION_URL)
    }

  notificationModel.addNotification(
    id = ACTIVITY_RESTART_KEY,
    text = LayoutInspectorBundle.message(ACTIVITY_RESTART_KEY),
    status = EditorNotificationPanel.Status.Info,
    actions = listOf(learnMoreAction, notificationModel.dismissAction),
  )
}
