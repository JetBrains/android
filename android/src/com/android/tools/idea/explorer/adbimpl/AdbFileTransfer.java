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

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executor;

public class AdbFileTransfer {
  @NotNull private static Logger LOGGER = Logger.getInstance(AdbFileTransfer.class);

  @NotNull private final IDevice myDevice;
  @NotNull private final FutureCallbackExecutor myProgressExecutor;
  @NotNull private final FutureCallbackExecutor myTaskExecutor;

  public AdbFileTransfer(@NotNull IDevice device,
                         @NotNull Executor progressExecutor,
                         @NotNull Executor taskExecutor) {
    myDevice = device;
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
  public ListenableFuture<Void> uploadFile(@NotNull Path localPath,
                                           @NotNull String remotePath,
                                           @NotNull FileTransferProgress progress) {
    return uploadFileWorker(localPath, remotePath, progress);
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
        long size = remotePathSize;
        syncService.pullFile(remotePath,
                             localPath.toString(),
                             new SingleFileProgressMonitor(myProgressExecutor, progress, size));
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
      LOGGER.info(String.format("Error pulling file \"%s\" from \"%s\"", localPath, remotePath), syncError);
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
        syncService.pushFile(localPath.toString(),
                             remotePath,
                             new SingleFileProgressMonitor(myProgressExecutor, progress, fileLength));
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
      LOGGER.info(String.format("Error pushing file \"%s\" to \"%s\"", localPath, remotePath), syncError);
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