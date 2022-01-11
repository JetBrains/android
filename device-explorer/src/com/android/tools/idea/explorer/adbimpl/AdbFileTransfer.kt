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

import com.android.ddmlib.IDevice
import com.android.ddmlib.SyncException
import com.android.ddmlib.SyncService
import com.android.ddmlib.SyncService.ISyncProgressMonitor
import com.android.tools.idea.adblib.ddmlibcompatibility.pullFile
import com.android.tools.idea.adblib.ddmlibcompatibility.pushFile
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.explorer.cancelAndThrow
import com.android.tools.idea.explorer.fs.FileTransferProgress
import com.android.tools.idea.explorer.fs.ThrottledProgress
import com.android.tools.idea.flags.StudioFlags
import com.google.common.base.Stopwatch
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.Executor

private val LOGGER = logger<AdbFileTransfer>()

class AdbFileTransfer(
  private val device: IDevice,
  private val fileOperations: AdbFileOperations,
  progressExecutor: Executor,
  private val dispatcher: CoroutineDispatcher
) {
  private val progressExecutor = FutureCallbackExecutor.wrap(progressExecutor)

  suspend fun downloadFile(
    remoteFileEntry: AdbFileListingEntry,
    localPath: Path,
    progress: FileTransferProgress
  ) {
    return downloadFileWorker(remoteFileEntry.fullPath, remoteFileEntry.size, localPath, progress)
  }

  suspend fun downloadFile(
    remotePath: String,
    remotePathSize: Long,
    localPath: Path,
    progress: FileTransferProgress
  ) {
    return downloadFileWorker(remotePath, remotePathSize, localPath, progress)
  }

  suspend fun downloadFileViaTempLocation(
    remotePath: String,
    remotePathSize: Long,
    localPath: Path,
    progress: FileTransferProgress,
    runAs: String?
  ) {
    // Note: We should reach this code only if the device is not root, in which case
    // trying a "pullFile" would fail because of permission error (reading from the /data/data/
    // directory), so we copy the file to a temp. location, then pull from that temp location.
    val tempFile = fileOperations.createTempFile(AdbPathUtil.DEVICE_TEMP_DIRECTORY)
    try {
      // Copy the remote file to the temporary remote location
      fileOperations.copyFileRunAs(remotePath, tempFile, runAs)
      downloadFile(tempFile, remotePathSize, localPath, progress)
    } finally {
      fileOperations.deleteFile(tempFile)
    }
  }

  suspend fun uploadFile(
    localPath: Path,
    remotePath: String,
    progress: FileTransferProgress
  ) {
    return uploadFileWorker(localPath, remotePath, progress)
  }

  suspend fun uploadFileViaTempLocation(
    localPath: Path,
    remotePath: String,
    progress: FileTransferProgress,
    runAs: String?
  ) {
    val tempFile = fileOperations.createTempFile(AdbPathUtil.DEVICE_TEMP_DIRECTORY)
    try {
      // Upload to temporary location
      uploadFile(localPath, tempFile, progress)
      fileOperations.copyFileRunAs(tempFile, remotePath, runAs)
    } finally {
      fileOperations.deleteFile(tempFile)
    }
  }

  private suspend fun downloadFileWorker(
    remotePath: String,
    remotePathSize: Long,
    localPath: Path,
    progress: FileTransferProgress
  ) {
    try {
      if (StudioFlags.ADBLIB_MIGRATION_DEVICE_EXPLORER.get()) {
        withContext(dispatcher) {
          val stopwatch = Stopwatch.createStarted()
          pullFile(
            device,
            remotePath,
            localPath.toString(),
            SingleFileProgressMonitor(progressExecutor, progress, remotePathSize))
          LOGGER.info("Pull file took $stopwatch to execute: \"$remotePath\" -> \"$localPath\"")
        }
      }
      else {
        syncService().use { syncService ->
          val stopwatch = Stopwatch.createStarted()
          syncService.pullFile(
            remotePath,
            localPath.toString(),
            SingleFileProgressMonitor(progressExecutor, progress, remotePathSize))
          LOGGER.info("Pull file took $stopwatch to execute: \"$remotePath\" -> \"$localPath\"")
        }
      }
    } catch (syncError: SyncException) {
      if (syncError.wasCanceled()) {
        // Simply forward cancellation as the cancelled exception
        cancelAndThrow()
      } else {
        LOGGER.info("Error pulling file from \"$remotePath\" to \"$localPath\"", syncError)
        throw syncError
      }
    }
  }

  private suspend fun uploadFileWorker(
    localPath: Path,
    remotePath: String,
    progress: FileTransferProgress
  ) {
    try {
      if (StudioFlags.ADBLIB_MIGRATION_DEVICE_EXPLORER.get()) {
        withContext(dispatcher) {
          val fileLength = localPath.toFile().length()
          val stopwatch = Stopwatch.createStarted()
          pushFile(device,
                   localPath.toString(),
                   remotePath,
                   SingleFileProgressMonitor(progressExecutor, progress, fileLength))
          LOGGER.info( "Push file took $stopwatch to execute: \"$localPath\" -> \"$remotePath\"")
        }
      } else {
        syncService().use { syncService ->
          val fileLength = localPath.toFile().length()
          val stopwatch = Stopwatch.createStarted()
          syncService.pushFile(
            localPath.toString(),
            remotePath,
            SingleFileProgressMonitor(progressExecutor, progress, fileLength))
          LOGGER.info("Push file took $stopwatch to execute: \"$localPath\" -> \"$remotePath\"")
        }
      }
    } catch (syncError: SyncException) {
      if (syncError.wasCanceled()) {
        // Simply forward cancellation as the cancelled exception
        cancelAndThrow()
      } else {
        LOGGER.info("Error pushing file from \"$localPath\" to \"$remotePath\"", syncError)
        throw syncError
      }
    }
  }

  private suspend fun syncService() =
    withContext(dispatcher) {
      device.syncService ?: throw IOException("Unable to open synchronization service to device")
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
    private val myThrottledProgress = ThrottledProgress(PROGRESS_REPORT_INTERVAL_MILLIS.toLong())
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
  }
}