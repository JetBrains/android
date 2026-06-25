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
package com.android.tools.idea.devicemanagerv2.details

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.tools.idea.devicemanagerv2.PairingStatus
import java.awt.Component
import kotlinx.coroutines.flow.Flow

/** Encapsulates the UI state and confirmation text for unpairing/removing a paired device. */
data class RemovePresentation(val isEnabled: Boolean, val confirmationTitle: String, val confirmationMessage: String)

internal interface PairingDelegate {
  /** Title used for the wizard action in popup menus (localized via DeviceManagerBundle). */
  val addMenuItemTitle: String

  fun getPairingId(handle: DeviceHandle): String?

  /** Returns true if this delegate handles action routing for the given paired device. */
  fun isDelegateForDevice(handle: DeviceHandle, pairedDevice: DeviceHandle): Boolean

  fun isPairDeviceWizardSupported(handle: DeviceHandle): Boolean

  suspend fun showPairDeviceWizard(parent: Component, handle: DeviceHandle)

  /** Returns the UI presentation state for removing a paired device. */
  fun getRemovePresentation(handle: DeviceHandle, pairedDevice: DeviceHandle): RemovePresentation

  /** Executes the removal/unpairing operation. */
  suspend fun removeDevice(parent: Component?, handle: DeviceHandle, pairedDevice: DeviceHandle)

  /** Exposes the reactive pairing status stream for this delegate. */
  fun pairedDevicesFlow(handle: DeviceHandle, devicesFlow: Flow<List<DeviceHandle>>): Flow<Map<String, List<PairingStatus>>>
}
