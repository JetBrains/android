/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files.fs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the file system of a single device.
 */
interface DeviceFileSystem {
  /**
   * The device name. Not for display; only used to construct a directory to download files into.
   */
  val name: String

  /**
   * A string created by adb to uniquely identify the device by its port number.
   */
  val deviceSerialNumber: String

  /**
   * The device state, as defined by [DeviceState]
   */
  val deviceStateFlow: StateFlow<DeviceState>

  val deviceState: DeviceState
    get() = deviceStateFlow.value

  val scope: CoroutineScope

  /**
   * Returns the root [DeviceFileEntry] of the device. The returned directory
   * can be used to traverse the file system recursively.
   */
  suspend fun rootDirectory(): DeviceFileEntry

  /**
   * Returns the [DeviceFileEntry] corresponding to the given `path`
   * The path follows the Unix syntax, i.e. starts with `/` and uses `/`
   * as name separator.
   *
   * @throws IllegalArgumentException if the path is not found
   */
  suspend fun getEntry(path: String): DeviceFileEntry
}