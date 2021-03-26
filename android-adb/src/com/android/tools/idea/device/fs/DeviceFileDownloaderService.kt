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
import com.intellij.openapi.project.Project

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
   * Downloads the file corresponding to the [DeviceFileId] passed as argument, from the device to the local machine.
   */
  fun downloadFile(deviceFileId: DeviceFileId, downloadProgress: DownloadProgress): ListenableFuture<DownloadedFileData>
}