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
package com.android.tools.idea.explorer

import com.android.tools.idea.device.fs.DeviceFileDownloaderService
import com.android.tools.idea.device.fs.DeviceFileId
import com.android.tools.idea.device.fs.DownloadedFileData
import com.intellij.openapi.project.Project
import java.util.concurrent.Future

class DeviceFileDownloaderServiceImpl(
  private val project: Project,
  private val DeviceExplorerFileManager: DeviceExplorerFileManager,
  private val fileManager: DeviceExplorerFileManager) : DeviceFileDownloaderService {

  override fun downloadFile(deviceFileId: DeviceFileId): Future<DownloadedFileData> {
    TODO("do in next CL. " +
         "Use ADbDeviceFileSystemService to resolve a DeviceFileEntry from a DeviceFileId, " +
         "then use file manager to download the file."
    )
  }
}