/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.device.fs

import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Service used to download a file from its [DeviceFileId].
 */
interface DeviceFileDownloaderService {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): DeviceFileDownloaderService {
      return ServiceManager.getService(project, DeviceFileDownloaderService::class.java)
    }
  }

  /**
   * Downloads the files corresponding to [onDevicePaths], from the device [deviceId] to the local machine.
   * If the file corresponding to a path is not found, that path is skipped.
   * Returns a map where each on-device path is mapped to the corresponding VirtualFile.
   *
   * If the device corresponding to [deviceId] is not found, the future fails with IllegalArgumentException.
   *
   * [downloadProgress] is a download progress for all the files, if canceled, all the running/waiting downloads will be stopped.
   */
  fun downloadFiles(
    deviceId: String,
    onDevicePaths: List<String>,
    downloadProgress: DownloadProgress
  ): ListenableFuture<Map<String, VirtualFile>>

  /**
   * Deletes the [VirtualFile]s passed as argument, using the VFS.
   *
   * The returned future fails with IOException in case of problems during file deletion.
   *
   * There is no guarantee on the order files are going to be deleted in. To avoid issues on Windows they should all be closed
   * and the caller should make sure to not delete a folder and the files inside it.
   */
  fun deleteFiles(virtualFiles: List<VirtualFile>): ListenableFuture<Unit>
}