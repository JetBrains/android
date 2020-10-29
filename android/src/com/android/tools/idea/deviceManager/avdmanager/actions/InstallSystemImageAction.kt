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
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.Messages
import java.awt.event.ActionEvent

/**
 * Action for downloading a missing system image.
 */
class InstallSystemImageAction(avdInfoProvider: AvdInfoProvider) : AvdUiAction(
  avdInfoProvider, "Download", "The corresponding system image is missing", AllIcons.General.BalloonWarning
) {
  private val packagePath: String?
    get() {
      val info = avdInfo ?: return null
      return AvdManagerConnection.getRequiredSystemImagePath(info)
    }

  override fun actionPerformed(actionEvent: ActionEvent) {
    val response = Messages.showOkCancelDialog(
      "The corresponding system image is missing.\n\nDownload it now?", "Download System Image", "Download", "Cancel",
      Messages.getQuestionIcon()
    )
    if (response != Messages.OK) {
      return
    }
    val sdkQuickfixWizard = SdkQuickfixUtils.createDialogForPaths(project, listOf(packagePath!!))
    if (sdkQuickfixWizard != null) {
      sdkQuickfixWizard.show()
      refreshAvds()
    }
  }


  override fun isEnabled(): Boolean = packagePath != null
}