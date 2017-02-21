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

import java.nio.file.Path;

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

  /**
   * Downloads the contents of the {@link DeviceFileEntry} to a local
   * file.
   */
  @NotNull
  ListenableFuture<Void> downloadFile(@NotNull DeviceFileEntry entry, @NotNull Path localPath, @NotNull FileTransferProgress progress);

  /**
   * Creates a new file "{@code fileName}" in the given {@code parentEntry}, and returns a future that
   * completes when the file is created. If there is any error creating the file (including the path
   * already exists), the future completes with an exception.
   */
  @NotNull
  ListenableFuture<Void> createNewFile(@NotNull DeviceFileEntry parentEntry, @NotNull String fileName);

  /**
   * Creates a new directory "{@code directoryName}" in the given {@code parentEntry}, and returns a
   * future that completes when the directory is created. If there is any error creating the directory
   * (including the path already exists), the future completes with an exception.
   */
  @NotNull
  ListenableFuture<Void> createNewDirectory(@NotNull DeviceFileEntry parentEntry, @NotNull String directoryName);
}
