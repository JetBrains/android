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
 * A {@link AdbDeviceFileEntry} that goes through the file system mounting points (see {@link AdbDeviceFileSystem#resolveMountPoint})
 * for its file operations.
 */
public class AdbDeviceDefaultFileEntry extends AdbDeviceFileEntry {

  public AdbDeviceDefaultFileEntry(@NotNull AdbDeviceFileSystem device,
                                   @NotNull AdbFileListingEntry entry,
                                   @Nullable AdbDeviceFileEntry parent) {
    super(device, entry, parent);
  }

  @NotNull
  @Override
  public ListenableFuture<List<DeviceFileEntry>> getEntries() {
    ListenableFuture<AdbDeviceFileEntry> futureMountPoint = myDevice.resolveMountPoint(this);
    return myDevice.getTaskExecutor().transformAsync(futureMountPoint, AdbDeviceFileEntry::getEntries);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> delete() {
    ListenableFuture<AdbDeviceFileEntry> futureMountPoint = myDevice.resolveMountPoint(this);
    return myDevice.getTaskExecutor().transformAsync(futureMountPoint, AdbDeviceFileEntry::delete);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> createNewFile(@NotNull String fileName) {
    ListenableFuture<AdbDeviceFileEntry> futureMountPoint = myDevice.resolveMountPoint(this);
    return myDevice.getTaskExecutor().transformAsync(futureMountPoint, x -> {
      assert x != null;
      return x.createNewFile(fileName);
    });
  }

  @NotNull
  @Override
  public ListenableFuture<Void> createNewDirectory(@NotNull String directoryName) {
    ListenableFuture<AdbDeviceFileEntry> futureMountPoint = myDevice.resolveMountPoint(this);
    return myDevice.getTaskExecutor().transformAsync(futureMountPoint, x -> {
      assert x != null;
      return x.createNewDirectory(directoryName);
    });
  }

  @NotNull
  @Override
  public ListenableFuture<Boolean> isSymbolicLinkToDirectory() {
    ListenableFuture<AdbDeviceFileEntry> futureMountPoint = myDevice.resolveMountPoint(this);
    return myDevice.getTaskExecutor().transformAsync(futureMountPoint, AdbDeviceFileEntry::isSymbolicLinkToDirectory);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> downloadFile(@NotNull Path localPath, @NotNull FileTransferProgress progress) {
    ListenableFuture<AdbDeviceFileEntry> futureMountPoint = myDevice.resolveMountPoint(this);
    return myDevice.getTaskExecutor().transformAsync(futureMountPoint, x -> {
      assert x != null;
      return x.downloadFile(localPath, progress);
    });
  }

  @NotNull
  @Override
  public ListenableFuture<Void> uploadFile(@NotNull Path localPath, @NotNull String fileName, @NotNull FileTransferProgress progress) {
    ListenableFuture<AdbDeviceFileEntry> futureMountPoint = myDevice.resolveMountPoint(this);
    return myDevice.getTaskExecutor().transformAsync(futureMountPoint, x -> {
      assert x != null;
      return x.uploadFile(localPath, fileName, progress);
    });
  }
}
