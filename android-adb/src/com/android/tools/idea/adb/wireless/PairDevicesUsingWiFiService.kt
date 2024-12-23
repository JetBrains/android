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
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/** Project level [Service] to support ADB over Wi-Fi pairing */
@Service
class PairDevicesUsingWiFiService(private val project: Project) : Disposable {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) =
      project.getService(PairDevicesUsingWiFiService::class.java)!!
  }

  private val timeProvider: TimeoutRemainder.SystemNanoTimeProvider by lazy {
    TimeoutRemainder.DefaultSystemNanoTime()
  }

  private val randomProvider by lazy { RandomProvider() }

  private val adbService: AdbServiceWrapper by lazy {
    if (StudioFlags.ADBLIB_MIGRATION_WIFI_PAIRING.get()) {
      AdbServiceWrapperAdbLibImpl(project)
    } else {
      AdbServiceWrapperImpl(project, timeProvider)
    }
  }

  private val devicePairingService: WiFiPairingService by lazy {
    WiFiPairingServiceImpl(randomProvider, adbService)
  }

  private val notificationService: WiFiPairingNotificationService by lazy {
    WiFiPairingNotificationServiceImpl(project)
  }

  override fun dispose() {
    // Nothing to do
  }

  fun createPairingDialogController(): WiFiPairingController {
    val model = WiFiPairingModel()
    val view =
      WiFiPairingViewImpl(project, notificationService, model, WiFiPairingHyperlinkListener)
    return WiFiPairingControllerImpl(project, this, devicePairingService, notificationService, view)
  }

  val isFeatureEnabled: Boolean
    get() = true
}
