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

import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.idea.deviceManager.avdmanager.AvdManagerConnection
import com.android.tools.idea.deviceManager.avdmanager.AvdManagerUtils
import com.google.common.util.concurrent.Futures
import com.intellij.util.concurrency.EdtExecutorService
import icons.StudioIcons
import java.awt.event.ActionEvent

/**
 * Run an Android virtual device
 */
class RunAvdAction(provider: AvdInfoProvider) : AvdUiAction(
  provider, "Run", "Launch this AVD in the emulator", StudioIcons.Avd.RUN) {
  override fun actionPerformed(e: ActionEvent?) {
    val info = avdInfo ?: return

    val project = avdInfoProvider.project
    val deviceFuture = AvdManagerConnection.getDefaultAvdManagerConnection().startAvd(project, info)
    Futures.addCallback(deviceFuture, AvdManagerUtils.newCallback(project), EdtExecutorService.getInstance())

    // TODO(qumeric): open emulator tool window here. Requires small changes in emulator code.
  }

  override fun isEnabled(): Boolean = avdInfo?.status == AvdInfo.AvdStatus.OK
}