/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files

import com.android.tools.idea.concurrency.AndroidDispatchers.diskIoThread
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.file.explorer.toolwindow.FileTransferWorkEstimator.Companion.directoryWorkUnits
import com.android.tools.idea.file.explorer.toolwindow.FileTransferWorkEstimator.Companion.fileWorkUnits
import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceFileEntry
import com.android.tools.idea.file.explorer.toolwindow.fs.ThrottledProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path

/**
 * Helper class used to estimate the amount of work required to transfer files from/to a device.
 * The work is estimated in terms of arbitrary "work units", which can be computed by calling
 * the [estimateDownloadWork] or [estimateUploadWork] methods.
 *
 *
 * NOTE: Work Units: When transferring files to/from a device, there is a cost proportional to the
 * amount of bytes transferred to/from the device, but there is also a (non-trivial) fixed cost
 * (a few milliseconds typically) per file/directory corresponding to the initial round-trip to
 * the device (either to create the file, or check its existence).
 *
 *
 * For example, when transferring 2,999 1-byte files and one 1MB file, the transfer
 * time will be dominated by the # of files, not by amount of bytes, so we need a model
 * that tries to reflect this additional fixed cost.
 *
 *
 * Options:
 *
 *  * If we had no fixed cost and a cost of 1 per byte, progress would stay at 0 for the
 * first 2,999 files, then grow very quickly from 0 to 100% while transferring the 1MB file.
 * This would clearly be inadequate.
 *
 *  * If we had a fixed cost 1 and a cost of 1 per byte, the total work would be 1,000,000 + 3,000,
 * meaning the creation of the first 2,999 files would count for about .2% of the total estimated
 * progress, while the creation and transfer of the 1 MB file would account for remaining 99.8%.
 * This model is still inadequate, because it underestimate file creation costs.
 *
 *  * By picking a larger value for the fixed cost (currently `64,000`), the total work
 * becomes 1,000,000 + (3,000 * 64,000) = 193,000,000, so the cost of transferring the 2,999
 * small files account for 99% of the transfer time (2,999 * 64,000), which is closer to actual
 * time spent transferring the 3,000 files.
 *
 *
 *
 * The [directoryWorkUnits] and [fileWorkUnits] methods return the
 * estimated fixed cost (in work units) of creating 1 file/directory.
 *
 *
 * The [.getFileContentsWorkUnits] returns the estimated cost (in work units)
 * proportional to the amount of bytes to transfer.
 */
class FileTransferWorkEstimator {
  private val myThrottledProgress = ThrottledProgress(PROGRESS_REPORT_INTERVAL_MILLIS.toLong())

  suspend fun estimateDownloadWork(
    entry: DeviceFileEntry,
    isLinkToDirectory: Boolean,
    progress: FileTransferWorkEstimatorProgress
  ): FileTransferWorkEstimate {
    val workEstimate = FileTransferWorkEstimate()
    estimateDownloadWorkWorker(entry, isLinkToDirectory, workEstimate, progress)
    return workEstimate
  }

  suspend fun estimateDownloadWorkWorker(
    entry: DeviceFileEntry,
    isLinkToDirectory: Boolean,
    estimate: FileTransferWorkEstimate,
    progress: FileTransferWorkEstimatorProgress
  ) {
    if (progress.isCancelled) {
      cancelAndThrow()
    }
    reportProgress(estimate, progress)
    if (entry.isDirectory || isLinkToDirectory) {
      val children = entry.entries()
      estimate.addDirectoryCount(1)
      estimate.addWorkUnits(directoryWorkUnits)
      for (child in children) {
        estimateDownloadWorkWorker(child, false, estimate, progress)
      }
    } else {
      estimate.addFileCount(1)
      estimate.addWorkUnits(fileWorkUnits + getFileContentsWorkUnits(entry.size))
    }
  }

  suspend fun estimateUploadWork(
    path: Path,
    progress: FileTransferWorkEstimatorProgress
  ): FileTransferWorkEstimate = coroutineScope {
    withContext(diskIoThread) {
      val workEstimate = FileTransferWorkEstimate()
      estimateUploadWorkWorker(path.toFile(), workEstimate, progress)
      workEstimate
    }
  }
  /**
   * Build the estimate by scanning the local file system (synchronously).
   *
   * @throws CancellationException if canceled via the [progress] parameter.
   */
  private suspend fun estimateUploadWorkWorker(
    file: File,
    estimate: FileTransferWorkEstimate,
    progress: FileTransferWorkEstimatorProgress
  ) {
    if (progress.isCancelled) {
      cancelAndThrow()
    }
    reportProgress(estimate, progress)
    if (file.isDirectory) {
      estimate.addWorkUnits(directoryWorkUnits)
      estimate.addDirectoryCount(1)
      file.listFiles()?.forEach { estimateUploadWorkWorker(it, estimate, progress) }
    } else {
      estimate.addWorkUnits(fileWorkUnits + getFileContentsWorkUnits(file.length()))
      estimate.addFileCount(1)
    }
  }

  private suspend fun reportProgress(estimate: FileTransferWorkEstimate, progress: FileTransferWorkEstimatorProgress) = coroutineScope {
    if (myThrottledProgress.check()) {
      // Capture values for lambda (since lambda may be executed after some delay)
      val fileCount = estimate.fileCount
      val directoryCount = estimate.directoryCount

      // Report progress on the EDT executor
      launch(uiThread) { progress.progress(fileCount, directoryCount) }
    }
  }

  companion object {
    private const val PROGRESS_REPORT_INTERVAL_MILLIS = 50

    @JvmStatic
    val directoryWorkUnits = 64000L

    @JvmStatic
    val fileWorkUnits = 64000L

    @JvmStatic
    fun getFileContentsWorkUnits(byteCount: Long): Long {
      return byteCount
    }
  }
}