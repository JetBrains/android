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
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceFileDownloaderService
import com.android.tools.idea.file.explorer.toolwindow.fs.DownloadProgress
import com.android.tools.idea.io.IdeFileService
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.testing.runDispatching
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.verify

class FileDatabaseManagerTest : LightPlatformTestCase() {

  private lateinit var deviceFileDownloaderService: DeviceFileDownloaderService

  private lateinit var processDescriptor: ProcessDescriptor
  private lateinit var liveDatabaseId: SqliteDatabaseId.LiveSqliteDatabaseId
  private lateinit var fileDatabaseId: SqliteDatabaseId.FileSqliteDatabaseId

  private lateinit var fileDatabaseManager: FileDatabaseManager

  private val edtDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()

  override fun setUp() {
    super.setUp()

    processDescriptor = StubProcessDescriptor()

    liveDatabaseId =
      SqliteDatabaseId.fromLiveDatabase("/data/user/0/com.example.package/databases/db-file", 0)
        as SqliteDatabaseId.LiveSqliteDatabaseId

    val virtualFile = mock<VirtualFile>()
    whenever(virtualFile.path).thenReturn("/data/data/com.example.package/databases/db-file")
    fileDatabaseId =
      SqliteDatabaseId.fromFileDatabase(DatabaseFileData(virtualFile))
        as SqliteDatabaseId.FileSqliteDatabaseId

    deviceFileDownloaderService = mock()

    fileDatabaseManager =
      FileDatabaseManagerImpl(project, edtDispatcher, deviceFileDownloaderService)
  }

  fun testOpenOfflineDatabases() = runBlocking {
    // Prepare
    val file1 = MockVirtualFile("f1")
    val file2 = MockVirtualFile("f2")
    val file3 = MockVirtualFile("f3")

    whenever(deviceFileDownloaderService.downloadFiles(any(), any(), any(), any()))
      .thenReturn(
        mapOf(
          "/data/data/com.example.package/databases/db-file" to file1,
          "/data/data/com.example.package/databases/db-file-shm" to file2,
          "/data/data/com.example.package/databases/db-file-wal" to file3
        )
      )

    // Act
    val offlineDatabaseData = runDispatching {
      fileDatabaseManager.loadDatabaseFileData("processName", processDescriptor, liveDatabaseId)
    }

    // Assert
    verify(deviceFileDownloaderService)
      .downloadFiles(
        eq("serial"),
        eq(
          listOf(
            "/data/data/com.example.package/databases/db-file",
            "/data/data/com.example.package/databases/db-file-shm",
            "/data/data/com.example.package/databases/db-file-wal"
          )
        ),
        any(DownloadProgress::class.java),
        eq(IdeFileService("database-inspector").cacheRoot)
      )

    assertEquals(DatabaseFileData(file1, listOf(file2, file3)), offlineDatabaseData)
  }

  fun testOpenOfflineDatabaseNoMainFileThrows() = runBlocking {
    // Prepare
    val file2 = mock<VirtualFile>()
    val file3 = mock<VirtualFile>()

    whenever(deviceFileDownloaderService.downloadFiles(any(), any(), any(), any()))
      .thenReturn(
        mapOf(
          "/data/data/com.example.package/databases/db-file-shm" to file2,
          "/data/data/com.example.package/databases/db-file-wal" to file3
        )
      )

    // Act
    runDispatching {
      try {
        fileDatabaseManager.loadDatabaseFileData("processName", processDescriptor, liveDatabaseId)
        fail()
      } catch (e: FileDatabaseException) {} catch (e: Throwable) {
        fail("Expected IOException, but got Throwable")
      }
    }
  }

  fun testFileDownloadFailedExceptionIsHandled() = runBlocking {
    // Prepare
    whenever(deviceFileDownloaderService.downloadFiles(any(), any(), any(), any()))
      .thenThrow(DeviceFileDownloaderService.FileDownloadFailedException::class.java)

    // Act
    runDispatching {
      try {
        fileDatabaseManager.loadDatabaseFileData("processName", processDescriptor, liveDatabaseId)
        fail()
      } catch (e: FileDatabaseException) {} catch (e: Throwable) {
        fail("Expected IOException, but got Throwable")
      }
    }
  }
}
