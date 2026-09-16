/*
 * Copyright (C) 2025 The Android Open Source Project
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
@file:JvmName("EmulatorWhpxUtils")

package com.android.tools.idea.avdmanager

import com.android.sdklib.internal.avd.getEmulatorPackage
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.EmulatorWindowsHypervisorMigrationEvent
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.io.FileUtilRt

const val WHPX_ENABLE_PENDING_RESTART = "whpx_enable_pending_restart"

/** Represents the result of a WHPX configuration operation. */
sealed class WhpxResult(val description: String) {
  object Success : WhpxResult("Operation successful")

  object EmulatorUpdateNeeded : WhpxResult("Emulator update needed")

  object EmulatorNotFound : WhpxResult("Emulator or emulator-check binary not found")

  object ExecutionFailed : WhpxResult("Execution failed")

  object AuthorizationRequired : WhpxResult("Authorization required")

  /** Represents any other code returned by the OS runtime. */
  data class Unknown(val code: Int) : WhpxResult("Unknown error (code: $code)")

  companion object {
    fun fromExitCode(value: Int): WhpxResult =
      when (value) {
        0 -> Success
        100 -> EmulatorUpdateNeeded
        1223 -> AuthorizationRequired
        else -> Unknown(value)
      }
  }
}

fun enableWhpx(sdk: AndroidSdkHandler): WhpxResult {
  return switchWhpx(sdk, true)
}

fun disableWHPX(sdk: AndroidSdkHandler): WhpxResult {
  return switchWhpx(sdk, false)
}

fun switchWhpx(sdk: AndroidSdkHandler, enable: Boolean): WhpxResult {
  val emulator = sdk.getEmulatorPackage(progressIndicator)
  val emulatorBinary = emulator?.emulatorBinary ?: return WhpxResult.EmulatorNotFound

  val commandLine = ElevatedCommandLine()
  commandLine.setWorkDirectory(emulator.location.toString())
  val checkBinary = emulator.emulatorCheckBinary?: return WhpxResult.EmulatorNotFound
  commandLine.exePath = checkBinary.toString()
  commandLine.addParameter(if(enable) "enable-whpx" else "disable-whpx")

  return try {
    WhpxResult.fromExitCode(CapturingAnsiEscapesAwareProcessHandler(commandLine).runProcess().exitCode)
  } catch (e: ExecutionException) {
    logger<EmulatorWhpxUtil>().warn(e)
    WhpxResult.ExecutionFailed
  }
}

fun rebootCommand() {
  val reboot = GeneralCommandLine()
  reboot.setExePath("shutdown")
  reboot.addParameters("/r", "/t", "0") // restart immediately (after 0 second)
  reboot.setWorkDirectory(FileUtilRt.getTempDirectory())
  try {
    CapturingAnsiEscapesAwareProcessHandler(reboot).runProcess()
  } catch (e: ExecutionException) {
    logger<EmulatorWhpxUtil>().warn("Failed to reboot", e)
  }
}

fun notifyAndReboot(prompt: String, project: Project? = null) {
  if (
    MessageDialogBuilder.Message("System Restart", prompt)
      .buttons("Cancel", "Reboot Now")
      .defaultButton("Reboot Now")
      .show(project, null) == "Cancel"
  ) {
    return
  } else rebootCommand()
}

fun balloonNotifyReboot(prompt: String, project: Project? = null) {
  val notification =
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Android Notification Group")
      .createNotification(prompt, NotificationType.INFORMATION)

  notification.addAction(NotificationAction.createSimple("Restart Now") { rebootCommand() })
  notification.notify(project)
}

fun showWhpxUpdateDialog(project: Project?, fromAehd: Boolean): Boolean {
  val dialog = WhpxUpdateDialog(project, fromAehd)
  if (fromAehd) logHypervisorMigrationEvent(EmulatorWindowsHypervisorMigrationEvent.Action.WHPX_UPDATE_DIALOG_SHOW)
  dialog.show()

  return dialog.isOperationSuccessful
}

fun logHypervisorMigrationEvent(action: EmulatorWindowsHypervisorMigrationEvent.Action) {
  UsageTracker.log(
    AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.EMULATOR_WINDOWS_HYPERVISOR_MIGRATION_EVENT)
      .setEmulatorWindowsHypervisorMigrationEvent(EmulatorWindowsHypervisorMigrationEvent.newBuilder().setAction(action))
  )
}

private object EmulatorWhpxUtil

private val progressIndicator = StudioLoggerProgressIndicator(EmulatorWhpxUtil::class.java)
