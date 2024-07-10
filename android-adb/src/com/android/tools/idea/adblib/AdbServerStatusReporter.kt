/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.adblib

import com.android.adblib.AdbFeatures
import com.android.adblib.AdbSession
import com.android.adblib.ServerStatus
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.adb.AdbOptionsService
import com.google.wireless.android.sdk.stats.AdbServerStatus
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.launch

/** Retrieve status of ADB Server and upload stats */
class AdbServerStatusReporter(val statusReporter: (ServerStatus) -> Unit) : ProjectActivity {
  @Suppress("unused") constructor() : this(::reportAdbStatus)

  override suspend fun execute(project: Project) {

    val session = AdbLibService.getInstance(project).session
    session.scope.launch {
      val serverStatus = retrieveServerStatus(session)
      statusReporter(serverStatus)
    }
  }

  private suspend fun retrieveServerStatus(session: AdbSession): ServerStatus {
    if (!session.hostServices.hostFeatures().contains(AdbFeatures.SERVER_STATUS)) {
      return ServerStatus()
    }
    return session.hostServices.serverStatus()
  }
}

private fun reportAdbStatus(serverStatus: ServerStatus) {
  UsageTracker.log(
    AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.ADB_SERVER_STATUS)
      .setAdbServerStatus(
        AdbServerStatus.newBuilder()
          .setIsManaged(AdbOptionsService.getInstance().optionsUpdater.useUserManagedAdb())
          .setVersion(serverStatus.version)
          .setIsUsbBackendForced(serverStatus.usbBackendForced)
          .setUsbBackend(
            when (serverStatus.usbBackend) {
              ServerStatus.UsbBackend.UNKNOWN -> AdbServerStatus.USBBackend.TYPE_USB_UNKNOWN
              ServerStatus.UsbBackend.LIBUSB -> AdbServerStatus.USBBackend.TYPE_LIBUSB
              ServerStatus.UsbBackend.NATIVE -> AdbServerStatus.USBBackend.TYPE_NATIVE
            }
          )
          .setMdnsBackend(
            when (serverStatus.mdnsBackEnd) {
              ServerStatus.MdnsBackend.UNKNOWN -> AdbServerStatus.MDNSBackend.TYPE_MDNS_UNKNOWN
              ServerStatus.MdnsBackend.BONJOUR -> AdbServerStatus.MDNSBackend.TYPE_BONJOUR
              ServerStatus.MdnsBackend.OPENSCREEN -> AdbServerStatus.MDNSBackend.TYPE_OPENSCREEN
            }
          )
          .setIsMdnsBackendForced(serverStatus.mdnsBackEndForced)
      )
  )
}
