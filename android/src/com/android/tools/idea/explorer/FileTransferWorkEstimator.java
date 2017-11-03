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
package com.android.tools.idea.explorer;

import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.android.tools.idea.explorer.fs.ThrottledProgress;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Helper class used to estimate the amount of work required to transfer files from/to a device.
 * The work is estimated in terms of arbitrary "work units", which can be computed by calling
 * the {@link #estimateDownloadWork(DeviceFileEntry, boolean, FileTransferWorkEstimatorProgress)} or
 * {@link #estimateUploadWork(Path, FileTransferWorkEstimatorProgress)}
 * methods.
 *
 * <p>NOTE: Work Units: When transferring files to/from a device, there is a cost proportional to the
 * amount of bytes transferred to/from the device, but there is also a (non-trivial) fixed cost
 * (a few milliseconds typically) per file/directory corresponding to the initial round-trip to
 * the device (either to create the file, or check its existence).
 *
 * <p>For example, when transferring 2,999 1-byte files and one 1MB file, the transfer
 *    time will be dominated by the # of files, not by amount of bytes, so we need a model
 *    that tries to reflect this additional fixed cost.
 *
 * <p>Options:
 * <ul>
 *   <li>If we had no fixed cost and a cost of 1 per byte, progress would stay at 0 for the
 *   first 2,999 files, then grow very quickly from 0 to 100% while transferring the 1MB file.
 *   This would clearly be inadequate.</li>
 *
 *   <li>If we had a fixed cost 1 and a cost of 1 per byte, the total work would be 1,000,000 + 3,000,
 *   meaning the creation of the first 2,999 files would count for about .2% of the total estimated
 *   progress, while the creation and transfer of the 1 MB file would account for remaining 99.8%.
 *   This model is still inadequate, because it underestimate file creation costs.</li>
 *
 *   <li>By picking a larger value for the fixed cost (currently {@code 64,000}), the total work
 *   becomes 1,000,000 + (3,000 * 64,000) = 193,000,000, so the cost of transferring the 2,999
 *   small files account for 99% of the transfer time (2,999 * 64,000), which is closer to actual
 *   time spent transferring the 3,000 files.</li>
 * </ul>
 *
 * <p>The {@link #getDirectoryWorkUnits()} and {@link #getFileWorkUnits()} methods return the
 * estimated fixed cost (in work units) of creating 1 file/directory.
 *
 * <p>The {@link #getFileContentsWorkUnits(long)} returns the estimated cost (in work units)
 * proportional to the amount of bytes to transfer.
 */
public class FileTransferWorkEstimator {
  private static final int DIRECTORY_TRANSFER_WORK_UNITS = 64_000;
  private static final int FILE_TRANSFER_WORK_UNITS = 64_000;
  private static final int PROGRESS_REPORT_INTERVAL_MILLIS = 50;
  @NotNull private final FutureCallbackExecutor myEdtExecutor;
  @NotNull private final FutureCallbackExecutor myTaskExecutor;
  @NotNull private final ThrottledProgress myThrottledProgress;

  FileTransferWorkEstimator(@NotNull Executor edtExecutor, @NotNull Executor taskExecutor) {
    myEdtExecutor = FutureCallbackExecutor.wrap(edtExecutor);
    myTaskExecutor = FutureCallbackExecutor.wrap(taskExecutor);
    myThrottledProgress = new ThrottledProgress(PROGRESS_REPORT_INTERVAL_MILLIS);
  }

  public static long getDirectoryWorkUnits() {
    return DIRECTORY_TRANSFER_WORK_UNITS;
  }

  public static long getFileWorkUnits() {
    return FILE_TRANSFER_WORK_UNITS;
  }

  public static long getFileContentsWorkUnits(long byteCount) {
    return byteCount;
  }

  public ListenableFuture<FileTransferWorkEstimate> estimateDownloadWork(@NotNull DeviceFileEntry entry,
                                                                         boolean isLinkToDirectory,
                                                                         @NotNull FileTransferWorkEstimatorProgress progress) {
    FileTransferWorkEstimate workEstimate = new FileTransferWorkEstimate();
    ListenableFuture<Void> future = estimateDownloadWorkWorker(entry, isLinkToDirectory, workEstimate, progress);
    return myEdtExecutor.transform(future, aVoid -> workEstimate);
  }

  public ListenableFuture<Void> estimateDownloadWorkWorker(@NotNull DeviceFileEntry entry,
                                                           boolean isLinkToDirectory,
                                                           @NotNull FileTransferWorkEstimate estimate,
                                                           @NotNull FileTransferWorkEstimatorProgress progress) {
    if (progress.isCancelled()) {
      return Futures.immediateCancelledFuture();
    }
    reportProgress(estimate, progress);

    if (entry.isDirectory() || isLinkToDirectory) {
      ListenableFuture<List<DeviceFileEntry>> futureEntries = entry.getEntries();
      return myEdtExecutor.transformAsync(futureEntries, entries -> {
        assert entries != null;
        estimate.addDirectoryCount(1);
        estimate.addWorkUnits(getDirectoryWorkUnits());
        return myEdtExecutor.executeFuturesInSequence(entries.iterator(),
                                                      childEntry -> estimateDownloadWorkWorker(childEntry, false, estimate, progress));
      });
    }
    else {
      estimate.addFileCount(1);
      estimate.addWorkUnits(getFileWorkUnits() + getFileContentsWorkUnits(entry.getSize()));
      return Futures.immediateFuture(null);
    }
  }

  public ListenableFuture<FileTransferWorkEstimate> estimateUploadWork(@NotNull Path path,
                                                                       @NotNull FileTransferWorkEstimatorProgress progress) {
    ListenableFuture<FileTransferWorkEstimate> futureEstimate = myTaskExecutor.executeAsync(() -> {
      FileTransferWorkEstimate workEstimate = new FileTransferWorkEstimate();
      if (!estimateUploadWorkWorker(path.toFile(), workEstimate, progress)) {
        return null;
      }
      return workEstimate;
    });
    // Handle "cancel" case
    return myTaskExecutor.transformAsync(futureEstimate, estimate -> {
      if (estimate == null) {
        return Futures.immediateCancelledFuture();
      }
      return Futures.immediateFuture(estimate);
    });
  }

  private boolean estimateUploadWorkWorker(@NotNull File file,
                                           @NotNull FileTransferWorkEstimate estimate,
                                           @NotNull FileTransferWorkEstimatorProgress progress) {
    if (progress.isCancelled()) {
      return false;
    }
    reportProgress(estimate, progress);

    if (file.isDirectory()) {
      estimate.addWorkUnits(getDirectoryWorkUnits());
      estimate.addDirectoryCount(1);
      File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) {
          if (!estimateUploadWorkWorker(child, estimate, progress)) {
            return false;
          }
        }
      }
    }
    else {
      estimate.addWorkUnits(getFileWorkUnits() + getFileContentsWorkUnits(file.length()));
      estimate.addFileCount(1);
    }

    return true;
  }

  private void reportProgress(@NotNull FileTransferWorkEstimate estimate, @NotNull FileTransferWorkEstimatorProgress progress) {
    if (myThrottledProgress.check()) {
      // Capture values for lambda (since lambda may be executed after some delay)
      final int fileCount = estimate.getFileCount();
      final int directoryCount = estimate.getDirectoryCount();

      // Report progress on the EDT executor
      myEdtExecutor.execute(() -> progress.progress(fileCount, directoryCount));
    }
  }
}