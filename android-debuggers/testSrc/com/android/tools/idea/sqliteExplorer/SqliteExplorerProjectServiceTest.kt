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
package com.android.tools.idea.sqliteExplorer

import com.android.testutils.MockitoKt.any
import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFutureException
import com.android.tools.idea.device.fs.DeviceFileDownloaderService
import com.android.tools.idea.device.fs.DeviceFileId
import com.android.tools.idea.device.fs.DownloadProgress
import com.android.tools.idea.device.fs.DownloadedFileData
import com.android.tools.idea.editors.sqlite.SqliteTestUtil
import com.android.tools.idea.sqlite.SqliteService
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorModel
import com.android.tools.idea.sqlite.mocks.MockSqliteController
import com.android.tools.idea.sqlite.mocks.MockToolWindow
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.registerServiceInstance
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import java.util.function.Consumer

class SqliteExplorerProjectServiceTest : PlatformTestCase() {
  private lateinit var sqliteUtil: SqliteTestUtil
  private lateinit var sqliteFile1: VirtualFile
  private lateinit var mockDatabase: SqliteDatabase
  private lateinit var sqliteExplorerProjectService: SqliteExplorerProjectService
  private lateinit var mockSqliteController: MockSqliteController
  private lateinit var fileOpened: VirtualFile

  private var openedDatabase: SqliteDatabase? = null

  override fun setUp() {
    super.setUp()

    sqliteUtil = SqliteTestUtil(IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()

    sqliteFile1 = sqliteUtil.createTestSqliteDatabase("db1.db")
    sqliteFile1.putUserData(DeviceFileId.KEY, DeviceFileId("deviceId", "filePath"))

    val model = MockDatabaseInspectorModel()
    mockSqliteController = spy(MockSqliteController(model))

    val mockToolWindowManager = mock(ToolWindowManager::class.java)
    `when`(mockToolWindowManager.getToolWindow(any(String::class.java))).thenReturn(MockToolWindow())

    mockDatabase = SqliteDatabase("db", mock(SqliteService::class.java))

    val fileOpener = Consumer<VirtualFile> { vf -> fileOpened = vf }

    sqliteExplorerProjectService = SqliteExplorerProjectServiceImpl(
      project = project,
      toolWindowManager = mockToolWindowManager,
      fileOpener = fileOpener,
      model = model,
      createController = { mockSqliteController }
    )
  }

  override fun tearDown() {
    try {
      if (openedDatabase != null) {
        pumpEventsAndWaitForFuture(openedDatabase!!.sqliteService.closeDatabase())
      }

      sqliteUtil.tearDown()
    } finally {
      super.tearDown()
    }
  }

  // TODO(b/144904247) This test fails on pre-submit on windows. re-enable it. Need a windows machine.
  //fun testDatabaseIsClosedWhenFileIsDeleted() {
  //  // Prepare
  //  openedDatabase = pumpEventsAndWaitForFuture(sqliteExplorerProjectService.openSqliteDatabase(sqliteFile1))
  //
  //  // Act
  //  runWriteAction { sqliteFile1.delete(this) }
  //  PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  //
  //  // Assert
  //  verify(mockSqliteController).closeDatabase(openedDatabase!!)
  //}

  fun testSyncOpensFile() {
    // Prepare
    openedDatabase = pumpEventsAndWaitForFuture(sqliteExplorerProjectService.openSqliteDatabase(sqliteFile1))

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
    pumpEventsAndWaitForFuture(sqliteExplorerProjectService.sync(openedDatabase!!, mock(DownloadProgress::class.java)))

    // Assert
    assertEquals(sqliteFile1, fileOpened)
  }

  fun testSyncFileIfFileNotOpened() {
    // Prepare
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
    pumpEventsAndWaitForFutureException(sqliteExplorerProjectService.sync(mockDatabase, mock(DownloadProgress::class.java)))
  }

  fun testSyncFileHasNoMetadata() {
    // Prepare
    sqliteFile1.putUserData(DeviceFileId.KEY, null)
    openedDatabase = pumpEventsAndWaitForFuture(sqliteExplorerProjectService.openSqliteDatabase(sqliteFile1))

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
    pumpEventsAndWaitForFutureException(sqliteExplorerProjectService.sync(openedDatabase!!, mock(DownloadProgress::class.java)))
  }
}