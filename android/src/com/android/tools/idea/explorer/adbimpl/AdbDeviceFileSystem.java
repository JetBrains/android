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
package com.android.tools.idea.explorer.adbimpl;

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.SyncService;
import com.android.tools.idea.explorer.FutureCallbackExecutor;
import com.android.tools.idea.explorer.fs.*;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

public class AdbDeviceFileSystem implements DeviceFileSystem {
  @NotNull private static final Logger LOGGER = Logger.getInstance(AdbDeviceFileSystem.class);
  @NotNull private final AdbDeviceFileSystemService myService;
  @NotNull private final IDevice myDevice;
  @NotNull private final AdbFileListing myFileListing;
  @NotNull private final AdbFileOperations myFileOperations;

  public AdbDeviceFileSystem(@NotNull AdbDeviceFileSystemService service, @NotNull IDevice device) {
    myService = service;
    myDevice = device;
    AdbDeviceCapabilities deviceCapabilities = new AdbDeviceCapabilities(myDevice);
    myFileListing = new AdbFileListing(myDevice, deviceCapabilities, service.getTaskExecutor());
    myFileOperations = new AdbFileOperations(myDevice, deviceCapabilities, service.getTaskExecutor());
  }

  boolean isDevice(@Nullable IDevice device) {
    return myDevice.equals(device);
  }

  @NotNull
  IDevice getDevice() {
    return myDevice;
  }

  @NotNull
  public AdbFileListing getAdbFileListing() {
    return myFileListing;
  }

  @NotNull
  public AdbFileOperations getAdbFileOperations() {
    return myFileOperations;
  }

  @NotNull
  FutureCallbackExecutor getEdtExecutor() {
    return myService.getEdtExecutor();
  }

  @NotNull
  FutureCallbackExecutor getTaskExecutor() {
    return myService.getTaskExecutor();
  }

  @NotNull
  @Override
  public String getName() {
    return myDevice.getName();
  }

  @NotNull
  @Override
  public DeviceState getDeviceState() {
    switch (myDevice.getState()) {
      case ONLINE:
        return DeviceState.ONLINE;
      case OFFLINE:
        return DeviceState.OFFLINE;
      case UNAUTHORIZED:
        return DeviceState.UNAUTHORIZED;
      case DISCONNECTED:
        return DeviceState.DISCONNECTED;
      case BOOTLOADER:
        return DeviceState.BOOTLOADER;
      case RECOVERY:
        return DeviceState.RECOVERY;
      case SIDELOAD:
        return DeviceState.SIDELOAD;
      default:
        return DeviceState.DISCONNECTED;
    }
  }

  @NotNull
  @Override
  public ListenableFuture<DeviceFileEntry> getRootDirectory() {
    return getTaskExecutor().transform(getAdbFileListing().getRoot(), entry -> {
      assert entry != null;
      return new AdbDeviceFileEntry(this, entry, null);
    });
  }

  @NotNull
  @Override
  public ListenableFuture<DeviceFileEntry> getEntry(@NotNull String path) {
    SettableFuture<DeviceFileEntry> resultFuture = SettableFuture.create();

    ListenableFuture<DeviceFileEntry> currentDir = getRootDirectory();
    getTaskExecutor().addCallback(currentDir, new FutureCallback<DeviceFileEntry>() {
      @Override
      public void onSuccess(@Nullable DeviceFileEntry result) {
        assert result != null;

        if (StringUtil.isEmpty(path) || StringUtil.equals(path, FileListingService.FILE_SEPARATOR)) {
          resultFuture.set(result);
          return;
        }

        String[] pathSegments = path.split(FileListingService.FILE_SEPARATOR);
        resolvePathSegments(resultFuture, result, pathSegments, 0);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        resultFuture.setException(t);
      }
    });

    return resultFuture;
  }

  private void resolvePathSegments(@NotNull SettableFuture<DeviceFileEntry> future,
                                   @NotNull DeviceFileEntry currentEntry,
                                   @NotNull String[] segments,
                                   int segmentIndex) {
    if (segmentIndex >= segments.length) {
      future.set(currentEntry);
      return;
    }

    if (!currentEntry.isDirectory()) {
      future.setException(new IllegalArgumentException("Segment is not a directory"));
      return;
    }

    ListenableFuture<List<DeviceFileEntry>> entriesFuture = currentEntry.getEntries();
    getTaskExecutor().addCallback(entriesFuture, new FutureCallback<List<DeviceFileEntry>>() {
      @Override
      public void onSuccess(@Nullable List<DeviceFileEntry> result) {
        assert result != null;

        Optional<DeviceFileEntry> entry = result
          .stream()
          .filter(x -> x.getName().equals(segments[segmentIndex]))
          .findFirst();
        if (!entry.isPresent()) {
          future.setException(new IllegalArgumentException("Path not found"));
        }
        else {
          resolvePathSegments(future, entry.get(), segments, segmentIndex + 1);
        }
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        future.setException(t);
      }
    });
  }

