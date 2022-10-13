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

import com.android.tools.idea.device.explorer.files.fs.DeviceFileEntry
import com.android.tools.idea.device.explorer.files.fs.FileTransferProgress
import java.nio.file.Path

/**
 * A [AdbDeviceFileEntry] that goes through the file system mounting points (see [AdbDeviceFileSystem.resolveMountPoint])
 * for its file operations.
 */
class AdbDeviceDefaultFileEntry(
  device: AdbDeviceFileSystem,
  entry: AdbFileListingEntry,
  parent: AdbDeviceFileEntry?
) : AdbDeviceFileEntry(device, entry, parent) {
  override suspend fun entries(): List<DeviceFileEntry> =
    fileSystem.resolveMountPoint(this).entries()

  override suspend fun delete() =
    fileSystem.resolveMountPoint(this).delete()

  override suspend fun createNewFile(fileName: String) =
    fileSystem.resolveMountPoint(this).createNewFile(fileName)

  override suspend fun createNewDirectory(directoryName: String) =
    fileSystem.resolveMountPoint(this).createNewDirectory(directoryName)

  override suspend fun isSymbolicLinkToDirectory(): Boolean =
    fileSystem.resolveMountPoint(this).isSymbolicLinkToDirectory()

  override suspend fun downloadFile(localPath: Path, progress: FileTransferProgress) =
    fileSystem.resolveMountPoint(this).downloadFile(localPath, progress)

  override suspend fun uploadFile(localPath: Path, fileName: String, progress: FileTransferProgress) =
    fileSystem.resolveMountPoint(this).uploadFile(localPath, fileName, progress)
}