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
package com.android.gmdcodecompletion.managedvirtual

import com.android.gmdcodecompletion.AndroidDeviceInfo
import com.android.gmdcodecompletion.GmdDeviceCatalog

/**
 * This class fetches and stores information from DeviceManager and RepoManager server to obtain
 * the latest device catalog for managed virtual devices
 */
data class ManagedVirtualDeviceCatalog(
  // Map of <device id, per Android device information>
  val devices: HashMap<String, AndroidDeviceInfo> = HashMap(),
  val apiLevels: ArrayList<ApiVersionInfo> = ArrayList()
) : GmdDeviceCatalog() {

  // Stores all required information for emulator images
  data class ApiVersionInfo(
    val apiLevel: Int = 0,
    val imageSource: String = "",
    val apiPreview: String = "",
    // Default is false, On arm machines this value does not have any effect
    val require64Bit: Boolean = false,
  )
}
