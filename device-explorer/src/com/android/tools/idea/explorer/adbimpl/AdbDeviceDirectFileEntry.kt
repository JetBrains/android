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

import com.android.tools.idea.explorer.fs.DeviceFileEntry.entries
import com.android.tools.idea.explorer.fs.DeviceFileEntry.delete
import com.android.tools.idea.explorer.fs.DeviceFileEntry.createNewFile
import com.android.tools.idea.explorer.fs.DeviceFileEntry.createNewDirectory
import com.android.tools.idea.explorer.fs.DeviceFileEntry.isSymbolicLinkToDirectory
import com.android.tools.idea.explorer.fs.DeviceFileEntry.downloadFile
import com.android.tools.idea.explorer.fs.DeviceFileEntry.uploadFile
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystem
import com.android.tools.idea.explorer.adbimpl.AdbFileListingEntry
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileEntry
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.tools.idea.explorer.fs.DeviceFileSystem
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileEntry.AdbPermissions
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileEntry.AdbDateTime
import com.android.tools.idea.explorer.fs.FileTransferProgress
import com.android.tools.idea.explorer.adbimpl.AdbDeviceForwardingFileEntry
import com.android.tools.idea.explorer.adbimpl.AdbDeviceDirectFileEntry
import com.android.tools.idea.explorer.adbimpl.AdbDeviceDataDirectoryEntry.AdbDeviceDataAppDirectoryEntry
import com.android.tools.idea.explorer.adbimpl.AdbDeviceDataDirectoryEntry
import com.android.tools.idea.explorer.adbimpl.AdbDeviceDataDirectoryEntry.AdbDeviceDataDataDirectoryEntry
import com.android.tools.idea.explorer.adbimpl.AdbDeviceDataDirectoryEntry.AdbDeviceDataLocalDirectoryEntry
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.explorer.adbimpl.AdbDeviceDataDirectoryEntry.AdbDevicePackageDirectoryEntry
import com.android.tools.idea.explorer.adbimpl.AdbFileOperations
import com.android.tools.idea.explorer.adbimpl.AdbPathUtil
import com.android.tools.idea.explorer.adbimpl.AdbFileListingEntryBuilder
import com.android.tools.idea.explorer.adbimpl.AdbDeviceDefaultFileEntry
import com.android.ddmlib.SyncException
import com.android.tools.idea.adb.AdbShellCommandException
import kotlin.Throws
import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.ShellCommandUnresponsiveException
import com.android.ddmlib.TimeoutException
import com.google.common.util.concurrent.AsyncFunction
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.IOException
import java.nio.file.Path
import java.util.stream.Collectors

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
    get() {
      val children = myDevice.adbFileListing.getChildrenRunAs(myEntry, myRunAs)
      return myDevice.taskExecutor.transform(children) { result: List<AdbFileListingEntry>? ->
        assert(result != null)
        result!!.stream()
          .map { listingEntry: AdbFileListingEntry -> AdbDeviceDefaultFileEntry(myDevice, listingEntry, this) }
          .collect(Collectors.toList())
      }
    }

  override fun delete(): ListenableFuture<Unit> {
    return if (isDirectory()) {
      myDevice.adbFileOperations.deleteRecursiveRunAs(getFullPath(), myRunAs)
    } else {
      myDevice.adbFileOperations.deleteFileRunAs(getFullPath(), myRunAs)
    }
  }

  override fun createNewFile(fileName: String): ListenableFuture<Unit> {
    return myDevice.adbFileOperations.createNewFileRunAs(getFullPath(), fileName, myRunAs)
  }

  override fun createNewDirectory(directoryName: String): ListenableFuture<Unit> {
    return myDevice.adbFileOperations.createNewDirectoryRunAs(getFullPath(), directoryName, myRunAs)
  }

  override val isSymbolicLinkToDirectory: ListenableFuture<Boolean>
    get() = myDevice.adbFileListing.isDirectoryLinkRunAs(myEntry, myRunAs)

  override fun downloadFile(
    localPath: Path,
    progress: FileTransferProgress
  ): ListenableFuture<Unit> {
    // Note: First try to download the file as the default user. If we get a permission error,
    //       download the file via a temp. directory using the "su 0" user.
    val futureDownload = myDevice.adbFileTransfer.downloadFile(myEntry, localPath, progress)
    return myDevice.taskExecutor.catchingAsync(futureDownload, SyncException::class.java) { syncException: SyncException? ->
      assert(syncException != null)
      if (isSyncPermissionError(syncException!!) && isDeviceSuAndNotRoot) {
        return@catchingAsync myDevice.adbFileTransfer.downloadFileViaTempLocation(getFullPath(), getSize(), localPath, progress, null)
      } else {
        return@catchingAsync Futures.immediateFailedFuture<Unit>(syncException)
      }
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
    val futureShouldCreateRemote = myDevice.taskExecutor.executeAsync { isDeviceSuAndNotRoot }
    return myDevice.taskExecutor.transformAsync(futureShouldCreateRemote) { shouldCreateRemote: Boolean? ->
      assert(shouldCreateRemote != null)
      if (shouldCreateRemote!!) {
        val futureTouchFile = myDevice.adbFileOperations.touchFileAsDefaultUser(remotePath)
        val futureUpload = myDevice.taskExecutor.transformAsync(
          futureTouchFile,
          AsyncFunction { aVoid: Unit ->  // If file creation succeeded, assume a regular upload will succeed.
            myDevice.adbFileTransfer.uploadFile(localPath, remotePath, progress)
          }
        )
        return@transformAsync myDevice.taskExecutor.catchingAsync(
          futureUpload, AdbShellCommandException::class.java
        ) { error: AdbShellCommandException? ->  // If file creation failed, use an upload via temp. directory (using "su").
          myDevice.adbFileTransfer.uploadFileViaTempLocation(localPath, remotePath, progress, null)
        }
      } else {
        // Regular upload if root or su not supported (i.e. user devices)
        return@transformAsync myDevice.adbFileTransfer.uploadFile(localPath, remotePath, progress)
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
    private get() = myDevice.capabilities.supportsSuRootCommand() && !myDevice.capabilities.isRoot

  companion object {
    private fun isSyncPermissionError(pullError: SyncException): Boolean {
      return pullError.errorCode == SyncException.SyncError.NO_REMOTE_OBJECT ||
        pullError.errorCode == SyncException.SyncError.TRANSFER_PROTOCOL_ERROR
    }
  }
}