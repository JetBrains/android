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
package com.android.tools.idea.device.explorer.files.adbimpl

import com.android.adblib.AdbFailResponseException
import com.android.ddmlib.SyncException
import com.android.tools.idea.adb.AdbShellCommandException
import com.android.tools.idea.device.explorer.files.fs.DeviceFileEntry
import com.android.tools.idea.device.explorer.files.fs.FileTransferProgress
import kotlinx.coroutines.withContext
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
  override suspend fun entries(): List<DeviceFileEntry> =
    fileSystem.adbFileListing.getChildrenRunAs(myEntry, myRunAs).map { AdbDeviceDefaultFileEntry(fileSystem, it, this) }

  override suspend fun delete() =
    if (isDirectory) {
      fileSystem.adbFileOperations.deleteRecursiveRunAs(fullPath, myRunAs)
    } else {
      fileSystem.adbFileOperations.deleteFileRunAs(fullPath, myRunAs)
    }

  override suspend fun createNewFile(fileName: String) =
    fileSystem.adbFileOperations.createNewFileRunAs(fullPath, fileName, myRunAs)

  override suspend fun createNewDirectory(directoryName: String) =
    fileSystem.adbFileOperations.createNewDirectoryRunAs(fullPath, directoryName, myRunAs)

  override suspend fun isSymbolicLinkToDirectory(): Boolean =
    fileSystem.adbFileListing.isDirectoryLinkRunAs(myEntry, myRunAs)

  override suspend fun downloadFile(
    localPath: Path,
    progress: FileTransferProgress
  ) =
    // Note: First try to download the file as the default user. If we get a permission error,
    //       download the file via a temp. directory using the "su 0" user.
    try {
      fileSystem.adbFileTransfer.downloadFile(myEntry, localPath, progress)
    } catch (syncException: IOException) {
      if (syncException is AdbFailResponseException && isDeviceSuAndNotRoot()) {
        fileSystem.adbFileTransfer.downloadFileViaTempLocation(fullPath, size, localPath, progress, null)
      } else {
        throw syncException
      }
    }

  override suspend fun uploadFile(
    localPath: Path,
    fileName: String,
    progress: FileTransferProgress
  ) {
    val remotePath = AdbPathUtil.resolve(myEntry.fullPath, fileName)

    // If the device is *not* root, but supports "su 0", the ADB Sync service may not have the
    // permissions upload the local file directly to the remote location.
    // Given https://code.google.com/p/android/issues/detail?id=241157, we should not rely on the error
    // returned by the "upload" service, because a permission error is only returned *after* transferring
    // the whole file.
    // So, instead we "touch" the file and either use a regular upload if it succeeded or an upload
    // via the temp directory if it failed.
    if (isDeviceSuAndNotRoot()) {
      try {
        fileSystem.adbFileOperations.touchFileAsDefaultUser(remotePath)
        fileSystem.adbFileTransfer.uploadFile(localPath, remotePath, progress)
      } catch(e : AdbShellCommandException) {
        fileSystem.adbFileTransfer.uploadFileViaTempLocation(localPath, remotePath, progress, null)
      }
    } else {
      // Regular upload if root or su not supported (i.e. user devices)
      fileSystem.adbFileTransfer.uploadFile(localPath, remotePath, progress)
    }
  }

  private suspend fun isDeviceSuAndNotRoot(): Boolean =
    withContext(fileSystem.dispatcher) {
      fileSystem.capabilities.supportsSuRootCommand() && !fileSystem.capabilities.isRoot()
    }

  companion object {
    private fun isSyncPermissionError(pullError: SyncException): Boolean {
      return pullError.errorCode == SyncException.SyncError.NO_REMOTE_OBJECT ||
        pullError.errorCode == SyncException.SyncError.TRANSFER_PROTOCOL_ERROR
    }
  }
}