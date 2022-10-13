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
package com.android.tools.idea.device.explorer.files.fs

import com.android.annotations.concurrency.UiThread

/**
 * Events fired by an instance of [DeviceFileSystemService].
 */
@UiThread
interface DeviceFileSystemServiceListener {
  /**
   * The internal state of the [DeviceFileSystemService] has changed,
   * meaning all devices and file system are now invalid and should be
   * re-acquired.
   */
  fun serviceRestarted()

  /**
   * A [DeviceFileSystem] has been added to the list of connected devices of the
   * [DeviceFileSystemService]
   */
  fun deviceAdded(device: DeviceFileSystem)

  /**
   * A [DeviceFileSystem] has been removed from the list of connected devices of the
   * [DeviceFileSystemService]
   */
  fun deviceRemoved(device: DeviceFileSystem)

  /**
   * A [DeviceFileSystem]  from the list of connected devices of the
   * [DeviceFileSystemService] has had a state change, for example it
   * has become online after being offline.
   */
  fun deviceUpdated(device: DeviceFileSystem)
}