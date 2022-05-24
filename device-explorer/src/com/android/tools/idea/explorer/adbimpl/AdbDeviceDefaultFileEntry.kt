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

import com.android.tools.idea.concurrency.transformAsync
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.tools.idea.explorer.fs.FileTransferProgress
import com.google.common.util.concurrent.ListenableFuture
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
  override val entries: ListenableFuture<List<DeviceFileEntry>>
    get() =
      fileSystem.resolveMountPoint(this).transformAsync(fileSystem.taskExecutor) { it.entries }

  override fun delete(): ListenableFuture<Unit> =
    fileSystem.resolveMountPoint(this).transformAsync(fileSystem.taskExecutor) { it.delete() }

  override fun createNewFile(fileName: String): ListenableFuture<Unit> =
    fileSystem.resolveMountPoint(this).transformAsync(fileSystem.taskExecutor) { it.createNewFile(fileName) }

  override fun createNewDirectory(directoryName: String): ListenableFuture<Unit> =
    fileSystem.resolveMountPoint(this).transformAsync(fileSystem.taskExecutor) { it.createNewDirectory(directoryName) }

  override val isSymbolicLinkToDirectory: ListenableFuture<Boolean>
    get() =
      fileSystem.resolveMountPoint(this).transformAsync(fileSystem.taskExecutor) { it.isSymbolicLinkToDirectory }

  override fun downloadFile(localPath: Path, progress: FileTransferProgress): ListenableFuture<Unit> =
    fileSystem.resolveMountPoint(this).transformAsync(fileSystem.taskExecutor) { mountPoint ->
      mountPoint.downloadFile(localPath, progress)
    }

  override fun uploadFile(localPath: Path, fileName: String, progress: FileTransferProgress): ListenableFuture<Unit> =
    fileSystem.resolveMountPoint(this).transformAsync(fileSystem.taskExecutor) { mountPoint ->
      mountPoint.uploadFile(localPath, fileName, progress)
    }
}