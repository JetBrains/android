/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.device
import com.android.ddmlib.IDevice
import com.google.common.base.Preconditions
import com.intellij.openapi.project.Project
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Bridges [IDevice] to [ConnectedDevice] using the [project]'s [AdbSession] if provided, or the application-level session.
 *
 * @return the [ConnectedDevice] or `null` if the device was disconnected.
 */
suspend fun IDevice.toConnectedDevice(project: Project?): ConnectedDevice? {
  return if (project != null) {
    toConnectedDeviceWithProject(project)
  } else {
    toConnectedDeviceWithoutProject()
  }
}

private suspend fun IDevice.toConnectedDeviceWithProject(project: Project): ConnectedDevice? {
  val projectAdbSession = AdbLibService.getSession(project)
  projectAdbSession.connectedDevicesTracker.device(serialNumber)?.let {
    return it
  }

  val appAdbSession = AdbLibApplicationService.instance.session
  if (appAdbSession.connectedDevicesTracker.device(serialNumber) != null) {
    // Project-level AdbSession device tracking starts lazily and may lag behind the application-level session.
    // Await initialization and a brief delay before retrying.
    projectAdbSession.connectedDevicesTracker.connectedDevices.first { !it.flowStatus.isStartOfFlow }
    delay(50.milliseconds)
    return projectAdbSession.connectedDevicesTracker.device(serialNumber)
  }
  return null
}

private suspend fun IDevice.toConnectedDeviceWithoutProject(): ConnectedDevice? {
  val appAdbSession = AdbLibApplicationService.instance.session
  Preconditions.checkState(
    !appAdbSession.connectedDevicesTracker.connectedDevices.value.flowStatus.isStartOfFlow,
    "IDevice source is always application AdbSession device tracker",
  )

  return appAdbSession.connectedDevicesTracker.device(serialNumber)
}