  @NotNull
  @Override
  public ListenableFuture<Void> downloadFile(@NotNull DeviceFileEntry entry,
                                             @NotNull Path localPath,
                                             @NotNull FileTransferProgress progress) {
    if (!(entry instanceof AdbDeviceFileEntry)) {
      return Futures.immediateFailedFuture(new IllegalArgumentException("Invalid file entry"));
    }
    AdbDeviceFileEntry adbEntry = (AdbDeviceFileEntry)entry;
    return downloadFileWorker(adbEntry, localPath, progress);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> uploadFile(@NotNull Path localFilePath,
                                           @NotNull DeviceFileEntry remoteDirectory,
                                           @NotNull FileTransferProgress progress) {
    if (!(remoteDirectory instanceof AdbDeviceFileEntry)) {
      return Futures.immediateFailedFuture(new IllegalArgumentException("Invalid directory entry"));
    }
    return uploadFileWorker(localFilePath, (AdbDeviceFileEntry)remoteDirectory, progress);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> createNewFile(@NotNull DeviceFileEntry parentEntry, @NotNull String fileName) {
    return myFileOperations.createNewFile(parentEntry.getFullPath(), fileName);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> createNewDirectory(@NotNull DeviceFileEntry parentEntry, @NotNull String directoryName) {
    return myFileOperations.createNewDirectory(parentEntry.getFullPath(), directoryName);
  }

  @NotNull
  private ListenableFuture<Void> downloadFileWorker(@NotNull AdbDeviceFileEntry entry,
                                                    @NotNull Path localPath,
                                                    @NotNull FileTransferProgress progress) {

    ListenableFuture<SyncService> futureSyncService = getSyncService();

    ListenableFuture<Void> futurePull = getTaskExecutor().transform(futureSyncService, syncService -> {
      assert syncService != null;
      try {
        long size = entry.getSize();
        syncService.pullFile(entry.myEntry.getFullPath(),
                             localPath.toString(),
                             new SingleFileProgressMonitor(getEdtExecutor(), progress, size));
        return null;
      }
      finally {
        syncService.close();
      }
    });

    return getTaskExecutor().catchingAsync(futurePull, SyncException.class, syncError -> {
      assert syncError != null;
      if (syncError.wasCanceled()) {
        // Simply forward cancellation as the cancelled exception
        return Futures.immediateCancelledFuture();
      }
      LOGGER.info(String.format("Error pulling file \"%s\" from \"%s\"", localPath, entry.getFullPath()), syncError);
      return Futures.immediateFailedFuture(syncError);
    });
  }

  @NotNull
  private ListenableFuture<Void> uploadFileWorker(@NotNull Path localPath,
                                                  @NotNull AdbDeviceFileEntry remoteDirectory,
                                                  @NotNull FileTransferProgress progress) {

    ListenableFuture<SyncService> futureSyncService = getSyncService();

    ListenableFuture<Void> futurePush = getTaskExecutor().transform(futureSyncService, syncService -> {
      assert syncService != null;
      try {
        long fileLength = localPath.toFile().length();
        String remotePath = AdbPathUtil.resolve(remoteDirectory.getFullPath(), localPath.getFileName().toString());
        syncService.pushFile(localPath.toString(),
                             remotePath,
                             new SingleFileProgressMonitor(getEdtExecutor(), progress, fileLength));
        return null;
      }
      finally {
        syncService.close();
      }
    });

    return getTaskExecutor().catchingAsync(futurePush, SyncException.class, syncError -> {
      assert syncError != null;
      if (syncError.wasCanceled()) {
        // Simply forward cancellation as the cancelled exception
        return Futures.immediateCancelledFuture();
      }
      LOGGER.info(String.format("Error pushing file \"%s\" to \"%s\"", localPath, remoteDirectory.getFullPath()), syncError);
      return Futures.immediateFailedFuture(syncError);
    });
  }

  @NotNull
  private ListenableFuture<SyncService> getSyncService() {
    return getTaskExecutor().executeAsync(() -> {
      SyncService sync = myDevice.getSyncService();
      if (sync == null) {
        throw new IOException("Unable to open synchronization service to device");
      }
      return sync;
    });
  }

  /**
   * Forward callbacks from a {@link SyncService.ISyncProgressMonitor}, running on a pooled thread,
   * to a {@link FileTransferProgress}, using the provided {@link Executor}, typically the
   * {@link com.android.tools.idea.ddms.EdtExecutor}
   */
  private static class SingleFileProgressMonitor implements SyncService.ISyncProgressMonitor {
    private static final int PROGRESS_REPORT_INTERVAL_MILLIS = 50;
    @NotNull private final Executor myCallbackExecutor;
    @NotNull private final FileTransferProgress myProgress;
    @NotNull private final ThrottledProgress myThrottledProgress;
    private final long myTotalBytes;
    private long myCurrentBytes;

    public SingleFileProgressMonitor(@NotNull Executor callbackExecutor,
                                     @NotNull FileTransferProgress progress,
                                     long totalBytes) {
      myCallbackExecutor = callbackExecutor;
      myProgress = progress;
      myTotalBytes = totalBytes;
      myThrottledProgress = new ThrottledProgress(PROGRESS_REPORT_INTERVAL_MILLIS);
    }

    @Override
    public void start(int totalWork) {
      // Note: We ignore the value of "totalWork" because 1) during a "pull", it is
      //       always 0, and 2) during a "push", it is truncated to 2GB (int), which
      //       makes things confusing when push a very big file (>2GB).
      //       This is why we have our owm "myTotalBytes" field.
      myCallbackExecutor.execute(() -> myProgress.progress(0, myTotalBytes));
    }

    @Override
    public void stop() {
      myCallbackExecutor.execute(() -> myProgress.progress(myTotalBytes, myTotalBytes));
    }

    @Override
    public boolean isCanceled() {
      return myProgress.isCancelled();
    }

    @Override
    public void startSubTask(String name) {
      assert false : "A single file sync should not have multiple tasks";
    }

    @Override
    public void advance(int work) {
      myCurrentBytes += work;
      if (myThrottledProgress.check()) {
        // Capture value for lambda (since lambda may be executed after some delay)
        final long currentBytes = myCurrentBytes;
        myCallbackExecutor.execute(() -> myProgress.progress(currentBytes, myTotalBytes));
      }
    }
  }
}