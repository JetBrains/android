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
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.layoutinspector.pipeline.adb.AbortAdbCommandRunnable
import com.android.tools.idea.layoutinspector.pipeline.adb.executeShellCommand
import com.android.tools.idea.project.AndroidNotification
import com.google.common.html.HtmlEscapers
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Helper class that handles setting debug settings on the device via ADB.
 *
 * These debug settings, when set, tell the system to expose some debug data (e.g. Composables)
 * that it normally would hide.
 */
class DebugViewAttributes(private val adb: AndroidDebugBridge, private val project: Project, private val process: ProcessDescriptor) {

  private var abortDeleteRunnable: AbortAdbCommandRunnable? = null

  /**
   * Enable debug view attributes for the current process.
   *
   * Ignore failures since we are able to inspect the process without debug view attributes.
   */
  @Slow
  fun set() {
    if (abortDeleteRunnable != null) return

    var errorMessage: String
    try {
      if (adb.executeShellCommand(process.device, "settings get global debug_view_attributes") !in listOf("null", "0")) {
        // A return value of "null" or "0" means: "debug_view_attributes" is not currently turned on for all processes on the device.
        return
      }
      val app = adb.executeShellCommand(process.device, "settings get global debug_view_attributes_application_package")
      if (app == process.name) {
        // A return value of process.name means: the debug_view_attributes are already turned on for this process.
        return
      }
      errorMessage =
        adb.executeShellCommand(process.device, "settings put global debug_view_attributes_application_package ${process.name}")

      if (errorMessage.isEmpty()) {
        // A return value of "" means: "debug_view_attributes_application_package" were successfully overridden.

        // Later, we'll try to clear the setting via `clear`, but we also register additional logic to trigger
        // automatically if the user forcefully closes the connection under us (e.g. closing the emulator or
        // pulling their USB cable).
        abortDeleteRunnable = AbortAdbCommandRunnable(
          adb,
          process.device,
          // This works by spawning a subshell which hangs forever (waiting for a read that never gets satisfied)
          // but trips the delete request when that shell is forcefully exited.
          "sh -c 'trap \"settings delete global debug_view_attributes_application_package\" EXIT; read'"
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
                 encoder.escape("\"debug_view_attributes_application_package\"") + "<br/>" +
                 encoder.escape("to: \"${process.name}\"") + "<br/><br/>" +
                 encoder.escape("Error: $errorMessage")
      AndroidNotification.getInstance(project).showBalloon("Could not enable resolution traces",
                                                           text, NotificationType.WARNING)
    }
  }

  /**
   * Disable debug view attributes for the current process that were set when we connected.
   *
   * Return true if the debug view attributes were successfully disabled.
   */
  @Slow
  fun clear() {
    if (abortDeleteRunnable == null) return

    try {
      adb.executeShellCommand(process.device, "settings delete global debug_view_attributes_application_package")
    }
    catch (ex: Exception) {
      abortDeleteRunnable!!.stop()
    }
    finally {
      abortDeleteRunnable = null
    }
  }
}