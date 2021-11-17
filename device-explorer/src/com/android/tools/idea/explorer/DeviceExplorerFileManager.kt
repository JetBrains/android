/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.explorer

import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.tools.idea.explorer.DeviceExplorerFileManager
import com.android.tools.idea.explorer.fs.DownloadProgress
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/**
 * Manage files synchronized between devices and the local file system
 */
interface DeviceExplorerFileManager {
  /**
   * Returns the default [Path] where to store the `entry` on the local file system.
   */
  fun getDefaultLocalPathForEntry(entry: DeviceFileEntry): Path

  /**
   * Asynchronously downloads the content of a [DeviceFileEntry] to the local file system.
   *
   *
   * Returns a [ListenableFuture] that completes when the download has completed.
   * The `progress` callback is regularly notified of the current progress of the
   * download operation.
   */
  fun downloadFileEntry(
    entry: DeviceFileEntry,
    localPath: Path,
    progress: DownloadProgress
  ): ListenableFuture<VirtualFile>

  /**
   * Delete the VirtualFile passed as argument using the VFS.
   * The returned future fails with IOException in case of problems during file deletion.
   */
  fun deleteFile(virtualFile: VirtualFile): ListenableFuture<Unit>

  /**
   * Returns the [Path] to use on the local file system when saving/downloading the entry from the device to the local file system
   *
   * The relative path of the entry is resolved against `destinationPath`.
   */
  fun getPathForEntry(entry: DeviceFileEntry, destinationPath: Path): Path

  companion object {
    fun getInstance(project: Project): DeviceExplorerFileManager {
      return project.getService(DeviceExplorerFileManager::class.java)
    }
  }
}