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

import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.tools.idea.explorer.fs.FileTransferProgress
import com.google.common.util.concurrent.ListenableFuture
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

  override val entries: ListenableFuture<List<DeviceFileEntry>>
    get() = forwardedFileEntry.entries

  override fun delete(): ListenableFuture<Unit> {
    return forwardedFileEntry.delete()
  }

  override fun createNewFile(fileName: String): ListenableFuture<Unit> {
    return forwardedFileEntry.createNewFile(fileName)
  }

  override fun createNewDirectory(directoryName: String): ListenableFuture<Unit> {
    return forwardedFileEntry.createNewDirectory(directoryName)
  }

  override val isSymbolicLinkToDirectory: ListenableFuture<Boolean>
    get() = forwardedFileEntry.isSymbolicLinkToDirectory

  override fun downloadFile(localPath: Path, progress: FileTransferProgress): ListenableFuture<Unit> {
    return forwardedFileEntry.downloadFile(localPath, progress)
  }

  override fun uploadFile(localPath: Path, fileName: String, progress: FileTransferProgress): ListenableFuture<Unit> {
    return forwardedFileEntry.uploadFile(localPath, fileName, progress)
  }
}