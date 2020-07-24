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

import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.device.fs.DeviceFileId
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.live.LiveDatabaseConnection
import com.android.tools.idea.sqlite.fileType.SqliteTestUtil
import com.android.tools.idea.sqlite.mocks.FakeDatabaseInspectorController
import com.android.tools.idea.sqlite.mocks.OpenDatabaseInspectorModel
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.getAllDatabaseIds
import com.android.tools.idea.sqlite.repository.DatabaseRepositoryImpl
import com.android.tools.idea.testing.runDispatching
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.concurrency.SameThreadExecutor
import junit.framework.TestCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.jetbrains.ide.PooledThreadExecutor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import java.util.function.Consumer

class DatabaseInspectorProjectServiceTest : PlatformTestCase() {
  private lateinit var sqliteUtil: SqliteTestUtil
  private lateinit var sqliteFile1: VirtualFile
  private lateinit var databaseInspectorProjectService: DatabaseInspectorProjectService
  private lateinit var databaseInspectorController: FakeDatabaseInspectorController
  private lateinit var fileOpened: VirtualFile
  private lateinit var model: OpenDatabaseInspectorModel
  private lateinit var repository: DatabaseRepositoryImpl

  private val taskExecutor = PooledThreadExecutor.INSTANCE
  private val scope = CoroutineScope(SameThreadExecutor.INSTANCE.asCoroutineDispatcher())

  override fun setUp() {
    super.setUp()

    sqliteUtil = SqliteTestUtil(
      IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()

    sqliteFile1 = sqliteUtil.createTestSqliteDatabase("db1.db")
    DeviceFileId("deviceId", "filePath").storeInVirtualFile(sqliteFile1)

    repository = DatabaseRepositoryImpl(project, EdtExecutorService.getInstance())
    model = OpenDatabaseInspectorModel()
    databaseInspectorController = spy(FakeDatabaseInspectorController(repository, model))

    val fileOpener = Consumer<VirtualFile> { vf -> fileOpened = vf }

    databaseInspectorProjectService = DatabaseInspectorProjectServiceImpl(
      project = project,
      fileOpener = fileOpener,
      model = model,
      createController = { _, _ -> databaseInspectorController }
    )
  }

  override fun tearDown() {
    try {
      sqliteUtil.tearDown()
    } finally {
      super.tearDown()
    }
  }

  fun testStopSessionClosesAllDatabase() {
    // Prepare
    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)
    val databaseId2 = SqliteDatabaseId.fromLiveDatabase("db2", 2)
    val connection1 = LiveDatabaseConnection(
      testRootDisposable,
      DatabaseInspectorMessenger(mock(AppInspectorClient.CommandMessenger::class.java), scope, taskExecutor),
      1,
      EdtExecutorService.getInstance()
    )
    val connection2 = LiveDatabaseConnection(
      testRootDisposable,
      DatabaseInspectorMessenger(mock(AppInspectorClient.CommandMessenger::class.java), scope, taskExecutor),
      2,
      EdtExecutorService.getInstance()
    )

    pumpEventsAndWaitForFuture(databaseInspectorProjectService.openSqliteDatabase(databaseId1, connection1))
    pumpEventsAndWaitForFuture(databaseInspectorProjectService.openSqliteDatabase(databaseId2, connection2))

    // Act
    runDispatching {
      databaseInspectorProjectService.stopAppInspectionSession()
    }

    // Assert
    assertEmpty(model.getAllDatabaseIds())
  }

  fun testStopSessionsRemovesDatabaseInspectorClientChannelAndAppInspectionServicesFromController() {
    // Prepare
    val clientCommandsChannel = object : DatabaseInspectorClientCommandsChannel {
      override fun keepConnectionsOpen(keepOpen: Boolean): ListenableFuture<Boolean?> = Futures.immediateFuture(null)
    }

    val appInspectionServices = mock(AppInspectionIdeServices::class.java)

    databaseInspectorProjectService.startAppInspectionSession(null, clientCommandsChannel, appInspectionServices)

    // Act
    runDispatching {
      databaseInspectorProjectService.stopAppInspectionSession()
    }

    // Assert
    verify(databaseInspectorController).setDatabaseInspectorClientCommandsChannel(clientCommandsChannel)
    verify(databaseInspectorController).setDatabaseInspectorClientCommandsChannel(null)
    verify(databaseInspectorController).setAppInspectionServices(appInspectionServices)
    verify(databaseInspectorController).setAppInspectionServices(null)
  }

  fun testDatabasePossiblyChangedNotifiesController() {
    // Act
    runDispatching {
      databaseInspectorProjectService.databasePossiblyChanged()
    }

    // Assert
    runBlocking {
      verify(databaseInspectorController).databasePossiblyChanged()
    }
  }

  fun testHandleDatabaseClosedClosesDatabase() {
    // Prepare
    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)
    val databaseId2 = SqliteDatabaseId.fromLiveDatabase("db2", 2)

    val connection = LiveDatabaseConnection(
      testRootDisposable,
      DatabaseInspectorMessenger(mock(AppInspectorClient.CommandMessenger::class.java), scope, taskExecutor),
      0,
      EdtExecutorService.getInstance()
    )

    pumpEventsAndWaitForFuture(databaseInspectorProjectService.openSqliteDatabase(databaseId1, connection))
    pumpEventsAndWaitForFuture(databaseInspectorProjectService.openSqliteDatabase(databaseId2, connection))

    // Act
    databaseInspectorProjectService.handleDatabaseClosed(databaseId1)

    // Assert
    assertSize(1, model.getOpenDatabaseIds())
    TestCase.assertEquals(databaseId2, model.getOpenDatabaseIds().first())
    assertSize(1, model.getCloseDatabaseIds())
    assertEquals(databaseId1, model.getCloseDatabaseIds().first())
  }

  fun testClosedDatabaseWithoutOpenDatabaseAddsClosedDatabase() {
    // Prepare
    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)

    val connection = mock(DatabaseConnection::class.java)
    `when`(connection.close()).thenReturn(Futures.immediateFuture(Unit))

    // Act
    databaseInspectorProjectService.handleDatabaseClosed(databaseId1)

    // Assert
    assertSize(0, model.getOpenDatabaseIds())
    assertSize(1, model.getCloseDatabaseIds())
    assertEquals(databaseId1, model.getCloseDatabaseIds().first())
  }
}