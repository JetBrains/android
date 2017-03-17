/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.explorer.adbimpl;

import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.android.tools.idea.explorer.fs.FileTransferProgress;
import com.google.common.util.concurrent.ListenableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * An abstract {@link AdbDeviceFileEntry} that goes through another {@link AdbDeviceFileEntry}
 * (see {@link #getForwardedFileEntry()}) for its file operations.
 *
 * <p>This class should be extended by {@link AdbDeviceFileEntry} implementations that override
 * only a subset of the abstract methods, using another instance of {@link AdbDeviceFileEntry}
 * as the default implementation of the non-overridden methods.
 */
public abstract class AdbDeviceForwardingFileEntry extends AdbDeviceFileEntry {

  public AdbDeviceForwardingFileEntry(@NotNull AdbDeviceFileSystem device,
                                      @NotNull AdbFileListingEntry entry,
                                      @Nullable AdbDeviceFileEntry parent) {
    super(device, entry, parent);
  }

  @NotNull
  public abstract AdbDeviceFileEntry getForwardedFileEntry();

  @NotNull
  @Override
  public ListenableFuture<List<DeviceFileEntry>> getEntries() {
    return getForwardedFileEntry().getEntries();
  }

  @NotNull
  @Override
  public ListenableFuture<Void> delete() {
    return getForwardedFileEntry().delete();
  }

  @NotNull
  @Override
  public ListenableFuture<Void> createNewFile(@NotNull String fileName) {
    return getForwardedFileEntry().createNewFile(fileName);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> createNewDirectory(@NotNull String directoryName) {
    return getForwardedFileEntry().createNewDirectory(directoryName);
  }

  @NotNull
  @Override
  public ListenableFuture<Boolean> isSymbolicLinkToDirectory() {
    return getForwardedFileEntry().isSymbolicLinkToDirectory();
  }

  @NotNull
  @Override
  public ListenableFuture<Void> downloadFile(@NotNull Path localPath, @NotNull FileTransferProgress progress) {
    return getForwardedFileEntry().downloadFile(localPath, progress);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> uploadFile(@NotNull Path localPath, @NotNull String fileName, @NotNull FileTransferProgress progress) {
    return getForwardedFileEntry().uploadFile(localPath, fileName, progress);
  }
}