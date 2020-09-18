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

import com.android.ddmlib.TimeoutRemainder
import com.android.tools.idea.flags.StudioFlags
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService

/**
 * Project level [Service] to support ADB over Wi-Fi pairing
 */
@Service
class PairDevicesUsingWiFiService(private val project: Project) : Disposable {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.getService(PairDevicesUsingWiFiService::class.java)!!
  }

  private val edtExecutor by lazy { EdtExecutorService.getInstance() }

  private val taskExecutor by lazy { AppExecutorUtil.getAppExecutorService() }

  private val timeProvider: TimeoutRemainder.SystemNanoTimeProvider by lazy { TimeoutRemainder.DefaultSystemNanoTime() }

  private val randomProvider by lazy { RandomProvider() }

  private val adbService: AdbServiceWrapper by lazy {
    AdbServiceWrapperImpl(project, timeProvider, MoreExecutors.listeningDecorator(taskExecutor))
  }

  private val devicePairingService : AdbDevicePairingService by lazy {
    AdbDevicePairingServiceImpl(randomProvider, adbService, taskExecutor)
  }

  override fun dispose() {
    // Nothing to do
  }

  fun createPairingDialogController(): AdbDevicePairingController {
    val model = AdbDevicePairingModel()
    val view = AdbDevicePairingViewImpl(project, model)
    return AdbDevicePairingControllerImpl(project, this, edtExecutor, devicePairingService, view)
  }

  val isFeatureEnabled: Boolean
    get() = StudioFlags.ADB_WIRELESS_PAIRING_ENABLED.get()
}
