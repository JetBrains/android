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

import com.android.ddmlib.AndroidDebugBridge
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.adb.AdbFileProvider
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutureException
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.live.LiveDatabaseConnection
import com.android.tools.idea.sqlite.fileType.SqliteTestUtil
import com.android.tools.idea.sqlite.mocks.FakeDatabaseInspectorController
import com.android.tools.idea.sqlite.mocks.OpenDatabaseInspectorModel
import com.android.tools.idea.sqlite.mocks.OpenDatabaseRepository
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.getAllDatabaseIds
import com.android.tools.idea.testing.runDispatching
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.replaceService
import com.intellij.util.concurrency.EdtExecutorService
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.jetbrains.ide.PooledThreadExecutor
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify

class DatabaseInspectorProjectServiceTest : LightPlatformTestCase() {
  private lateinit var sqliteUtil: SqliteTestUtil
  private lateinit var sqliteFile1: VirtualFile
  private lateinit var databaseInspectorProjectService: DatabaseInspectorProjectServiceImpl
  private lateinit var databaseInspectorController: FakeDatabaseInspectorController
  private lateinit var model: OpenDatabaseInspectorModel
  private lateinit var repository: OpenDatabaseRepository
  private lateinit var processDescriptor: ProcessDescriptor
  private lateinit var fileDatabaseManager: FileDatabaseManager

  private val edtExecutor = EdtExecutorService.getInstance()
  private val taskExecutor = PooledThreadExecutor.INSTANCE
  private val scope = CoroutineScope(edtExecutor.asCoroutineDispatcher())

