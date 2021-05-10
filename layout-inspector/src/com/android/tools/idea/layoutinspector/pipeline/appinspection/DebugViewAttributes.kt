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
import com.android.tools.idea.layoutinspector.pipeline.adb.executeShellCommand
import com.android.tools.idea.project.AndroidNotification
import com.google.common.html.HtmlEscapers
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.panel
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JLabel

/**
 * Helper class that handles setting debug settings on the device via ADB.
 *
 * These debug settings, when set, tell the system to expose some debug data (e.g. Composables)
 * that it normally would hide.
 */
class DebugViewAttributes(private val adb: AndroidDebugBridge, private val project: Project, private val process: ProcessDescriptor) {

  private class OkButtonAction : AbstractAction("OK") {
    init {
      putValue(DialogWrapper.DEFAULT_ACTION, true)
    }

    override fun actionPerformed(event: ActionEvent) {
      val wrapper = DialogWrapper.findInstance(event.source as? Component)
      wrapper?.close(DialogWrapper.OK_EXIT_CODE)
    }
  }

  private var debugAttributesSet = false

  /**
   * Enable debug view attributes for the current process.
   *
   * Ignore failures since we are able to inspect the process without debug view attributes.
   */
  @Slow
  fun set() {
    if (debugAttributesSet) return

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
        debugAttributesSet = true
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
    if (!debugAttributesSet) return

    debugAttributesSet = false
    try {
      adb.executeShellCommand(process.device, "settings delete global debug_view_attributes_application_package")
    }
    catch (ex: Exception) {
      reportUnableToResetGlobalSettings()
    }
  }

  private fun reportUnableToResetGlobalSettings() {
    ApplicationManager.getApplication().invokeLater {
      val message = """Could not reset the state on your device.

                       To fix this, manually run this command:
                       $ adb shell settings delete global debug_view_attributes_application_package
                       """.trimIndent()

      val dialog = dialog(
        title = "Unable to connect to your device",
        panel = panel {
          row(JLabel(UIUtil.getErrorIcon())) {}
          noteRow(message)
        },
        createActions = { listOf(OkButtonAction()) },
        project = project)
      dialog.show()
    }
  }
}