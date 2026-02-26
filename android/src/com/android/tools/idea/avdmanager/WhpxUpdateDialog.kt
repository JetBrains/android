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
package com.android.tools.idea.avdmanager

import com.android.tools.idea.sdk.AndroidSdks
import com.google.wireless.android.sdk.stats.EmulatorWindowsHypervisorMigrationEvent
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Banner.WARNING_BORDER_COLOR
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/** A dialog that prompts the user to update to the Windows Hypervisor Platform (WHPX). */
class WhpxUpdateDialog(private val project: Project?, private val fromAehd: Boolean) : DialogWrapper(project) {
  var isOperationSuccessful: Boolean = false
    private set

  init {
    if (fromAehd) title = "Update to Windows Hypervisor Platform" else title = "Enable Windows Hypervisor Platform"
    isResizable = false
    init()
  }

  override fun createCenterPanel(): JComponent {
    val panel = JPanel(BorderLayout(0, JBUI.scale(20)))
    panel.border = JBUI.Borders.empty(15)

    // Main Message
    val messageLabel =
      JBLabel(
        "<html>" +
          (if (fromAehd) "Update to " else "Enable ") +
          "Windows Hypervisor Platform (WHPX) to ensure the Android Emulator remains compatible with your system." +
          (if (fromAehd) " Android Emulator hypervisor driver support ends Jan 1, 2027.</html>" else "</html>")
      )
    panel.add(messageLabel, BorderLayout.NORTH)

    val warningBanner = RebootWarningPanel("Changes will not take effect until after a system restart")
    panel.add(warningBanner, BorderLayout.SOUTH)

    panel.preferredSize = JBUI.size(500, 100)
    return panel
  }

  private fun enableWhpxAndReboot(rebootNow: Boolean) {
    val result = enableWhpx(AndroidSdks.getInstance().tryToChooseSdkHandler())
    if (result is WhpxResult.Success) {
      isOperationSuccessful = true
      PropertiesComponent.getInstance().setValue(WHPX_ENABLE_PENDING_RESTART, true)
      if (fromAehd) logHypervisorMigrationEvent(EmulatorWindowsHypervisorMigrationEvent.Action.ENABLE_WHPX_SUCCESS)

      if (rebootNow) {
        notifyAndReboot("You must restart your system to complete Windows Hypervisor Platform update.", project)
      } else {
        balloonNotifyReboot("Restart your system to complete Windows Hypervisor Platform update.", project)
      }
    } else {
      logger<WhpxUpdateDialog>().error("Operation enableWHPX failed: ${result.description}.")
      isOperationSuccessful = false
      if (fromAehd) logHypervisorMigrationEvent(EmulatorWindowsHypervisorMigrationEvent.Action.ENABLE_WHPX_FAILURE)

      when (result) {
        is WhpxResult.AuthorizationRequired -> {
          Messages.showErrorDialog(
            "Enabling WHPX requires authorization. " +
              "Please retry the operation and click \"Yes\" to allow emulator-check.exe to make changes.",
            "Authorization Needed",
          )
        }
        is WhpxResult.EmulatorUpdateNeeded -> {
          Messages.showErrorDialog(
            "Enabling WHPX requires a newer version of the Android Emulator. " +
              "Please update the Android Emulator to version 36.5.7 or higher and retry the operation.",
            "Emulator Update Needed",
          )
        }
        else -> {
          Messages.showErrorDialog(
            "Failed to enable WHPX: ${result.description}. Please consult the IDE log (Help | Show Log).",
            "Operation Failed",
          )
        }
      }
    }
  }

  override fun createActions(): Array<Action> {
    val updateAndRestartAction =
      object : AbstractAction(if (fromAehd) "Update and Restart Now" else "Enable and Restart Now") {
        override fun actionPerformed(e: ActionEvent?) {
          if (fromAehd) logHypervisorMigrationEvent(EmulatorWindowsHypervisorMigrationEvent.Action.WHPX_UPDATE_ACCEPTED)
          close(UPDATE_AND_RESTART_EXIT_CODE)
          enableWhpxAndReboot(true)
        }
      }

    // Set OK button as "Update"
    okAction.putValue(Action.NAME, if (fromAehd) "Update" else "Enable")

    return arrayOf(helpAction, cancelAction, updateAndRestartAction, okAction)
  }

  override fun doHelpAction() {
    BrowserUtil.browse("https://developer.android.com/studio/run/emulator-acceleration#vm-windows")
  }

  /* Action when user clicks "Update" */
  override fun doOKAction() {
    if (fromAehd) logHypervisorMigrationEvent(EmulatorWindowsHypervisorMigrationEvent.Action.WHPX_UPDATE_ACCEPTED)
    super.doOKAction()
    enableWhpxAndReboot(false)
  }

  override fun doCancelAction() {
    if (fromAehd) logHypervisorMigrationEvent(EmulatorWindowsHypervisorMigrationEvent.Action.WHPX_UPDATE_REJECTED)
    super.doCancelAction()
  }

  private inner class RebootWarningPanel(warningText: String) : EditorNotificationPanel(Status.Warning) {
    init {
      myLabel.horizontalAlignment = SwingConstants.CENTER
      myLabel.horizontalTextPosition = SwingConstants.RIGHT
      icon(StudioIcons.Common.WARNING)
      text = warningText
      border = JBUI.Borders.customLine(WARNING_BORDER_COLOR, 1)
    }
  }

  companion object {
    const val UPDATE_AND_RESTART_EXIT_CODE = 101
  }
}