  override fun setUp() {
    super.setUp()

    registerMockAdbService()

    fileDatabaseManager = mock(FileDatabaseManager::class.java)

    sqliteUtil =
      SqliteTestUtil(IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()

    sqliteFile1 = sqliteUtil.createTestSqliteDatabase("db1.db")

    repository = OpenDatabaseRepository(project, EdtExecutorService.getInstance())
    model = OpenDatabaseInspectorModel()
    databaseInspectorController = spy(FakeDatabaseInspectorController(repository, model))

    databaseInspectorProjectService =
      DatabaseInspectorProjectServiceImpl(
        project = project,
        model = model,
        databaseRepository = repository,
        fileDatabaseManager = fileDatabaseManager,
        createController = { _, _, _, _ -> databaseInspectorController }
      )

    processDescriptor = StubProcessDescriptor()
  }

  override fun tearDown() {
    runDispatching { repository.clear() }

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
    val connection1 =
      LiveDatabaseConnection(
        testRootDisposable,
        DatabaseInspectorMessenger(mock(AppInspectorMessenger::class.java), scope, taskExecutor),
        1,
        EdtExecutorService.getInstance()
      )
    val connection2 =
      LiveDatabaseConnection(
        testRootDisposable,
        DatabaseInspectorMessenger(mock(AppInspectorMessenger::class.java), scope, taskExecutor),
        2,
        EdtExecutorService.getInstance()
      )

    pumpEventsAndWaitForFuture(
      databaseInspectorProjectService.openSqliteDatabase(databaseId1, connection1)
    )
    pumpEventsAndWaitForFuture(
      databaseInspectorProjectService.openSqliteDatabase(databaseId2, connection2)
    )

    // Act
    runDispatching(edtExecutor.asCoroutineDispatcher()) {
      databaseInspectorProjectService.stopAppInspectionSession(processDescriptor)
    }

    // Assert
    assertEmpty(model.getAllDatabaseIds())
    assertEmpty(repository.openDatabases)
  }

  fun testStopSessionsRemovesDatabaseInspectorClientChannelAndAppInspectionServicesFromController() =
    runBlocking {
      // Prepare
      val clientCommandsChannel =
        object : DatabaseInspectorClientCommandsChannel {
          override fun keepConnectionsOpen(keepOpen: Boolean): ListenableFuture<Boolean?> =
            Futures.immediateFuture(null)
          override fun acquireDatabaseLock(databaseId: Int): ListenableFuture<Int?> =
            Futures.immediateFuture(null)
          override fun releaseDatabaseLock(lockId: Int): ListenableFuture<Unit> =
            Futures.immediateFuture(null)
        }

      val appInspectionServices = mock(AppInspectionIdeServices::class.java)

      runDispatching {
        databaseInspectorProjectService.startAppInspectionSession(
          clientCommandsChannel,
          appInspectionServices,
          processDescriptor
        )
      }

      // Act
      runDispatching(edtExecutor.asCoroutineDispatcher()) {
        databaseInspectorProjectService.stopAppInspectionSession(processDescriptor)
      }

      // Assert
      verify(databaseInspectorController)
        .startAppInspectionSession(
          clientCommandsChannel,
          appInspectionServices,
          processDescriptor,
          processDescriptor.name
        )
      verify(databaseInspectorController).stopAppInspectionSession("processName", processDescriptor)
    }

  fun testDatabasePossiblyChangedNotifiesController() {
    // Act
    runDispatching { databaseInspectorProjectService.databasePossiblyChanged() }

    // Assert
    runBlocking { verify(databaseInspectorController).databasePossiblyChanged() }
  }

  fun testHandleDatabaseClosedClosesDatabase() {
    // Prepare
    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)
    val databaseId2 = SqliteDatabaseId.fromLiveDatabase("db2", 2)

    val connection =
      LiveDatabaseConnection(
        testRootDisposable,
        DatabaseInspectorMessenger(mock(AppInspectorMessenger::class.java), scope, taskExecutor),
        0,
        EdtExecutorService.getInstance()
      )

    pumpEventsAndWaitForFuture(
      databaseInspectorProjectService.openSqliteDatabase(databaseId1, connection)
    )
    pumpEventsAndWaitForFuture(
      databaseInspectorProjectService.openSqliteDatabase(databaseId2, connection)
    )

    // Act
    databaseInspectorProjectService.handleDatabaseClosed(databaseId1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertSize(1, model.getOpenDatabaseIds())
    assertEquals(databaseId2, model.getOpenDatabaseIds().first())
    assertSize(1, model.getCloseDatabaseIds())
    assertEquals(databaseId1, model.getCloseDatabaseIds().first())
    assertEquals(listOf(databaseId2), repository.openDatabases)

    runDispatching { verify(databaseInspectorController).closeDatabase(databaseId1) }
  }

  fun testClosedDatabaseWithoutOpenDatabaseAddsClosedDatabase() {
    // Prepare
    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)

    val connection = mock(DatabaseConnection::class.java)
    whenever(connection.close()).thenReturn(Futures.immediateFuture(Unit))

    // Act
    databaseInspectorProjectService.handleDatabaseClosed(databaseId1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertSize(0, model.getOpenDatabaseIds())
    assertSize(1, model.getCloseDatabaseIds())
    assertEquals(databaseId1, model.getCloseDatabaseIds().first())
  }

  fun testStartSessionsClearsDatabases() {
    // Prepare
    val clientCommandsChannel =
      object : DatabaseInspectorClientCommandsChannel {
        override fun keepConnectionsOpen(keepOpen: Boolean): ListenableFuture<Boolean?> =
          Futures.immediateFuture(null)
        override fun acquireDatabaseLock(databaseId: Int): ListenableFuture<Int?> =
          Futures.immediateFuture(null)
        override fun releaseDatabaseLock(lockId: Int): ListenableFuture<Unit> =
          Futures.immediateFuture(null)
      }

    val appInspectionServices = mock(AppInspectionIdeServices::class.java)
    model.addDatabaseSchema(SqliteDatabaseId.fromLiveDatabase("db", 0), SqliteSchema(emptyList()))

    assertSize(1, model.getAllDatabaseIds())

    // Act
    runDispatching {
      databaseInspectorProjectService.startAppInspectionSession(
        clientCommandsChannel,
        appInspectionServices,
        processDescriptor
      )
    }

    // Assert
    assertEmpty(model.getAllDatabaseIds())
  }

  fun testOpenFileDatabaseSuccess() {
    // Prepare
    val databaseFileData = DatabaseFileData(sqliteFile1)
    val databaseId = SqliteDatabaseId.fromFileDatabase(databaseFileData)

    // Act
    pumpEventsAndWaitForFuture(databaseInspectorProjectService.openSqliteDatabase(databaseFileData))
    runDispatching { repository.closeDatabase(databaseId) }

    // Verify
    runDispatching { verify(databaseInspectorController).addSqliteDatabase(databaseId) }
  }

  fun testOpenFileDatabaseFailure() {
    // Prepare
    val databaseFileData = DatabaseFileData(MockVirtualFile("not-a-sqlite-file"))

    // Act
    val error =
      pumpEventsAndWaitForFutureException(
        databaseInspectorProjectService.openSqliteDatabase(databaseFileData)
      )

    // Verify
    assertEquals(emptyList<SqliteDatabaseId>(), repository.openDatabases)
    verify(databaseInspectorController)
      .showError("Error opening database from '${databaseFileData.mainFile.path}'", error.cause)
  }

  private fun registerMockAdbService() {
    val mockAdbService = mock(AdbService::class.java)
    ApplicationManager.getApplication()
      .registerServiceInstance(AdbService::class.java, mockAdbService)
    val mockAndroidDebugBridge = mock(AndroidDebugBridge::class.java)
    whenever(mockAndroidDebugBridge.devices).thenReturn(emptyArray())
    whenever(mockAdbService.getDebugBridge(any(File::class.java)))
      .thenReturn(Futures.immediateFuture(mockAndroidDebugBridge))

    val tmpFile = createTempFile()
    val adbFileProvider = AdbFileProvider { tmpFile }
    project.replaceService(AdbFileProvider::class.java, adbFileProvider, testRootDisposable)
  }
}
