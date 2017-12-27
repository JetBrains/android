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

import com.android.ddmlib.IDevice;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.SyncService;
import com.android.tools.idea.explorer.FutureCallbackExecutor;
import com.android.tools.idea.explorer.fs.FileTransferProgress;
import com.android.tools.idea.explorer.fs.ThrottledProgress;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executor;

import static com.android.tools.idea.explorer.adbimpl.AdbPathUtil.DEVICE_TEMP_DIRECTORY;

public class AdbFileTransfer {
  @NotNull private static Logger LOGGER = Logger.getInstance(AdbFileTransfer.class);

  @NotNull private final IDevice myDevice;
  @NotNull private final AdbFileOperations myFileOperations;
  @NotNull private final FutureCallbackExecutor myProgressExecutor;
  @NotNull private final FutureCallbackExecutor myTaskExecutor;

  public AdbFileTransfer(@NotNull IDevice device,
                         @NotNull AdbFileOperations fileOperations,
                         @NotNull Executor progressExecutor,
                         @NotNull Executor taskExecutor) {
    myDevice = device;
    myFileOperations = fileOperations;
    myProgressExecutor = FutureCallbackExecutor.wrap(progressExecutor);
    myTaskExecutor = FutureCallbackExecutor.wrap(taskExecutor);
  }

  @NotNull
  public ListenableFuture<Void> downloadFile(@NotNull AdbFileListingEntry remoteFileEntry,
                                             @NotNull Path localPath,
                                             @NotNull FileTransferProgress progress) {
    return downloadFileWorker(remoteFileEntry.getFullPath(), remoteFileEntry.getSize(), localPath, progress);
  }

  @NotNull
  public ListenableFuture<Void> downloadFile(@NotNull String remotePath,
                                             long remotePathSize,
                                             @NotNull Path localPath,
                                             @NotNull FileTransferProgress progress) {
    return downloadFileWorker(remotePath, remotePathSize, localPath, progress);
  }

  @NotNull
  public ListenableFuture<Void> downloadFileViaTempLocation(@NotNull String remotePath,
                                                            long remotePathSize,
                                                            @NotNull Path localPath,
                                                            @NotNull FileTransferProgress progress,
                                                            @Nullable String runAs) {
    // Note: We should reach this code only if the device is not root, in which case
    // trying a "pullFile" would fail because of permission error (reading from the /data/data/
    // directory), so we copy the file to a temp. location, then pull from that temp location.
    ListenableFuture<String> futureTempFile = myFileOperations.createTempFile(DEVICE_TEMP_DIRECTORY);
    return myTaskExecutor.transformAsync(futureTempFile, tempFile -> {
      assert tempFile != null;

      // Copy the remote file to the temporary remote location
      ListenableFuture<Void> futureCopy = myFileOperations.copyFileRunAs(remotePath, tempFile, runAs);
      ListenableFuture<Void> futureDownload = myTaskExecutor.transformAsync(futureCopy, aVoid -> {
        // Download the temporary remote file to local disk
        return downloadFile(tempFile, remotePathSize, localPath, progress);
      });

      // Ensure temporary remote file is deleted in all cases (after download success *or* error)
      return myTaskExecutor.finallyAsync(futureDownload,
                                         () -> myFileOperations.deleteFile(tempFile));
    });
  }


  @NotNull
  public ListenableFuture<Void> uploadFile(@NotNull Path localPath,
                                           @NotNull String remotePath,
                                           @NotNull FileTransferProgress progress) {
    return uploadFileWorker(localPath, remotePath, progress);
  }

  public ListenableFuture<Void> uploadFileViaTempLocation(@NotNull Path localPath,
                                                          @NotNull String remotePath,
                                                          @NotNull FileTransferProgress progress,
                                                          @Nullable String runAs) {
    ListenableFuture<String> futureTempFile = myFileOperations.createTempFile(DEVICE_TEMP_DIRECTORY);
    return myTaskExecutor.transformAsync(futureTempFile, tempFile -> {
      assert tempFile != null;

      // Upload to temporary location
      ListenableFuture<Void> futureUpload = uploadFile(localPath, tempFile, progress);
      ListenableFuture<Void> futureCopy = myTaskExecutor.transformAsync(futureUpload, aVoid -> {
        // Copy file from temporary location to package location (using "run-as")
        return myFileOperations.copyFileRunAs(tempFile, remotePath, runAs);
      });

      // Ensure temporary remote file is deleted in all cases (after upload success *or* error)
      return myTaskExecutor.finallyAsync(futureCopy,
                                         () -> myFileOperations.deleteFile(tempFile));
    });
  }

  @NotNull
  private ListenableFuture<Void> downloadFileWorker(@NotNull String remotePath,
                                                    long remotePathSize,
                                                    @NotNull Path localPath,
                                                    @NotNull FileTransferProgress progress) {

    ListenableFuture<SyncService> futureSyncService = getSyncService();

    ListenableFuture<Void> futurePull = myTaskExecutor.transform(futureSyncService, syncService -> {
      assert syncService != null;
      try {
        long startTime = System.nanoTime();
        syncService.pullFile(remotePath,
                             localPath.toString(),
                             new SingleFileProgressMonitor(myProgressExecutor, progress, remotePathSize));
        long endTime = System.nanoTime();
        LOGGER.info(String.format("Pull file took %,d ms to execute: \"%s\" -> \"%s\"",
                                  (endTime - startTime) / 1_000_000, remotePath, localPath));
        return null;
      }
      finally {
        syncService.close();
      }
    });

    return myTaskExecutor.catchingAsync(futurePull, SyncException.class, syncError -> {
      assert syncError != null;
      if (syncError.wasCanceled()) {
        // Simply forward cancellation as the cancelled exception
        return Futures.immediateCancelledFuture();
      }
      LOGGER.info(String.format("Error pulling file from \"%s\" to \"%s\"", remotePath, localPath), syncError);
      return Futures.immediateFailedFuture(syncError);
    });
  }

  @NotNull
  private ListenableFuture<Void> uploadFileWorker(@NotNull Path localPath,
                                                  @NotNull String remotePath,
                                                  @NotNull FileTransferProgress progress) {

    ListenableFuture<SyncService> futureSyncService = getSyncService();

    ListenableFuture<Void> futurePush = myTaskExecutor.transform(futureSyncService, syncService -> {
      assert syncService != null;
      try {
        long fileLength = localPath.toFile().length();
        long startTime = System.nanoTime();
        syncService.pushFile(localPath.toString(),
                             remotePath,
                             new SingleFileProgressMonitor(myProgressExecutor, progress, fileLength));
        long endTime = System.nanoTime();
        LOGGER.info(String.format("Push file took %,d ms to execute: \"%s\" -> \"%s\"",
                                  (endTime - startTime) / 1_000_000, localPath, remotePath));
        return null;
      }
      finally {
        syncService.close();
      }
    });

    return myTaskExecutor.catchingAsync(futurePush, SyncException.class, syncError -> {
      assert syncError != null;
      if (syncError.wasCanceled()) {
        // Simply forward cancellation as the cancelled exception
        return Futures.immediateCancelledFuture();
      }
      LOGGER.info(String.format("Error pushing file from \"%s\" to \"%s\"", localPath, remotePath), syncError);
      return Futures.immediateFailedFuture(syncError);
    });
  }

  @NotNull
  private ListenableFuture<SyncService> getSyncService() {
    return myTaskExecutor.executeAsync(() -> {
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