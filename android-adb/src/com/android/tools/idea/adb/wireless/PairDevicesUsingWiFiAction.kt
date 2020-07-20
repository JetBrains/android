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
package com.android.tools.idea.adb.wireless

import com.android.annotations.concurrency.UiThread
import com.android.ddmlib.TimeoutRemainder
import com.android.tools.idea.flags.StudioFlags
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService

/**
 * The action to show the [AdbDevicePairingDialog] window.
 */
class PairDevicesUsingWiFiAction : AnAction() {
  @UiThread
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = isFeatureEnabled
  }

  @UiThread
  override fun actionPerformed(event: AnActionEvent) {
    if (!isFeatureEnabled) {
      return
    }
    val project = event.project ?: return

    val edtExecutor = EdtExecutorService.getInstance()
    val taskExecutor = AppExecutorUtil.getAppExecutorService()
    val timeProvider = TimeoutRemainder.DefaultSystemNanoTime()
    val randomProvider = RandomProvider()
    val adbService = AdbServiceWrapperImpl(project, timeProvider, MoreExecutors.listeningDecorator(taskExecutor))
    val service = AdbDevicePairingServiceImpl(randomProvider, adbService, taskExecutor)
    val model = AdbDevicePairingModel()
    val view = AdbDevicePairingViewImpl(project, model)
    val controller = AdbDevicePairingControllerImpl(project, edtExecutor, service, view)
    controller.startPairingProcess()
  }

  private val isFeatureEnabled: Boolean
    get() = StudioFlags.ADB_WIRELESS_PAIRING_ENABLED.get()

  companion object {
    const val ID = "Android.AdbDevicePairing"
  }
}
