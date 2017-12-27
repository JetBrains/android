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
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * An file or directory entry in a {@link DeviceFileSystem}
 */
public interface DeviceFileEntry {
  /**
   * The {@link DeviceFileSystem} this entry belongs to.
   */
  @NotNull
  DeviceFileSystem getFileSystem();

  /**
   * The parent {@link DeviceFileEntry} or <code>null</code> if this is the root directory.
   */
  @Nullable
  DeviceFileEntry getParent();

  /**
   * The name of this entry in its parent directory.
   */
  @NotNull
  String getName();

  /**
   * The full path of the entry in the device file system.
   */
  @NotNull
  String getFullPath();

  /**
   * The list of entries contained in this directory.
   */
  @NotNull
  ListenableFuture<List<DeviceFileEntry>> getEntries();

  /**
   * Deletes the entry from the device file system.
   */
  @NotNull
  ListenableFuture<Void> delete();

  /**
   * Creates a new file "{@code fileName}" in this directory, and returns a future that
   * completes when the file is created. If there is any error creating the file (including the path
   * already exists), the future completes with an exception.
   */
  @NotNull
  ListenableFuture<Void> createNewFile(@NotNull String fileName);

  /**
   * Creates a new directory "{@code directoryName}" in this directory, and returns a future that
   * completes when the directory is created. If there is any error creating the directory
   * (including the path already exists), the future completes with an exception.
   */
  @NotNull
  ListenableFuture<Void> createNewDirectory(@NotNull String directoryName);

  /**
   * Returns {@code true} if the entry is a symbolic link that points to a directory.
   *
   * @see com.android.tools.idea.explorer.adbimpl.AdbFileListing#isDirectoryLink
   */
  @NotNull
  ListenableFuture<Boolean> isSymbolicLinkToDirectory();

  /**
   * Downloads the contents of the {@link DeviceFileEntry} to a local file.
   */
  @NotNull
  ListenableFuture<Void> downloadFile(@NotNull Path localPath,
                                      @NotNull FileTransferProgress progress);

  /**
   * Uploads the contents of a local file to a remote {@link DeviceFileEntry} directory.
   */
  @NotNull
  default ListenableFuture<Void> uploadFile(@NotNull Path localPath,
                                            @NotNull FileTransferProgress progress) {
    return uploadFile(localPath, localPath.getFileName().toString(), progress);
  }

  /**
   * Uploads the contents of a local file to a remote {@link DeviceFileEntry} directory.
   */
  @NotNull
  ListenableFuture<Void> uploadFile(@NotNull Path localPath,
                                    @NotNull String fileName,
                                    @NotNull FileTransferProgress progress);

  /**
   * The permissions associated to this entry, similar to unix permissions.
   */
  @NotNull
  Permissions getPermissions();

  /**
   * The last modification date & time of this entry
   */
  @NotNull
  DateTime getLastModifiedDate();

  /**
   * The size (in bytes) of this entry, or <code>-1</code> if the size is unknown.
   */
  long getSize();

  /**
   * <code>true</code> if the entry is a directory, i.e. it contains entries.
   */
  boolean isDirectory();

  /**
   * <code>true</code> if the entry is a file, i.e. it has content and does not contain entries.
   */
  boolean isFile();

  /**
   * <code>true</code> if the entry is a symbolic link.
   */
  boolean isSymbolicLink();

  /**
   * The link target of the entry if {@link #isSymbolicLink()} is <code>true</code>, <code>null</code> otherwise.
   */
  @Nullable
  String getSymbolicLinkTarget();

  /**
   * Permissions associated to a {@link DeviceFileEntry}.
   */
  interface Permissions {
    @NotNull
    String getText();
  }

  /**
   * Date & time associated to a {@link DeviceFileEntry}.
   */
  interface DateTime {
    @NotNull
    String getText();
  }
}