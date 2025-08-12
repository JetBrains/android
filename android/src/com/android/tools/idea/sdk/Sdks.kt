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
package com.android.tools.idea.sdk

import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sdk.wizard.SetupSdkApplicationService
import com.android.tools.idea.welcome.install.SdkComponentInstaller
import com.android.tools.idea.welcome.wizard.FirstRunWizardTracker
import com.google.wireless.android.sdk.stats.SetupWizardEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Attempts to get an AndroidSdkHandler. If it does not have a valid location, asks the user if they
 * want to configure the SDK, and if so, shows a dialog to configure / download the SDK. If this
 * succeeds, the chosen SDK is returned.
 */
suspend fun getOrSetupValidSdk(project: Project?, missingSdkMessage: String): AndroidSdkHandler? {
  val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
  if (sdkHandler.location != null) {
    return sdkHandler
  }
  val wasUpdated =
    withContext(Dispatchers.EDT) {
      var sdkPath: File? = null
      if (
        MessageDialogBuilder.yesNo("Missing SDK", missingSdkMessage)
          .yesText("Configure SDK")
          .noText("Cancel")
          .ask(project)
      ) {
        val useDeprecatedWizard = !StudioFlags.SDK_SETUP_MIGRATED_WIZARD_ENABLED.get()
        SetupSdkApplicationService.instance.showSdkSetupWizard(
          "",
          { sdkPath = it },
          SdkComponentInstaller(),
          FirstRunWizardTracker(SetupWizardEvent.SetupWizardMode.MISSING_SDK, useDeprecatedWizard),
          useDeprecatedWizard,
        )
      }
      sdkPath != null
    }
  return if (wasUpdated)
    AndroidSdks.getInstance().tryToChooseSdkHandler().takeIf { it.location != null }
  else null
}
