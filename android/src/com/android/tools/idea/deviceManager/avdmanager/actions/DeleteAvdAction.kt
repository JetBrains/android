/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.avdmanager.actions

import com.android.tools.idea.deviceManager.avdmanager.AvdManagerConnection
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.Messages
import java.awt.event.ActionEvent

/**
 * Delete an AVD with confirmation.
 */
class DeleteAvdAction(avdInfoProvider: AvdInfoProvider) : AvdUiAction(
  avdInfoProvider, "Delete", "Delete this AVD", AllIcons.Actions.Cancel
) {
  override fun actionPerformed(e: ActionEvent) {
    val connection = AvdManagerConnection.getDefaultAvdManagerConnection()
    val info = avdInfo ?: return
    if (connection.isAvdRunning(info)) {
      Messages.showErrorDialog(
        avdInfoProvider.avdProviderComponent,
        "The selected AVD is currently running in the Emulator. Please exit the emulator instance and try deleting again.",
        "Cannot Delete A Running AVD"
      )
      return
    }
    val result = Messages.showYesNoDialog(
      avdInfoProvider.avdProviderComponent,
      "Do you really want to delete AVD ${info.name}?",
      "Confirm Deletion",
      AllIcons.General.QuestionDialog
    )
    if (result == Messages.YES) {
      if (!connection.deleteAvd(info)) {
        Messages.showErrorDialog(
          avdInfoProvider.avdProviderComponent,
          "An error occurred while deleting the AVD. See idea.log for details.",
          "Error Deleting AVD"
        )
      } else {
        // TODO(qumeric): make it a toast. P2: Make it undoable.
        Messages.showInfoMessage(
          avdInfoProvider.avdProviderComponent,
          "${info.name} removed from device manager",
          "Deleted ${info.name} virtual device"
        )
      }
      refreshAvds()
    }
  }

  override fun isEnabled(): Boolean = true
}