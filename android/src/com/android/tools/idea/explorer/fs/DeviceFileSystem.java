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

import com.google.common.util.concurrent.ListenableFuture;
import org.jetbrains.annotations.NotNull;

/**
 * Abstraction over the file system of a single device.
 */
public interface DeviceFileSystem {
  /**
   * The device name
   */
  @NotNull
  String getName();

  /**
   * The device state, as defined by {@link DeviceState}
   */
  @NotNull
  DeviceState getDeviceState();

  /**
   * Returns the root {@link DeviceFileEntry} of the device. The returned directory
   * can be used to traverse the file system recursively.
   */
  @NotNull
  ListenableFuture<DeviceFileEntry> getRootDirectory();

  /**
   * Returns the {@link DeviceFileEntry} corresponding to the given <code>path</code>
   * The path follows the Unix syntax, i.e. starts with <code>/</code> and uses <code>/</code>
   * as name separator.
   */
  @NotNull
  ListenableFuture<DeviceFileEntry> getEntry(@NotNull String path);
}