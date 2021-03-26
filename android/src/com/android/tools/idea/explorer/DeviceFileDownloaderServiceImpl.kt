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

import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.device.fs.DeviceFileDownloaderService
import com.android.tools.idea.device.fs.DeviceFileId
import com.android.tools.idea.device.fs.DownloadProgress
import com.android.tools.idea.device.fs.DownloadedFileData
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystemService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.jetbrains.ide.PooledThreadExecutor

class DeviceFileDownloaderServiceImpl @NonInjectable constructor(
  private val project: Project,
  private val adbDeviceFileSystemService: AdbDeviceFileSystemService,
  private val fileManager: DeviceExplorerFileManager) : DeviceFileDownloaderService {
  constructor(project: Project) : this(project, AdbDeviceFileSystemService.getInstance(project),
                                       DeviceExplorerFileManager.getInstance(project))

  private val taskExecutor = FutureCallbackExecutor(PooledThreadExecutor.INSTANCE)

  override fun downloadFile(deviceFileId: DeviceFileId, downloadProgress: DownloadProgress): ListenableFuture<DownloadedFileData> {
    val settableFuture = SettableFuture.create<DownloadedFileData>()

    val startServiceFuture = adbDeviceFileSystemService.start { AndroidSdkUtils.getAdb(project) }
    taskExecutor.transform(startServiceFuture) {
      taskExecutor.transform(doDownloadFile(deviceFileId, downloadProgress)) { downloadedFileData ->
        settableFuture.set(downloadedFileData)
      }
    }

    return settableFuture
  }

  private fun doDownloadFile(deviceFileId: DeviceFileId, downloadProgress: DownloadProgress): ListenableFuture<DownloadedFileData> {
    return taskExecutor.transformAsync(adbDeviceFileSystemService.devices) { devices ->
      val device = devices!!.find { it.name == deviceFileId.deviceId }
      require(device != null)
      taskExecutor.transformAsync(device.getEntry(deviceFileId.devicePath)) { entry ->
        val localPath = fileManager.getDefaultLocalPathForEntry(entry!!)
        fileManager.downloadFileEntry(entry, localPath, downloadProgress)
      }
    }
  }
}
