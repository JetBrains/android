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

import com.android.adblib.ServerStatus
import com.android.annotations.concurrency.AnyThread
import com.android.ddmlib.IDevice
import com.intellij.openapi.project.Project

/**
 * Amount of time to wait for device to come online after pairing. This is the default timeout for
 * [AdbServiceWrapper.waitForOnlineDevice]
 */
internal const val ADB_DEVICE_CONNECT_MILLIS = 120_000L

/** List of ADB services required for ADB over Wi-FI pairing */
interface AdbServiceWrapper {
  /**
   * Executes a command using the ADB executable configured for the [Project]
   *
   * [args] List of argument to pass to ADB executable [stdin] String to pass as "stdin" to the ADB
   * executable, to be used if there is interaction required
   */
  @AnyThread suspend fun executeCommand(args: List<String>, stdin: String = ""): AdbCommandResult

  /**
   * Returns when the device corresponding to [pairingResult] is visible as a `connected` device to
   * the underlying ADB implementation.
   *
   * Throws a [AdbCommandException] in case the device does not show up as online within a
   * "reasonable" timeout (chosen by the implementation).
   */
  @AnyThread suspend fun waitForOnlineDevice(pairingResult: PairingResult): AdbOnlineDevice

  /** Retrieve ADB server-status */
  @AnyThread suspend fun getServerStatus(): ServerStatus
}

/** Snapshot of an [IDevice] when the corresponding device was online */
data class AdbOnlineDevice(val id: String, val properties: Map<String, String>) {
  val displayString: String
    get() {
      // TODO: Use DeviceNameRenderer class when it has moved out of android.core module
      val manufacturer = properties[IDevice.PROP_DEVICE_MANUFACTURER] ?: ""
      val model = properties[IDevice.PROP_DEVICE_MODEL] ?: id
      return if (model.startsWith(manufacturer, true)) model else "$manufacturer $model"
    }
}

/** Result of executing an ADB command using [AdbServiceWrapper.executeCommand] */
data class AdbCommandResult(val errorCode: Int, val stdout: List<String>, val stderr: List<String>)
