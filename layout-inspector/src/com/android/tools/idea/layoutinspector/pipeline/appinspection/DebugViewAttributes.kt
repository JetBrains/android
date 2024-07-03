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

import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.shellAsText
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.StatusNotificationAction
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotificationPanel
import io.ktor.utils.io.CancellationException

private const val PER_DEVICE_SETTING = "debug_view_attributes"

// A return value of "null" or "0" means: "debug_view_attributes" is not currently turned on.
private val debugViewAttributesDisabledConstants = setOf("null", "0")

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

private sealed class AdbCommandResult {
  object Success : AdbCommandResult()

  data class Failure(val message: String) : AdbCommandResult()
}

/** The result of trying to set the flag */
sealed class SetFlagResult {
  /** The flag is set. [previouslySet] is true, if the flag was already set. */
  data class Set(val previouslySet: Boolean) : SetFlagResult()

  data class Failure(val error: String?) : SetFlagResult()

  object Cancelled : SetFlagResult()
}

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
class DebugViewAttributes(
  private val project: Project,
  private val adbSession: AdbSession = AdbLibService.getSession(project),
) {

  /** Enable debug view attributes for the current process. */
  suspend fun set(device: DeviceDescriptor): SetFlagResult {
    val putCommand = Command.Put(PER_DEVICE_SETTING, "1")

    return try {
      val adb = adbSession.deviceServices
      if (!shouldSetFlag(adb, device)) {
        return SetFlagResult.Set(true)
      }

      when (val commandResult = executePut(adb, device, putCommand)) {
        AdbCommandResult.Success -> SetFlagResult.Set(false)
        is AdbCommandResult.Failure -> SetFlagResult.Failure(commandResult.message)
      }
    } catch (cancellation: CancellationException) {
      SetFlagResult.Cancelled
    } catch (t: Throwable) {
      Logger.getInstance(DebugViewAttributes::class.java).warn(t)
      SetFlagResult.Failure(t.message ?: t.javaClass.simpleName)
    }
  }

  private suspend fun shouldSetFlag(adb: AdbDeviceServices, device: DeviceDescriptor): Boolean {
    return !isPerDeviceSettingOn(adb, device)
  }

  /**
   * Turns on the flag.
   *
   * @return empty string in case of success, or error message otherwise.
   */
  private suspend fun executePut(
    adb: AdbDeviceServices,
    device: DeviceDescriptor,
    putCommand: Command.Put,
  ): AdbCommandResult {
    val output = adb.shellAsText(DeviceSelector.fromSerialNumber(device.serial), putCommand.get())
    return if (output.stdout.isBlank() && output.stderr.isBlank()) {
      // Empty output means "debug_view_attributes" was set successfully.
      AdbCommandResult.Success
    } else {
      AdbCommandResult.Failure(output.stderr)
    }
  }

  private suspend fun isPerDeviceSettingOn(
    adb: AdbDeviceServices,
    device: DeviceDescriptor,
  ): Boolean {
    val output =
      adb.shellAsText(
        DeviceSelector.fromSerialNumber(device.serial),
        Command.Get(PER_DEVICE_SETTING).get(),
      )

    return output.stdout.trim() !in debugViewAttributesDisabledConstants
  }
}

private const val ACTIVITY_RESTART_KEY = "activity.restart"
private const val FAILED_TO_ENABLE_VIEW_ATTRIBUTES_INSPECTION =
  "failed.to.enable.view.attributes.inspection"
private const val DEBUG_VIEW_ATTRIBUTES_DOCUMENTATION_URL =
  "https://d.android.com/r/studio-ui/layout-inspector-activity-restart"

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

/** Show a banner explaining why the activity was restarted after setting debug view attributes. */
fun showUnableToSetDebugViewAttributesBanner(notificationModel: NotificationModel) {
  val learnMoreAction =
    StatusNotificationAction(LayoutInspectorBundle.message("learn.more")) {
      BrowserUtil.browse(DEBUG_VIEW_ATTRIBUTES_DOCUMENTATION_URL)
    }

  notificationModel.addNotification(
    id = FAILED_TO_ENABLE_VIEW_ATTRIBUTES_INSPECTION,
    text = LayoutInspectorBundle.message(FAILED_TO_ENABLE_VIEW_ATTRIBUTES_INSPECTION),
    status = EditorNotificationPanel.Status.Error,
    actions = listOf(learnMoreAction, notificationModel.dismissAction),
  )
}
