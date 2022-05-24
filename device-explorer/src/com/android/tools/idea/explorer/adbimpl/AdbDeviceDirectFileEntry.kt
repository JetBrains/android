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

import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.ShellCommandUnresponsiveException
import com.android.ddmlib.SyncException
import com.android.ddmlib.TimeoutException
import com.android.tools.idea.adb.AdbShellCommandException
import com.android.tools.idea.concurrency.catchingAsync
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.concurrency.transformAsync
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.tools.idea.explorer.fs.FileTransferProgress
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.IOException
import java.nio.file.Path

/**
 * A [AdbDeviceFileEntry] that goes directly to the remote file system for its file operations.
 *
 *
 * The (optional) `runAs` parameter is directly passed to the [AdbFileListing] and
 * [AdbFileOperations] methods to use as a `"run-as package-name" prefix`.
 */
class AdbDeviceDirectFileEntry(
  device: AdbDeviceFileSystem,
  entry: AdbFileListingEntry,
  parent: AdbDeviceFileEntry?,
  private val myRunAs: String?
) : AdbDeviceFileEntry(device, entry, parent) {
  override val entries: ListenableFuture<List<DeviceFileEntry>>
    get() =
      fileSystem.adbFileListing.getChildrenRunAs(myEntry, myRunAs).transform(fileSystem.taskExecutor) { children ->
        children.map { AdbDeviceDefaultFileEntry(fileSystem, it, this) }
      }

  override fun delete(): ListenableFuture<Unit> =
    if (isDirectory) {
      fileSystem.adbFileOperations.deleteRecursiveRunAs(fullPath, myRunAs)
    } else {
      fileSystem.adbFileOperations.deleteFileRunAs(fullPath, myRunAs)
    }

  override fun createNewFile(fileName: String): ListenableFuture<Unit> =
    fileSystem.adbFileOperations.createNewFileRunAs(fullPath, fileName, myRunAs)

  override fun createNewDirectory(directoryName: String): ListenableFuture<Unit> =
    fileSystem.adbFileOperations.createNewDirectoryRunAs(fullPath, directoryName, myRunAs)

  override val isSymbolicLinkToDirectory: ListenableFuture<Boolean>
    get() = fileSystem.adbFileListing.isDirectoryLinkRunAs(myEntry, myRunAs)

  override fun downloadFile(
    localPath: Path,
    progress: FileTransferProgress
  ): ListenableFuture<Unit> =
    // Note: First try to download the file as the default user. If we get a permission error,
    //       download the file via a temp. directory using the "su 0" user.
    fileSystem.adbFileTransfer.downloadFile(myEntry, localPath, progress)
      .catchingAsync(fileSystem.taskExecutor, SyncException::class.java) { syncException ->
        if (isSyncPermissionError(syncException) && isDeviceSuAndNotRoot) {
          fileSystem.adbFileTransfer.downloadFileViaTempLocation(fullPath, size, localPath, progress, null)
        }
        else {
          Futures.immediateFailedFuture(syncException)
        }
      }

  override fun uploadFile(
    localPath: Path,
    fileName: String,
    progress: FileTransferProgress
  ): ListenableFuture<Unit> {
    val remotePath = AdbPathUtil.resolve(myEntry.fullPath, fileName)

    // If the device is *not* root, but supports "su 0", the ADB Sync service may not have the
    // permissions upload the local file directly to the remote location.
    // Given https://code.google.com/p/android/issues/detail?id=241157, we should not rely on the error
    // returned by the "upload" service, because a permission error is only returned *after* transferring
    // the whole file.
    // So, instead we "touch" the file and either use a regular upload if it succeeded or an upload
    // via the temp directory if it failed.
    val futureShouldCreateRemote = fileSystem.taskExecutor.executeAsync { isDeviceSuAndNotRoot }
    return fileSystem.taskExecutor.transformAsync(futureShouldCreateRemote) { shouldCreateRemote: Boolean? ->
      if (checkNotNull(shouldCreateRemote)) {
        fileSystem.adbFileOperations.touchFileAsDefaultUser(remotePath).transformAsync(fileSystem.taskExecutor) {
          fileSystem.adbFileTransfer.uploadFile(localPath, remotePath, progress)
        }.catchingAsync(fileSystem.taskExecutor, AdbShellCommandException::class.java) {
          fileSystem.adbFileTransfer.uploadFileViaTempLocation(localPath, remotePath, progress, null)
        }
      }
      else {
        // Regular upload if root or su not supported (i.e. user devices)
        fileSystem.adbFileTransfer.uploadFile(localPath, remotePath, progress)
      }
    }
  }

  @get:Throws(
    TimeoutException::class,
    AdbCommandRejectedException::class,
    ShellCommandUnresponsiveException::class,
    IOException::class
  )
  private val isDeviceSuAndNotRoot: Boolean
    get() = fileSystem.capabilities.supportsSuRootCommand() && !fileSystem.capabilities.isRoot

  companion object {
    private fun isSyncPermissionError(pullError: SyncException): Boolean {
      return pullError.errorCode == SyncException.SyncError.NO_REMOTE_OBJECT ||
        pullError.errorCode == SyncException.SyncError.TRANSFER_PROTOCOL_ERROR
    }
  }
}