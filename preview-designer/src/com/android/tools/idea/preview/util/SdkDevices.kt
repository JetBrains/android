/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.preview.util

import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.module.Module
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.sdk.StudioAndroidSdkData

/**
 * Returns the [Device]s present in the Sdk.
 *
 * @see DeviceManager
 */
fun getSdkDevices(module: Module): List<Device> {
  return AndroidFacet.getInstance(module)?.let { facet ->
    StudioAndroidSdkData.getSdkData(facet)
      ?.deviceManager
      ?.getDevices(DeviceManager.ALL_DEVICES)
      ?.toList()
  } ?: emptyList()
}

/** Key to obtain the list of all available devices from the device manager. */
val AvailableDevicesKey = DataKey.create<Collection<Device>>("preview.available.devices")
