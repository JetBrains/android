/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.EmulatorAdvFeatures
import com.android.tools.idea.avdmanager.AvdWizardUtils.COLD_BOOT_ONCE_VALUE
import com.android.tools.idea.avdmanager.AvdWizardUtils.USE_COLD_BOOT
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger

import java.awt.event.ActionEvent

/**
 * Launch the emulator now, forcing a cold boot.
 * This does not change the general Cold/Fast selection.
 */
class ColdBootNowAction(avdInfoProvider: AvdUiAction.AvdInfoProvider) :
    AvdUiAction(avdInfoProvider, "Cold Boot Now", "Force one cold boot", AllIcons.Actions.Menu_open) {

  override fun actionPerformed(actionEvent: ActionEvent) {

    val origAvdInfo = avdInfo ?: return
    val origSystemImage = origAvdInfo.systemImage ?: return

    val coldBootProperties = mutableMapOf<String, String>()
    coldBootProperties.putAll(origAvdInfo.properties)
    coldBootProperties.put(USE_COLD_BOOT, COLD_BOOT_ONCE_VALUE)
    val coldBootAvdInfo = AvdInfo(origAvdInfo.name, origAvdInfo.iniFile, origAvdInfo.dataFolderPath,
        origSystemImage, coldBootProperties)
    AvdManagerConnection.getDefaultAvdManagerConnection().startAvd(myAvdInfoProvider.project, coldBootAvdInfo)
  }

  override fun isEnabled(): Boolean {
    return avdInfo != null
        && EmulatorAdvFeatures.emulatorSupportsFastBoot(AndroidSdks.getInstance().tryToChooseSdkHandler(),
                                                        StudioLoggerProgressIndicator(ColdBootNowAction::class.java),
                                                        LogWrapper(Logger.getInstance(AvdManagerConnection::class.java)))
  }
}
