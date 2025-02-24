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
@file:JvmName("PhysicalDisplayIdResolver")
package com.android.tools.idea.ui.util

import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.shellAsText

/**
 * Returns the physical ID corresponding to a logical display. Throws an exception in case of
 * a device communication error or if the given logical display ID is invalid. It is important to
 * remember that the logical-to-physical display mapping is dynamic and may change over time.
 */
suspend fun AdbSession.getPhysicalDisplayId(device: DeviceSelector, displayId: Int): Long {
  val dumpsysOutput = deviceServices.shellAsText(device, "dumpsys display").stdout
  return getPhysicalDisplayIdFromDumpsysOutput(dumpsysOutput, displayId)
}

/**
 * Returns the physical ID corresponding to a logical display by parsing output of `adb dumpsys display`.
 * Throws an exception if the given logical display ID is not fund in the dumpsys output.
 */
fun getPhysicalDisplayIdFromDumpsysOutput(dumpsysOutput: String, displayId: Int): Long {
  val regex = Regex("\\s+mCurrentLayerStack=$displayId[\\s\\S]*?\\s+mPhysicalDisplayId=(\\d+)\n", RegexOption.MULTILINE)
  val match = regex.find(dumpsysOutput) ?: throw RuntimeException("Unable to find physical id for logical display $displayId")
  return match.groupValues[1].toLong()
}
