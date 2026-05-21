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

import com.android.adblib.ServerStatus
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.adb.AdbOptionsService
import com.android.tools.idea.adb.AdbServerStatusRetriever
import com.android.tools.idea.isAndroidEnvironment
import com.google.wireless.android.sdk.stats.AdbServerStatus
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/** Retrieve status of ADB Server and upload stats */
class AdbServerStatusReporter(val statusReporter: (ServerStatus) -> Unit) : ProjectActivity {
  @Suppress("unused") constructor() : this(::reportAdbStatus)

  override suspend fun execute(project: Project) {
    if (!isAndroidEnvironment(project)) {
      return
    }
    val serverStatus =
      AdbServerStatusRetriever.getInstance(project).serverStatus.filterNotNull().first()
    statusReporter(serverStatus)
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
          .setUsbBackend(serverStatus.usbBackend.toProto())
          .setMdnsBackend(serverStatus.mdnsBackEnd.toProto())
          .setIsMdnsBackendForced(serverStatus.mdnsBackEndForced)
      )
  )
}

private fun ServerStatus.UsbBackend.toProto(): AdbServerStatus.USBBackend =
  when (this) {
    ServerStatus.UsbBackend.UNKNOWN -> AdbServerStatus.USBBackend.TYPE_USB_UNKNOWN
    ServerStatus.UsbBackend.LIBUSB -> AdbServerStatus.USBBackend.TYPE_LIBUSB
    ServerStatus.UsbBackend.NATIVE -> AdbServerStatus.USBBackend.TYPE_NATIVE
    ServerStatus.UsbBackend.USB_DISABLED -> AdbServerStatus.USBBackend.TYPE_USB_DISABLED
  }

private fun ServerStatus.MdnsBackend.toProto(): AdbServerStatus.MDNSBackend =
  when (this) {
    ServerStatus.MdnsBackend.UNKNOWN -> AdbServerStatus.MDNSBackend.TYPE_MDNS_UNKNOWN
    ServerStatus.MdnsBackend.BONJOUR -> AdbServerStatus.MDNSBackend.TYPE_BONJOUR
    ServerStatus.MdnsBackend.OPENSCREEN -> AdbServerStatus.MDNSBackend.TYPE_OPENSCREEN
    ServerStatus.MdnsBackend.LIBADBMDNS -> AdbServerStatus.MDNSBackend.TYPE_LIBADBMDNS
    ServerStatus.MdnsBackend.MDNS_DISABLED -> AdbServerStatus.MDNSBackend.TYPE_MDNS_DISABLED
  }
