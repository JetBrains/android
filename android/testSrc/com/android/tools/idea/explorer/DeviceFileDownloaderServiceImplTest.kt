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
import com.android.tools.idea.device.fs.DownloadProgress
import com.android.tools.idea.device.fs.DownloadedFileData
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystem
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystemService
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.AsyncTestUtils
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.nio.file.Path
import java.nio.file.Paths

class DeviceFileDownloaderServiceImplTest : AndroidTestCase() {

  lateinit var deviceFileDownloaderService: DeviceFileDownloaderService
  lateinit var mockAdbDeviceFileSystemService: AdbDeviceFileSystemService
  lateinit var mockDeviceExplorerFileManager: DeviceExplorerFileManager

  override fun setUp() {
    super.setUp()

    val path = Paths.get("test/path")

    mockAdbDeviceFileSystemService = mock(AdbDeviceFileSystemService::class.java)
    mockDeviceExplorerFileManager = object : DeviceExplorerFileManager {
      override fun getDefaultLocalPathForEntry(entry: DeviceFileEntry) = path

      override fun downloadFileEntry(
        entry: DeviceFileEntry,
        localPath: Path,
        progress: DownloadProgress
      ): ListenableFuture<DownloadedFileData> {
        return Futures.immediateFuture(
          DownloadedFileData(DeviceFileId("deviceId", "fileId"), mock(VirtualFile::class.java), emptyList())
        )
      }

      override fun openFile(entry: DeviceFileEntry, localPath: Path): ListenableFuture<Void> = Futures.immediateFuture(null)

    }

    val mockEntry = mock(DeviceFileEntry::class.java)

    val mockDevice = mock(AdbDeviceFileSystem::class.java)
    `when`(mockDevice.name).thenReturn("deviceId")
    `when`(mockDevice.getEntry("fileId")).thenReturn(Futures.immediateFuture(mockEntry))
    `when`(mockAdbDeviceFileSystemService.devices).thenReturn(Futures.immediateFuture(listOf(mockDevice)))

    `when`(mockAdbDeviceFileSystemService.start(ArgumentMatchers.any())).thenReturn(Futures.immediateFuture(null))

    deviceFileDownloaderService = DeviceFileDownloaderServiceImpl(project, mockAdbDeviceFileSystemService, mockDeviceExplorerFileManager)
  }

  fun testDownloadFile() {
    // Prepare
    val deviceFileId = DeviceFileId("deviceId", "fileId")
    val downloadProgress = mock(DownloadProgress::class.java)

    // Act
    val downloadedFileData =
      AsyncTestUtils.pumpEventsAndWaitForFuture(deviceFileDownloaderService.downloadFile(deviceFileId, downloadProgress))

    // Assert
    verify(mockAdbDeviceFileSystemService).start(ArgumentMatchers.any())

    assertEquals(downloadedFileData.deviceFileId.deviceId, "deviceId")
    assertEquals(downloadedFileData.deviceFileId.devicePath, "fileId")
  }
}