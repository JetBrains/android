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

class WipeAvdDataAction(avdInfoProvider: AvdInfoProvider) : AvdUiAction(
  avdInfoProvider, "Wipe Data", "Wipe the user data of this AVD", AllIcons.Actions.Edit
) {
  override fun actionPerformed(e: ActionEvent) {
    val connection = AvdManagerConnection.getDefaultAvdManagerConnection()
    val info = avdInfo ?: return
    if (connection.isAvdRunning(info)) {
      Messages.showErrorDialog(
        avdInfoProvider.avdProviderComponent,
        "The selected AVD is currently running in the Emulator. Please exit the emulator instance and try wiping again.",
        "Cannot Wipe A Running AVD"
      )
      return
    }
    val result = Messages.showYesNoDialog(
      avdInfoProvider.avdProviderComponent,
      "Do you really want to wipe user files from AVD ${info.name}?",
      "Confirm Data Wipe",
      AllIcons.General.QuestionDialog
    )
    if (result == Messages.YES) {
      connection.wipeUserData(info)
      refreshAvds()
    }
  }

  override fun isEnabled(): Boolean = avdInfo != null
}