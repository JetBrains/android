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
package com.android.tools.idea.device.explorer.files.adbimpl

import com.android.adblib.ConnectedDevice
import com.android.adblib.RemoteFileMode
import com.android.adblib.SyncProgress
import com.android.adblib.syncRecv
import com.android.adblib.syncSend
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.device.explorer.files.cancelAndThrow
import com.android.tools.idea.device.explorer.files.fs.FileTransferProgress
import com.android.tools.idea.device.explorer.files.fs.ThrottledProgress
import com.android.tools.idea.flags.StudioFlags
import com.google.common.base.Stopwatch
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.Executor

private val LOGGER = logger<AdbFileTransfer>()

class AdbFileTransfer(
  private val device: ConnectedDevice,
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
      val monitor = SingleFileProgressMonitor(
        progressExecutor.asCoroutineDispatcher(), progress, remotePathSize)
      withContext(dispatcher) {
        val stopwatch = Stopwatch.createStarted()
        device.session.channelFactory.createFile(localPath).use { fileChannel ->
          device.session.deviceServices.syncRecv(device.selector, remotePath, fileChannel, monitor)
        }
        LOGGER.info("Pull file took $stopwatch to execute: \"$remotePath\" -> \"$localPath\"")
      }
    } catch (e: IOException) {
      LOGGER.info("Error pulling file from \"$remotePath\" to \"$localPath\"", e)
      throw e
    }
  }

  private suspend fun uploadFileWorker(
    localPath: Path,
    remotePath: String,
    progress: FileTransferProgress
  ) {
    try {
      withContext(dispatcher) {
        val fileLength = localPath.toFile().length()
        val stopwatch = Stopwatch.createStarted()
        val monitor = SingleFileProgressMonitor(progressExecutor.asCoroutineDispatcher(), progress, fileLength)

        device.session.channelFactory.openFile(localPath).use { fileChannel ->
          device.session.deviceServices.syncSend(
            device.selector, fileChannel, remotePath,
            RemoteFileMode.fromPath(localPath) ?: RemoteFileMode.DEFAULT,
            null,
            monitor)
        }

        LOGGER.info( "Push file took $stopwatch to execute: \"$localPath\" -> \"$remotePath\"")
      }
    } catch (e: IOException) {
      LOGGER.info("Error pushing file from \"$localPath\" to \"$remotePath\"", e)
      throw e
    }
  }
}

/**
 * Forward callbacks from a [SyncProgress], running on a pooled thread,
 * to a [FileTransferProgress], using the provided [CoroutineDispatcher],
 * typically the UI dispatcher.
 */
private class SingleFileProgressMonitor(
  private val callbackDispatcher: CoroutineDispatcher,
  private val progress: FileTransferProgress,
  private val totalBytes: Long
) : SyncProgress {
  private val throttledProgress = ThrottledProgress(PROGRESS_REPORT_INTERVAL_MILLIS)
  private var currentBytes: Long = 0

  override suspend fun transferStarted(remotePath: String) {
    withContext(callbackDispatcher) {
      progress.progress(0, totalBytes)
    }
  }

  override suspend fun transferProgress(remotePath: String, totalBytesSoFar: Long) {
    if (progress.isCancelled) {
      cancelAndThrow()
    }
    currentBytes = totalBytesSoFar
    if (throttledProgress.check()) {
      withContext(callbackDispatcher) { progress.progress(totalBytesSoFar, totalBytes) }
    }
  }

  override suspend fun transferDone(remotePath: String, totalBytes: Long) {
    withContext(callbackDispatcher) {
      progress.progress(totalBytes, totalBytes)
    }
  }
}

private const val PROGRESS_REPORT_INTERVAL_MILLIS = 50L
