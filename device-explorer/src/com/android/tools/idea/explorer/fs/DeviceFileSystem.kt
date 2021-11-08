/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.explorer.fs

import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.google.common.util.concurrent.ListenableFuture

/**
 * Abstraction over the file system of a single device.
 */
interface DeviceFileSystem {
  /**
   * The device name
   */
  val name: String

  /**
   * A string created by adb to uniquely identify the device by its port number.
   */
  val deviceSerialNumber: String

  /**
   * The device state, as defined by [DeviceState]
   */
  val deviceState: DeviceState

  /**
   * Returns the root [DeviceFileEntry] of the device. The returned directory
   * can be used to traverse the file system recursively.
   */
  val rootDirectory: ListenableFuture<DeviceFileEntry?>

  /**
   * Returns the [DeviceFileEntry] corresponding to the given `path`
   * The path follows the Unix syntax, i.e. starts with `/` and uses `/`
   * as name separator.
   *
   * If the path is not found the future fails with an IllegalArgumentException.
   */
  fun getEntry(path: String): ListenableFuture<DeviceFileEntry?>
}