/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.sqlite

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.device.fs.DeviceFileDownloaderService
import com.android.tools.idea.device.fs.DownloadProgress
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.testing.runDispatching
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import java.io.IOException

class OfflineDatabaseManagerTest : LightPlatformTestCase() {

  private lateinit var deviceFileDownloaderService: DeviceFileDownloaderService

  private lateinit var processDescriptor: ProcessDescriptor
  private lateinit var liveDatabaseId: SqliteDatabaseId.LiveSqliteDatabaseId
  private lateinit var fileDatabaseId: SqliteDatabaseId.FileSqliteDatabaseId

  private lateinit var offlineDatabaseManager: OfflineDatabaseManager

  override fun setUp() {
    super.setUp()

    processDescriptor = object : ProcessDescriptor {
      override val manufacturer = "manufacturer"
      override val model = "model"
      override val serial = "serial"
      override val processName = "processName"
      override val isEmulator = false
    }

    liveDatabaseId = SqliteDatabaseId.fromLiveDatabase(
      "/data/user/0/com.example.package/databases/db-file", 0
    ) as SqliteDatabaseId.LiveSqliteDatabaseId

    val virtualFile = mock<VirtualFile>()
    `when`(virtualFile.path).thenReturn("/data/data/com.example.package/databases/db-file")
    fileDatabaseId = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(virtualFile)) as SqliteDatabaseId.FileSqliteDatabaseId

    deviceFileDownloaderService = mock()

    offlineDatabaseManager = OfflineDatabaseManagerImpl(
      project,
      deviceFileDownloaderService
    )
  }

  fun testOpenOfflineDatabases() {
    // Prepare
    val file1 = mock<VirtualFile>()
    val file2 = mock<VirtualFile>()
    val file3 = mock<VirtualFile>()

    `when`(deviceFileDownloaderService.downloadFiles(any(), any(), any())).thenReturn(
      Futures.immediateFuture(
        mapOf(
          "/data/data/com.example.package/databases/db-file" to file1,
          "/data/data/com.example.package/databases/db-file-shm" to file2,
          "/data/data/com.example.package/databases/db-file-wal" to file3
        )
      )
    )

    // Act
    val offlineDatabaseData = runDispatching { offlineDatabaseManager.loadDatabaseFileData(processDescriptor, liveDatabaseId) }

    // Assert
    verify(deviceFileDownloaderService).downloadFiles(
      eq("model [serial]"),
      eq(listOf(
        "/data/data/com.example.package/databases/db-file",
        "/data/data/com.example.package/databases/db-file-shm",
        "/data/data/com.example.package/databases/db-file-wal"
      )),
      any(DownloadProgress::class.java)
    )

    assertEquals(DatabaseFileData(file1, listOf(file2, file3)), offlineDatabaseData)
  }

  fun testOpenOfflineDatabaseNoMainFileThrows() {
    // Prepare
    val file2 = mock<VirtualFile>()
    val file3 = mock<VirtualFile>()

    `when`(deviceFileDownloaderService.downloadFiles(any(), any(), any())).thenReturn(
      Futures.immediateFuture(
        mapOf(
          "/data/data/com.example.package/databases/db-file-shm" to file2,
          "/data/data/com.example.package/databases/db-file-wal" to file3
        )
      )
    )

    // Act
    runDispatching {
      try {
        offlineDatabaseManager.loadDatabaseFileData(processDescriptor, liveDatabaseId)
        fail()
      }
      catch (e: IOException) { }
      catch (e: Throwable) {
        fail("Expected IOException, but got Throwable")
      }
    }
  }
}