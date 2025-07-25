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
package com.android.tools.idea.devicemanagerv2

import com.android.adblib.serialNumber
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.intellij.openapi.project.Project

/** Indicates that the device requires user attention. */
internal fun Project.userInvolvementRequired(deviceHandle: DeviceHandle) {
  val connected = deviceHandle.state as? DeviceState.Connected ?: return
  val serialNumber = connected.connectedDevice.serialNumber
  messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).userInvolvementRequired(serialNumber, this)
}
