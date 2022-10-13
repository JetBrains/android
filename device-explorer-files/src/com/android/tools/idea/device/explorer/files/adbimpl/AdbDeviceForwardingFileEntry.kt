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
 * An abstract [AdbDeviceFileEntry] that goes through another [AdbDeviceFileEntry]
 * (see [.getForwardedFileEntry]) for its file operations.
 *
 * This class should be extended by [AdbDeviceFileEntry] implementations that override
 * only a subset of the abstract methods, using another instance of [AdbDeviceFileEntry]
 * as the default implementation of the non-overridden methods.
 */
abstract class AdbDeviceForwardingFileEntry(
  private val forwardedFileEntry: AdbDeviceFileEntry
) : AdbDeviceFileEntry(forwardedFileEntry.fileSystem, forwardedFileEntry.myEntry, forwardedFileEntry.parent) {

  override suspend fun entries(): List<DeviceFileEntry> =
    forwardedFileEntry.entries()

  override suspend fun delete() {
    return forwardedFileEntry.delete()
  }

  override suspend fun createNewFile(fileName: String) {
    return forwardedFileEntry.createNewFile(fileName)
  }

  override suspend fun createNewDirectory(directoryName: String) {
    return forwardedFileEntry.createNewDirectory(directoryName)
  }

  override suspend fun isSymbolicLinkToDirectory(): Boolean =
    forwardedFileEntry.isSymbolicLinkToDirectory()

  override suspend fun downloadFile(localPath: Path, progress: FileTransferProgress) {
    forwardedFileEntry.downloadFile(localPath, progress)
  }

  override suspend fun uploadFile(localPath: Path, fileName: String, progress: FileTransferProgress) {
    forwardedFileEntry.uploadFile(localPath, fileName, progress)
  }
}