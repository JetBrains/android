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

import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.google.common.util.concurrent.Futures
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.EdtExecutorService
import java.awt.event.ActionEvent

/**
 * Launch the emulator now, forcing a cold boot.
 * This does not change the general Cold/Fast selection.
 */
class ColdBootNowAction(avdInfoProvider: AvdUiAction.AvdInfoProvider) :
    AvdUiAction(avdInfoProvider, "Cold Boot Now", "Force one cold boot", AllIcons.Actions.MenuOpen) {

  override fun actionPerformed(actionEvent: ActionEvent) {
    val project = myAvdInfoProvider.project
    val avd = avdInfo ?: return
    val deviceFuture = AvdManagerConnection.getDefaultAvdManagerConnection().startAvdWithColdBoot(project, avd)
    Futures.addCallback(deviceFuture, AvdManagerUtils.newCallback(project), EdtExecutorService.getInstance())
  }

  override fun isEnabled(): Boolean {
    return avdInfo != null
        && EmulatorAdvFeatures.emulatorSupportsFastBoot(AndroidSdks.getInstance().tryToChooseSdkHandler(),
                                                        StudioLoggerProgressIndicator(ColdBootNowAction::class.java),
                                                        LogWrapper(Logger.getInstance(AvdManagerConnection::class.java)))
  }
}
