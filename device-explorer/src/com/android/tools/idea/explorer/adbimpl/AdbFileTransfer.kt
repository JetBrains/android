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
package com.android.tools.idea.explorer.adbimpl

import com.android.tools.idea.adblib.ddmlibcompatibility.pullFile
import com.android.tools.idea.adblib.ddmlibcompatibility.pushFile
import com.android.ddmlib.IDevice
import com.android.tools.idea.explorer.adbimpl.AdbFileOperations
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.explorer.adbimpl.AdbFileListingEntry
import com.android.tools.idea.explorer.fs.FileTransferProgress
import com.android.tools.idea.explorer.adbimpl.AdbPathUtil
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.explorer.adbimpl.AdbFileTransfer.SingleFileProgressMonitor
import com.android.tools.idea.explorer.adbimpl.AdbFileTransfer
import com.android.ddmlib.SyncService
import com.android.ddmlib.SyncException
import java.io.IOException
import com.android.ddmlib.SyncService.ISyncProgressMonitor
import com.android.tools.idea.explorer.fs.ThrottledProgress
import com.google.common.util.concurrent.AsyncFunction
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.diagnostic.Logger
import java.lang.Runnable
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.Executor

class AdbFileTransfer(
  private val myDevice: IDevice,
  private val myFileOperations: AdbFileOperations,
  progressExecutor: Executor,
  taskExecutor: Executor
) {
  private val myProgressExecutor: FutureCallbackExecutor
  private val myTaskExecutor: FutureCallbackExecutor
  fun downloadFile(
    remoteFileEntry: AdbFileListingEntry,
    localPath: Path,
    progress: FileTransferProgress
  ): ListenableFuture<Unit> {
    return downloadFileWorker(remoteFileEntry.fullPath, remoteFileEntry.size, localPath, progress)
  }

  fun downloadFile(
    remotePath: String,
    remotePathSize: Long,
    localPath: Path,
    progress: FileTransferProgress
  ): ListenableFuture<Unit> {
    return downloadFileWorker(remotePath, remotePathSize, localPath, progress)
  }

  fun downloadFileViaTempLocation(
    remotePath: String,
    remotePathSize: Long,
    localPath: Path,
    progress: FileTransferProgress,
    runAs: String?
  ): ListenableFuture<Unit> {
    // Note: We should reach this code only if the device is not root, in which case
    // trying a "pullFile" would fail because of permission error (reading from the /data/data/
    // directory), so we copy the file to a temp. location, then pull from that temp location.
    val futureTempFile = myFileOperations.createTempFile(AdbPathUtil.DEVICE_TEMP_DIRECTORY)
    return myTaskExecutor.transformAsync(futureTempFile) { tempFile: String? ->
      assert(tempFile != null)

      // Copy the remote file to the temporary remote location
      val futureCopy = myFileOperations.copyFileRunAs(remotePath, tempFile!!, runAs)
      val futureDownload = myTaskExecutor.transformAsync(futureCopy, AsyncFunction { aVoid: Unit ->
        downloadFile(
          tempFile, remotePathSize, localPath, progress
        )
      })
      myTaskExecutor.finallyAsync(
        futureDownload
      ) {
        myFileOperations.deleteFile(
          tempFile
        )
      }
    }
  }

  fun uploadFile(
    localPath: Path,
    remotePath: String,
    progress: FileTransferProgress
  ): ListenableFuture<Unit> {
    return uploadFileWorker(localPath, remotePath, progress)
  }

  fun uploadFileViaTempLocation(
    localPath: Path,
    remotePath: String,
    progress: FileTransferProgress,
    runAs: String?
  ): ListenableFuture<Unit> {
    val futureTempFile = myFileOperations.createTempFile(AdbPathUtil.DEVICE_TEMP_DIRECTORY)
    return myTaskExecutor.transformAsync(futureTempFile) { tempFile: String? ->
      assert(tempFile != null)

      // Upload to temporary location
      val futureUpload = uploadFile(localPath, tempFile!!, progress)
      val futureCopy = myTaskExecutor.transformAsync(futureUpload, AsyncFunction { aVoid: Unit ->
        myFileOperations.copyFileRunAs(
          tempFile, remotePath, runAs
        )
      })
      myTaskExecutor.finallyAsync(
        futureCopy
      ) {
        myFileOperations.deleteFile(
          tempFile
        )
      }
    }
  }

  private fun downloadFileWorker(
    remotePath: String,
    remotePathSize: Long,
    localPath: Path,
    progress: FileTransferProgress
  ): ListenableFuture<Unit> {
    val futurePull: ListenableFuture<Unit>
    futurePull = if (StudioFlags.ADBLIB_MIGRATION_DEVICE_EXPLORER.get()) {
      myTaskExecutor.executeAsync {
        val startTime = System.nanoTime()
        pullFile(
          myDevice,
          remotePath,
          localPath.toString(),
          SingleFileProgressMonitor(myProgressExecutor, progress, remotePathSize)
        )
        val endTime = System.nanoTime()
        LOGGER.info(
          String.format(
            Locale.US, "Pull file took %,d ms to execute: \"%s\" -> \"%s\"", (endTime - startTime) / 1000000,
            remotePath, localPath
          )
        )
        Unit
      }
    } else {
      val futureSyncService = syncService
      myTaskExecutor.transform(futureSyncService) { syncService: SyncService? ->
        assert(syncService != null)
        syncService.use { ignored ->
          val startTime = System.nanoTime()
          syncService!!.pullFile(
            remotePath,
            localPath.toString(),
            SingleFileProgressMonitor(myProgressExecutor, progress, remotePathSize)
          )
          val endTime = System.nanoTime()
          LOGGER.info(
            String.format(
              Locale.US, "Pull file took %,d ms to execute: \"%s\" -> \"%s\"", (endTime - startTime) / 1000000,
              remotePath, localPath
            )
          )
          return@transform Unit
        }
      }
    }
    return myTaskExecutor.catchingAsync(futurePull, SyncException::class.java) { syncError: SyncException? ->
      assert(syncError != null)
      if (syncError!!.wasCanceled()) {
        // Simply forward cancellation as the cancelled exception
        return@catchingAsync Futures.immediateCancelledFuture<Unit>()
      }
      LOGGER.info(String.format("Error pulling file from \"%s\" to \"%s\"", remotePath, localPath), syncError)
      Futures.immediateFailedFuture(syncError)
    }
  }

  private fun uploadFileWorker(
    localPath: Path,
    remotePath: String,
    progress: FileTransferProgress
  ): ListenableFuture<Unit> {
    val futurePush: ListenableFuture<Unit>
    futurePush = if (StudioFlags.ADBLIB_MIGRATION_DEVICE_EXPLORER.get()) {
      myTaskExecutor.executeAsync {
        val fileLength = localPath.toFile().length()
        val startTime = System.nanoTime()
        pushFile(
          myDevice,
          localPath.toString(),
          remotePath,
          SingleFileProgressMonitor(myProgressExecutor, progress, fileLength)
        )
        val endTime = System.nanoTime()
        LOGGER.info(
          String.format(
            Locale.US, "Push file took %,d ms to execute: \"%s\" -> \"%s\"", (endTime - startTime) / 1000000,
            localPath,
            remotePath
          )
        )
        Unit
      }
    } else {
      val futureSyncService = syncService
      myTaskExecutor.transform(futureSyncService) { syncService: SyncService? ->
        assert(syncService != null)
        syncService.use { ignored ->
          val fileLength = localPath.toFile().length()
          val startTime = System.nanoTime()
          syncService!!.pushFile(
            localPath.toString(),
            remotePath,
            SingleFileProgressMonitor(myProgressExecutor, progress, fileLength)
          )
          val endTime = System.nanoTime()
          LOGGER.info(
            String.format(
              Locale.US, "Push file took %,d ms to execute: \"%s\" -> \"%s\"", (endTime - startTime) / 1000000,
              localPath,
              remotePath
            )
          )
          return@transform Unit
        }
      }
    }
    return myTaskExecutor.catchingAsync(futurePush, SyncException::class.java) { syncError: SyncException? ->
      assert(syncError != null)
      if (syncError!!.wasCanceled()) {
        // Simply forward cancellation as the cancelled exception
        return@catchingAsync Futures.immediateCancelledFuture<Unit>()
      }
      LOGGER.info(String.format("Error pushing file from \"%s\" to \"%s\"", localPath, remotePath), syncError)
      Futures.immediateFailedFuture(syncError)
    }
  }

  private val syncService: ListenableFuture<SyncService>
    private get() = myTaskExecutor.executeAsync {
      val sync = myDevice.syncService ?: throw IOException("Unable to open synchronization service to device")
      sync
    }

  /**
   * Forward callbacks from a [SyncService.ISyncProgressMonitor], running on a pooled thread,
   * to a [FileTransferProgress], using the provided [Executor], typically the
   * [com.intellij.util.concurrency.EdtExecutorService].
   */
  private class SingleFileProgressMonitor(
    private val myCallbackExecutor: Executor,
    private val myProgress: FileTransferProgress,
    private val myTotalBytes: Long
  ) : ISyncProgressMonitor {
    private val myThrottledProgress: ThrottledProgress
    private var myCurrentBytes: Long = 0
    override fun start(totalWork: Int) {
      // Note: We ignore the value of "totalWork" because 1) during a "pull", it is
      //       always 0, and 2) during a "push", it is truncated to 2GB (int), which
      //       makes things confusing when push a very big file (>2GB).
      //       This is why we have our owm "myTotalBytes" field.
      myCallbackExecutor.execute { myProgress.progress(0, myTotalBytes) }
    }

    override fun stop() {
      myCallbackExecutor.execute { myProgress.progress(myTotalBytes, myTotalBytes) }
    }

    override fun isCanceled(): Boolean {
      return myProgress.isCancelled
    }

    override fun startSubTask(name: String) {
      assert(false) { "A single file sync should not have multiple tasks" }
    }

    override fun advance(work: Int) {
      myCurrentBytes += work.toLong()
      if (myThrottledProgress.check()) {
        // Capture value for lambda (since lambda may be executed after some delay)
        val currentBytes = myCurrentBytes
        myCallbackExecutor.execute { myProgress.progress(currentBytes, myTotalBytes) }
      }
    }

    companion object {
      private const val PROGRESS_REPORT_INTERVAL_MILLIS = 50
    }

    init {
      myThrottledProgress = ThrottledProgress(PROGRESS_REPORT_INTERVAL_MILLIS.toLong())
    }
  }

  companion object {
    private val LOGGER = Logger.getInstance(AdbFileTransfer::class.java)
  }

  init {
    myProgressExecutor = FutureCallbackExecutor.wrap(progressExecutor)
    myTaskExecutor = FutureCallbackExecutor.wrap(taskExecutor)
  }
}