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

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.android.tools.idea.explorer.fs.FileTransferProgress;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A {@link AdbDeviceFileEntry} that goes directly to the remote file system for its file operations.
 *
 * <p>The (optional) {@code runAs} parameter is directly passed to the {@link AdbFileListing} and
 * {@link AdbFileOperations} methods to use as a {@code "run-as package-name" prefix}.
 */
public class AdbDeviceDirectFileEntry extends AdbDeviceFileEntry {
  @Nullable private final String myRunAs;

  public AdbDeviceDirectFileEntry(@NotNull AdbDeviceFileSystem device,
                                  @NotNull AdbFileListingEntry entry,
                                  @Nullable AdbDeviceFileEntry parent,
                                  @Nullable String runAs) {
    super(device, entry, parent);
    myRunAs = runAs;
  }

  @NotNull
  @Override
  public ListenableFuture<List<DeviceFileEntry>> getEntries() {
    ListenableFuture<List<AdbFileListingEntry>> children = myDevice.getAdbFileListing().getChildrenRunAs(myEntry, myRunAs);
    return myDevice.getTaskExecutor().transform(children, result -> {
      assert result != null;
      return result.stream()
        .map(listingEntry -> new AdbDeviceDefaultFileEntry(myDevice, listingEntry, this))
        .collect(Collectors.toList());
    });
  }

  @NotNull
  @Override
  public ListenableFuture<Void> delete() {
    if (isDirectory()) {
      return myDevice.getAdbFileOperations().deleteRecursiveRunAs(getFullPath(), myRunAs);
    }
    else {
      return myDevice.getAdbFileOperations().deleteFileRunAs(getFullPath(), myRunAs);
    }
  }

  @NotNull
  @Override
  public ListenableFuture<Void> createNewFile(@NotNull String fileName) {
    return myDevice.getAdbFileOperations().createNewFileRunAs(getFullPath(), fileName, myRunAs);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> createNewDirectory(@NotNull String directoryName) {
    return myDevice.getAdbFileOperations().createNewDirectoryRunAs(getFullPath(), directoryName, myRunAs);
  }

  @NotNull
  @Override
  public ListenableFuture<Boolean> isSymbolicLinkToDirectory() {
    return myDevice.getAdbFileListing().isDirectoryLinkRunAs(myEntry, myRunAs);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> downloadFile(@NotNull Path localPath,
                                             @NotNull FileTransferProgress progress) {
    // Note: First try to download the file as the default user. If we get a permission error,
    //       download the file via a temp. directory using the "su 0" user.
    ListenableFuture<Void> futureDownload = myDevice.getAdbFileTransfer().downloadFile(this.myEntry, localPath, progress);
    return myDevice.getTaskExecutor().catchingAsync(futureDownload, SyncException.class, syncError -> {
      assert syncError != null;
      if (isSyncPermissionError(syncError) && isDeviceSuAndNotRoot()) {
        return myDevice.getAdbFileTransfer().downloadFileViaTempLocation(getFullPath(), getSize(), localPath, progress, null);
      }
      else {
        return Futures.immediateFailedFuture(syncError);
      }
    });
  }

  @NotNull
  @Override
  public ListenableFuture<Void> uploadFile(@NotNull Path localPath,
                                           @NotNull String fileName,
                                           @NotNull FileTransferProgress progress) {
    String remotePath = AdbPathUtil.resolve(myEntry.getFullPath(), fileName);

    // If the device is *not* root, but supports "su 0", the ADB Sync service may not have the
    // permissions upload the local file directly to the remote location.
    // Given https://code.google.com/p/android/issues/detail?id=241157, we should not rely on the error
    // returned by the "upload" service, because a permission error is only returned *after* transferring
    // the whole file.
    // So, instead we "touch" the file and either use a regular upload if it succeeded or an upload
    // via the temp directory if it failed.

    ListenableFuture<Boolean> futureShouldCreateRemote = myDevice.getTaskExecutor().executeAsync(this::isDeviceSuAndNotRoot);

    return myDevice.getTaskExecutor().transformAsync(futureShouldCreateRemote, shouldCreateRemote -> {
      assert shouldCreateRemote != null;
      if (shouldCreateRemote) {
        ListenableFuture<Void> futureTouchFile = myDevice.getAdbFileOperations().touchFileAsDefaultUser(remotePath);
        ListenableFuture<Void> futureUpload = myDevice.getTaskExecutor().transformAsync(futureTouchFile, aVoid ->
          // If file creation succeeded, assume a regular upload will succeed.
          myDevice.getAdbFileTransfer().uploadFile(localPath, remotePath, progress)
        );
        return myDevice.getTaskExecutor().catchingAsync(futureUpload, AdbShellCommandException.class, error ->
          // If file creation failed, use an upload via temp. directory (using "su").
          myDevice.getAdbFileTransfer().uploadFileViaTempLocation(localPath, remotePath, progress, null)
        );
      }
      else {
        // Regular upload if root or su not supported (i.e. user devices)
        return myDevice.getAdbFileTransfer().uploadFile(localPath, remotePath, progress);
      }
    });
  }

  private static boolean isSyncPermissionError(@NotNull SyncException pullError) {
    return pullError.getErrorCode() == SyncException.SyncError.NO_REMOTE_OBJECT ||
           pullError.getErrorCode() == SyncException.SyncError.TRANSFER_PROTOCOL_ERROR;
  }

  private boolean isDeviceSuAndNotRoot()
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
    return myDevice.getCapabilities().supportsSuRootCommand() && !myDevice.getCapabilities().isRoot();
  }
}