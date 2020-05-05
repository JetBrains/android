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

import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFuture
import com.android.tools.idea.device.fs.DeviceFileId
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.fileType.SqliteTestUtil
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorController
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorModel
import com.android.tools.idea.sqlite.model.LiveSqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.getAllDatabaseIds
import com.android.tools.idea.testing.runDispatching
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import junit.framework.TestCase
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
  private lateinit var model: MockDatabaseInspectorModel

  private var databaseToClose: SqliteDatabase? = null

  override fun setUp() {
    super.setUp()

    sqliteUtil = SqliteTestUtil(
      IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()

    sqliteFile1 = sqliteUtil.createTestSqliteDatabase("db1.db")
    DeviceFileId("deviceId", "filePath").storeInVirtualFile(sqliteFile1)

    model = MockDatabaseInspectorModel()
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

  fun testStopSessionClosesAllDatabase() {
    // Prepare
    val fileDatabase = pumpEventsAndWaitForFuture(databaseInspectorProjectService.openSqliteDatabase(sqliteFile1))
    databaseToClose = fileDatabase

    val connection1 = mock(DatabaseConnection::class.java)
    `when`(connection1.close()).thenReturn(Futures.immediateFuture(Unit))

    val connection2 = mock(DatabaseConnection::class.java)
    `when`(connection2.close()).thenReturn(Futures.immediateFuture(Unit))

    pumpEventsAndWaitForFuture(databaseInspectorProjectService.openSqliteDatabase(
      LiveSqliteDatabase(SqliteDatabaseId.fromLiveDatabase("db1", 1), connection1)
    ))

    pumpEventsAndWaitForFuture(databaseInspectorProjectService.openSqliteDatabase(
      LiveSqliteDatabase(SqliteDatabaseId.fromLiveDatabase("db2", 2), connection2)
    ))

    // Act
    runDispatching {
      databaseInspectorProjectService.stopAppInspectionSession()
    }

    // Assert
    assertEmpty(model.getAllDatabaseIds())
  }

  fun testStopSessionsRemovesDatabaseInspectorClientChannelFromController() {
    // Prepare
    val clientCommandsChannel = object : DatabaseInspectorClientCommandsChannel {
      override fun keepConnectionsOpen(keepOpen: Boolean): ListenableFuture<Boolean?> = Futures.immediateFuture(null)
    }

    databaseInspectorProjectService.startAppInspectionSession(null, clientCommandsChannel)

    // Act
    runDispatching {
      databaseInspectorProjectService.stopAppInspectionSession()
    }

    // Assert
    verify(mockSqliteController).setDatabaseInspectorClientCommandsChannel(clientCommandsChannel)
    verify(mockSqliteController).setDatabaseInspectorClientCommandsChannel(null)
  }

  fun testDatabasePossiblyChangedNotifiesController() {
    // Act
    runDispatching {
      databaseInspectorProjectService.databasePossiblyChanged()
    }

    // Assert
    runBlocking {
      verify(mockSqliteController).databasePossiblyChanged()
    }
  }

  fun testHandleDatabaseClosedClosesDatabase() {
    // Prepare
    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)
    val databaseId2 = SqliteDatabaseId.fromLiveDatabase("db2", 2)

    val connection = mock(DatabaseConnection::class.java)
    `when`(connection.close()).thenReturn(Futures.immediateFuture(Unit))

    pumpEventsAndWaitForFuture(
      databaseInspectorProjectService.openSqliteDatabase(LiveSqliteDatabase(databaseId1, connection))
    )
    pumpEventsAndWaitForFuture(
      databaseInspectorProjectService.openSqliteDatabase(LiveSqliteDatabase(databaseId2, connection))
    )

    // Act
    databaseInspectorProjectService.handleDatabaseClosed(1)

    // Assert
    assertSize(1, model.getOpenDatabaseIds())
    TestCase.assertEquals(databaseId2, model.getOpenDatabaseIds().first())
    assertSize(1, model.getCloseDatabaseIds())
    assertEquals(databaseId1, model.getCloseDatabaseIds().first())
  }
}