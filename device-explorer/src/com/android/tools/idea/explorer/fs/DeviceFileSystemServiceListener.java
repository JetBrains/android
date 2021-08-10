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
package com.android.tools.idea.explorer.fs;

import org.jetbrains.annotations.NotNull;

/**
 * Events fired by an instance of {@link DeviceFileSystemService}.
 */
public interface DeviceFileSystemServiceListener {
  /**
   * The internal state of the {@link DeviceFileSystemService} has changed,
   * meaning all devices and file system are now invalid and should be
   * re-acquired.
   */
  void serviceRestarted();

  /**
   * A {@link DeviceFileSystem} has been added to the list of connected devices of the
   * {@link DeviceFileSystemService}
   */
  void deviceAdded(@NotNull DeviceFileSystem device);

  /**
   * A {@link DeviceFileSystem} has been removed from the list of connected devices of the
   * {@link DeviceFileSystemService}
   */
  void deviceRemoved(@NotNull DeviceFileSystem device);

  /**
   * A {@link DeviceFileSystem}  from the list of connected devices of the
   * {@link DeviceFileSystemService} has had a state change, for example it
   * has become online after being offline.
   */
  void deviceUpdated(@NotNull DeviceFileSystem device);
}
