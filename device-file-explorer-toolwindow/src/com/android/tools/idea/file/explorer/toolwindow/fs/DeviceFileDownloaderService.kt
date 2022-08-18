/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.file.explorer.toolwindow.fs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/**
 * Service used to download a file from its [DeviceFileId].
 */
interface DeviceFileDownloaderService {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): DeviceFileDownloaderService {
      return project.getService(DeviceFileDownloaderService::class.java)
    }
  }

  /**
   * Downloads on the local machine the files corresponding to [onDevicePaths], from the device corresponding to [deviceSerialNumber].
   * If the file corresponding to a path is not found, that path is skipped.
   * Returns a map where each on-device path is mapped to the corresponding VirtualFile.
   *
   * If the device corresponding to [deviceSerialNumber] is not found, throws [IllegalArgumentException].
   * If the download fails because it's not possible to execute adb commands, throws [FileDownloadFailedException].
   *
   * @param deviceSerialNumber the serial number of the device from which the files should be downloaded.
   * @param onDevicePaths the paths of files to be downloaded from the device.
   * @param downloadProgress download progress for all the files, if canceled, all the running/waiting downloads will be stopped.
   * @param localDestinationDirectory the download destination of the files, on the local machine.
   */
  suspend fun downloadFiles(
    deviceSerialNumber: String,
    onDevicePaths: List<String>,
    downloadProgress: DownloadProgress,
    localDestinationDirectory: Path
  ): Map<String, VirtualFile>

  /**
   * Deletes the [VirtualFile]s passed as argument, using the VFS.
   *
   * There is no guarantee on the order files are going to be deleted in. To avoid issues on Windows they should all be closed
   * and the caller should make sure to not delete a folder and the files inside it.
   *
   * @throws java.io.IOException in case of problems during file deletion.
   */
  suspend fun deleteFiles(virtualFiles: List<VirtualFile>)

  data class FileDownloadFailedException(override val cause: Throwable?) : RuntimeException(cause)
}