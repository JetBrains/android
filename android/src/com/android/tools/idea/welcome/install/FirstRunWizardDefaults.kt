/*
 * Copyright (C) 2014 The Android Open Source Project
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

/**
 * The goal is to keep all defaults in one place so it is easier to update them as needed.
 */
@file:JvmName("FirstRunWizardDefaults")

package com.android.tools.idea.welcome.install

import com.android.sdklib.devices.Storage
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.startup.AndroidSdkInitializer
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import java.io.File
import kotlin.math.min

const val HAXM_DOCUMENTATION_URL = "https://github.com/intel/haxm"
const val HAXM_WINDOWS_INSTALL_URL = "https://github.com/intel/haxm/wiki/Installation-Instructions-on-Windows"
const val GVM_WINDOWS_INSTALL_URL = "https://github.com/google/android-emulator-hypervisor-driver"

/**
 * Returns recommended memory allocation given the computer RAM size.
 */
fun getRecommendedHaxmMemory(memorySize: Long): Int {
  val gb = Storage.Unit.GiB.numberOfBytes
  val defaultMemory = when {
    memorySize > 16 * gb -> 4 * gb
    memorySize > 4 * gb -> 2 * gb
    memorySize > 2 * gb -> gb
    else -> min(memorySize, gb / 2)
  }
  return (defaultMemory / UI_UNITS.numberOfBytes).toInt().coerceAtMost(getMaxHaxmMemory(memorySize))
}

/**
 * Returns maximum memory allocation given the computer RAM size.
 */
fun getMaxHaxmMemory(memorySize: Long): Int {
  val gb = Storage.Unit.GiB.numberOfBytes
  val maxMemory = (memorySize - 2 * gb).coerceAtLeast(memorySize / 2)
  return (maxMemory / UI_UNITS.numberOfBytes).toInt()
}


/**
 * Returns initial SDK location. That will be the SDK location from the installer handoff file in the handoff case,
 * SDK location location from the preference if set or platform-dependant default path.
 */
fun getInitialSdkLocation(mode: FirstRunWizardMode): File =
  mode.sdkLocation
  ?: AndroidSdks.getInstance().allAndroidSdks.firstOrNull()?.homeDirectory?.toIoFile()
  ?: AndroidSdkInitializer.getAndroidSdkPathOrDefault()
