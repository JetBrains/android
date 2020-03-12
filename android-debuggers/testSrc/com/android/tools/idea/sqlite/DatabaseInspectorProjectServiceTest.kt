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
package com.android.tools.idea.sqlite

import com.android.testutils.MockitoKt.any
import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFutureException
import com.android.tools.idea.device.fs.DeviceFileDownloaderService
import com.android.tools.idea.device.fs.DeviceFileId
import com.android.tools.idea.device.fs.DownloadProgress
import com.android.tools.idea.device.fs.DownloadedFileData
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.fileType.SqliteTestUtil
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorController
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorModel
import com.android.tools.idea.sqlite.model.FileSqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.registerServiceInstance
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import java.util.function.Consumer

class DatabaseInspectorProjectServiceTest : PlatformTestCase() {
  private lateinit var sqliteUtil: SqliteTestUtil
  private lateinit var sqliteFile1: VirtualFile
  private lateinit var databaseInspectorProjectService: DatabaseInspectorProjectService
  private lateinit var mockSqliteController: MockDatabaseInspectorController
  private lateinit var fileOpened: VirtualFile

  private var databaseToClose: SqliteDatabase? = null

  override fun setUp() {
    super.setUp()

    sqliteUtil = SqliteTestUtil(
      IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()

    sqliteFile1 = sqliteUtil.createTestSqliteDatabase("db1.db")
    DeviceFileId("deviceId", "filePath").storeInVirtualFile(sqliteFile1)

    val model = MockDatabaseInspectorModel()
    mockSqliteController = spy(MockDatabaseInspectorController(model))

    val fileOpener = Consumer<VirtualFile> { vf -> fileOpened = vf }

    databaseInspectorProjectService = DatabaseInspectorProjectServiceImpl(
      project = project,
      fileOpener = fileOpener,
      model = model,
      createController = { mockSqliteController }
    )
  }

  override fun tearDown() {
    try {
      if (databaseToClose != null) {
        pumpEventsAndWaitForFuture(databaseToClose!!.databaseConnection.close())
      }

      sqliteUtil.tearDown()
    } finally {
      super.tearDown()
    }
  }

  // TODO(b/144904247) This test fails on pre-submit on windows. re-enable it. Need a windows machine.
  fun testDatabaseIsClosedWhenFileIsDeleted() {
    if (SystemInfo.isWindows) {
      return
    }
    // Prepare
    databaseToClose = pumpEventsAndWaitForFuture(databaseInspectorProjectService.openSqliteDatabase(sqliteFile1))

    // Act
    runWriteAction { sqliteFile1.delete(this) }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    runBlocking {
      verify(mockSqliteController).closeDatabase(databaseToClose!!)
    }
  }

  fun testReDownloadOpensFile() {
    // Prepare
    val openedDatabase = pumpEventsAndWaitForFuture(databaseInspectorProjectService.openSqliteDatabase(sqliteFile1)) as FileSqliteDatabase
    databaseToClose = openedDatabase

    val mockDownloaderService = mock(DeviceFileDownloaderService::class.java)
    `when`(mockDownloaderService.downloadFile(any(DeviceFileId::class.java), any(DownloadProgress::class.java)))
      .thenReturn(Futures.immediateFuture(
        DownloadedFileData(
          DeviceFileId("deviceId", "filePath"),
          sqliteFile1, emptyList()
        )
      ))
    project.registerServiceInstance(DeviceFileDownloaderService::class.java, mockDownloaderService)

    // Act
    pumpEventsAndWaitForFuture(databaseInspectorProjectService.reDownloadAndOpenFile(openedDatabase, mock(DownloadProgress::class.java)))

    // Assert
    assertEquals(sqliteFile1, fileOpened)
  }

  fun testReDownloadFileIfFileNotOpened() {
    // Prepare
    val deviceFileId = DeviceFileId("deviceId", "filePath")
    val mockVirtualFile = mock(VirtualFile::class.java)
    deviceFileId.storeInVirtualFile(mockVirtualFile)
    val fileDatabase = FileSqliteDatabase("db", mock(DatabaseConnection::class.java), mockVirtualFile)

    val mockDownloaderService = mock(DeviceFileDownloaderService::class.java)
    `when`(mockDownloaderService.downloadFile(any(DeviceFileId::class.java), any(DownloadProgress::class.java)))
      .thenReturn(Futures.immediateFuture(DownloadedFileData(deviceFileId, sqliteFile1, emptyList())))
    project.registerServiceInstance(DeviceFileDownloaderService::class.java, mockDownloaderService)

    // Act/Assert
    pumpEventsAndWaitForFutureException(
      databaseInspectorProjectService.reDownloadAndOpenFile(fileDatabase, mock(DownloadProgress::class.java))
    )
  }

  fun testReDownloadFileHasNoMetadata() {
    // Prepare
    DeviceFileId.removeFromVirtualFile(sqliteFile1)
    val openedDatabase = pumpEventsAndWaitForFuture(databaseInspectorProjectService.openSqliteDatabase(sqliteFile1)) as FileSqliteDatabase
    databaseToClose = openedDatabase

    val mockDownloaderService = mock(DeviceFileDownloaderService::class.java)
    `when`(mockDownloaderService.downloadFile(any(DeviceFileId::class.java), any(DownloadProgress::class.java)))
      .thenReturn(Futures.immediateFuture(
        DownloadedFileData(
          DeviceFileId("deviceId", "filePath"),
          sqliteFile1, emptyList()
        )
      ))
    project.registerServiceInstance(DeviceFileDownloaderService::class.java, mockDownloaderService)

    // Act/Assert
    pumpEventsAndWaitForFutureException(
      databaseInspectorProjectService.reDownloadAndOpenFile(openedDatabase, mock(DownloadProgress::class.java)))
  }
}