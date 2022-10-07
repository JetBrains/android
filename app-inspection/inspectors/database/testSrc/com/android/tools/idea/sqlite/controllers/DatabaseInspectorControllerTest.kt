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
package com.android.tools.idea.sqlite.controllers

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.appinspection.inspector.api.AppInspectionConnectionException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.DatabaseInspectorClientCommandsChannel
import com.android.tools.idea.sqlite.DatabaseInspectorFlagController
import com.android.tools.idea.sqlite.DatabaseInspectorProjectService
import com.android.tools.idea.sqlite.DatabaseInspectorTabProvider
import com.android.tools.idea.sqlite.FileDatabaseException
import com.android.tools.idea.sqlite.OfflineModeManager
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.StubProcessDescriptor
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.databaseConnection.live.LiveInspectorException
import com.android.tools.idea.sqlite.fileType.SqliteTestUtil
import com.android.tools.idea.sqlite.mocks.DatabaseConnectionWrapper
import com.android.tools.idea.sqlite.mocks.FakeDatabaseConnection
import com.android.tools.idea.sqlite.mocks.FakeDatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.mocks.FakeDatabaseInspectorView
import com.android.tools.idea.sqlite.mocks.FakeDatabaseInspectorViewsFactory
import com.android.tools.idea.sqlite.mocks.FakeFileDatabaseManager
import com.android.tools.idea.sqlite.mocks.FakeSchemaProvider
import com.android.tools.idea.sqlite.mocks.FakeSqliteResultSet
import com.android.tools.idea.sqlite.mocks.OpenDatabaseInspectorModel
import com.android.tools.idea.sqlite.mocks.OpenDatabaseRepository
import com.android.tools.idea.sqlite.mocks.OpenOfflineModeManager
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.RowIdName
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.createSqliteStatement
import com.android.tools.idea.sqlite.settings.DatabaseInspectorSettings
import com.android.tools.idea.sqlite.ui.mainView.AddColumns
import com.android.tools.idea.sqlite.ui.mainView.AddTable
import com.android.tools.idea.sqlite.ui.mainView.DatabaseDiffOperation
import com.android.tools.idea.sqlite.ui.mainView.IndexedSqliteColumn
import com.android.tools.idea.sqlite.ui.mainView.IndexedSqliteTable
import com.android.tools.idea.sqlite.ui.mainView.RemoveTable
import com.android.tools.idea.sqlite.ui.mainView.ViewDatabase
import com.android.tools.idea.sqlite.ui.tableView.RowDiffOperation
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.android.tools.idea.sqlite.utils.getJdbcDatabaseConnection
import com.android.tools.idea.sqlite.utils.toViewColumns
import com.android.tools.idea.testing.runDispatching
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.Futures.immediateFailedFuture
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.registerServiceInstance
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.concurrency.SameThreadExecutor
import icons.StudioIcons
import junit.framework.Assert
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InOrder
import org.mockito.Mockito
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import java.util.concurrent.Executor
import javax.swing.Icon
import javax.swing.JComponent

class DatabaseInspectorControllerTest : HeavyPlatformTestCase() {
  private lateinit var databaseInspectorView: FakeDatabaseInspectorView
  private lateinit var edtExecutor: EdtExecutorService
  private lateinit var edtDispatcher: CoroutineDispatcher
  private lateinit var taskExecutor: Executor
  private lateinit var databaseInspectorController: DatabaseInspectorControllerImpl
  private lateinit var orderVerifier: InOrder
  private lateinit var sqliteUtil: SqliteTestUtil

  private lateinit var viewsFactory: FakeDatabaseInspectorViewsFactory

  private lateinit var databaseId1: SqliteDatabaseId
  private lateinit var databaseId2: SqliteDatabaseId
  private lateinit var databaseId3: SqliteDatabaseId
  private lateinit var databaseIdFile: SqliteDatabaseId.FileSqliteDatabaseId

  private lateinit var testSqliteSchema1: SqliteSchema
  private lateinit var testSqliteSchema2: SqliteSchema
  private lateinit var testSqliteSchema3: SqliteSchema

  private lateinit var mockDatabaseConnection: DatabaseConnection
  private lateinit var realDatabaseConnection: DatabaseConnection
  private lateinit var databaseFileData: DatabaseFileData

  private val testSqliteTable = SqliteTable("testTable", arrayListOf(), null, false)
  private lateinit var sqliteResultSet: SqliteResultSet

  private lateinit var tempDirTestFixture: TempDirTestFixture

  private lateinit var databaseInspectorModel: OpenDatabaseInspectorModel
  private lateinit var databaseRepository: OpenDatabaseRepository
  private lateinit var fileDatabaseManager: FakeFileDatabaseManager
  private lateinit var offlineModeManager: OfflineModeManager

  private lateinit var trackerService: FakeDatabaseInspectorAnalyticsTracker

  private lateinit var processDescriptor: ProcessDescriptor

  private lateinit var scope: CoroutineScope
  private lateinit var sqliteFile: VirtualFile

  override fun setUp() {
    super.setUp()

    tempDirTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    tempDirTestFixture.setUp()

    viewsFactory = spy(FakeDatabaseInspectorViewsFactory())

    testSqliteSchema1 = SqliteSchema(emptyList())
    testSqliteSchema2 = SqliteSchema(emptyList())
    testSqliteSchema3 = SqliteSchema(emptyList())

    databaseInspectorView = viewsFactory.databaseInspectorView
    edtExecutor = EdtExecutorService.getInstance()
    edtDispatcher = edtExecutor.asCoroutineDispatcher()
    taskExecutor = SameThreadExecutor.INSTANCE
    scope = CoroutineScope(edtDispatcher)

    databaseInspectorModel = spy(OpenDatabaseInspectorModel())
    databaseRepository = spy(OpenDatabaseRepository(project, edtExecutor))

    trackerService = spy(FakeDatabaseInspectorAnalyticsTracker())
    project.registerServiceInstance(DatabaseInspectorAnalyticsTracker::class.java, trackerService)

    sqliteResultSet = mock(SqliteResultSet::class.java)
    whenever(sqliteResultSet.columns).thenReturn(Futures.immediateFuture(emptyList()))

    mockDatabaseConnection = mock(DatabaseConnection::class.java)
    whenever(mockDatabaseConnection.close()).thenReturn(Futures.immediateFuture(null))
    whenever(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(sqliteResultSet))

    databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1",  1)
    databaseId2 = SqliteDatabaseId.fromLiveDatabase("db2", 2)
    databaseId3 = SqliteDatabaseId.fromLiveDatabase("db", 3)

    sqliteUtil = SqliteTestUtil(IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()

    sqliteFile = sqliteUtil.createTestSqliteDatabase("db-name", "t1", listOf("c1"), emptyList(), false)
    databaseFileData = DatabaseFileData(sqliteFile)
    databaseIdFile = SqliteDatabaseId.fromFileDatabase(databaseFileData) as SqliteDatabaseId.FileSqliteDatabaseId

    fileDatabaseManager = spy(FakeFileDatabaseManager(sqliteFile))
    offlineModeManager = spy(OpenOfflineModeManager(project, fileDatabaseManager, edtDispatcher))

    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId1, mockDatabaseConnection)
      databaseRepository.addDatabaseConnection(databaseId2, mockDatabaseConnection)
      databaseRepository.addDatabaseConnection(databaseId3, mockDatabaseConnection)
      databaseRepository.addDatabaseConnection(databaseIdFile, mockDatabaseConnection)
    }

    orderVerifier = inOrder(databaseInspectorView, databaseRepository, mockDatabaseConnection, fileDatabaseManager)

    realDatabaseConnection = pumpEventsAndWaitForFuture(
      getJdbcDatabaseConnection(testRootDisposable, databaseFileData.mainFile, FutureCallbackExecutor.wrap(taskExecutor))
    )

    processDescriptor = StubProcessDescriptor()

    databaseInspectorController = DatabaseInspectorControllerImpl(
      project,
      databaseInspectorModel,
      databaseRepository,
      viewsFactory,
      fileDatabaseManager,
      offlineModeManager,
      edtExecutor,
      edtExecutor
    )
    databaseInspectorController.setUp()

    DatabaseInspectorSettings.getInstance().isOfflineModeEnabled = true
  }

  override fun tearDown() {
    Disposer.dispose(databaseInspectorController)
    try {
      pumpEventsAndWaitForFuture(realDatabaseConnection.close())
      sqliteUtil.tearDown()
    } finally {
      tempDirTestFixture.tearDown()
      super.tearDown()
    }
  }

  fun testAllTabsAreClosedOnDisposed() {
    // Prepare
    // open evaluator tab
    databaseInspectorView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // open query tab
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    verify(databaseInspectorView).openTab(
      TabId.AdHocQueryTab(1),
      "New Query [1]",
      StudioIcons.DatabaseInspector.TABLE,
      viewsFactory.sqliteEvaluatorView.component
    )
    verify(databaseInspectorView).openTab(
      TabId.TableTab(databaseId1, testSqliteTable.name),
      testSqliteTable.name,
      StudioIcons.DatabaseInspector.TABLE,
      viewsFactory.tableView.component
    )

    // Act
    Disposer.dispose(databaseInspectorController)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(databaseInspectorView).closeTab(TabId.AdHocQueryTab(1))
    verify(databaseInspectorView).closeTab(TabId.TableTab(databaseId1, testSqliteTable.name))
  }

  fun testAddSqliteDatabase() {
    // Prepare
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))

    // Act
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    // Assert
    verify(mockDatabaseConnection).readSchema()
  }

  fun testAddSqliteDatabaseFailureReadSchema() {
    // Prepare
    val exception = IllegalStateException("expected")
    whenever(mockDatabaseConnection.readSchema()).thenReturn(immediateFailedFuture(exception))

    // Act
    val result = runCatching {
      runDispatching {
        databaseInspectorController.addSqliteDatabase(databaseId1)
      }
    }

    // Assert
    assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    assertThat(result.exceptionOrNull()).hasMessageThat().isEqualTo("expected")

    runDispatching { orderVerifier.verify(databaseRepository).fetchSchema(databaseId1) }
    orderVerifier.verify(databaseInspectorView).reportError(eq("Error reading Sqlite database"), any(IllegalStateException::class.java))
    orderVerifier.verifyNoMoreInteractions()

    assertEquals("expected", databaseInspectorView.errorInvocations.first().second?.message)
  }

  fun testDisplayResultSetIsCalledForTable() {
    // Prepare
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    // Act
    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(databaseInspectorView).openTab(
      TabId.TableTab(databaseId1, testSqliteTable.name),
      testSqliteTable.name,
      StudioIcons.DatabaseInspector.TABLE,
      viewsFactory.tableView.component
    )
  }

  fun testDisplayResultSetIsCalledForEvaluatorView() {
    // Prepare
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    // Act
    databaseInspectorView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(databaseInspectorView).openTab(
      TabId.AdHocQueryTab(1),
      "New Query [1]",
      StudioIcons.DatabaseInspector.TABLE,
      viewsFactory.sqliteEvaluatorView.component
    )
  }

  fun testCloseTabIsCalledForTable() {
    // Prepare
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    whenever(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(FakeSqliteResultSet()))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    // Act
    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    databaseInspectorView.viewListeners.single().closeTabActionInvoked(TabId.TableTab(databaseId1, testSqliteTable.name))

    // Assert
    verify(viewsFactory).createTableView()
    verify(databaseInspectorView).closeTab(eq(TabId.TableTab(databaseId1, testSqliteTable.name)))
  }

  fun testCloseTabIsCalledForEvaluatorView() {
    // Prepare
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    // Act
    databaseInspectorView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    val tabId = databaseInspectorView.lastDisplayedResultSetTabId
    assert(tabId is TabId.AdHocQueryTab)
    databaseInspectorView.viewListeners.single().closeTabActionInvoked(tabId!!)

    // Assert
    verify(viewsFactory).createEvaluatorView(any(Project::class.java), any(SchemaProvider::class.java), any(TableView::class.java))
    verify(databaseInspectorView).closeTab(eq(tabId))
  }

  fun testCloseTabInvokedFromTableViewClosesTab() {
    // Prepare
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    whenever(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(FakeSqliteResultSet()))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    // Act
    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    viewsFactory.tableView.listeners.single().cancelRunningStatementInvoked()

    // Assert
    verify(viewsFactory).createTableView()
    verify(databaseInspectorView).closeTab(eq(TabId.TableTab(databaseId1, testSqliteTable.name)))
  }

  fun testCloseTabInvokedFromEvaluatorViewClosesTab() {
    // Prepare
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    whenever(mockDatabaseConnection.query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM tab")))
      .thenReturn(Futures.immediateFuture(FakeSqliteResultSet()))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    // Act
    runDispatching {
      databaseInspectorController.runSqlStatement(databaseId1, SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM tab"))
    }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    viewsFactory.tableView.listeners.single().cancelRunningStatementInvoked()

    // Assert
    verify(databaseInspectorView).closeTab(any(TabId.AdHocQueryTab::class.java))
  }

  fun testFocusTabIsCalled() {
    // Prepare
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    whenever(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(FakeSqliteResultSet()))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    // Act
    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)

    // Assert
    verify(viewsFactory).createTableView()
    verify(databaseInspectorView)
      .openTab(
        TabId.TableTab(databaseId1, testSqliteTable.name),
        testSqliteTable.name,
        StudioIcons.DatabaseInspector.TABLE,
        viewsFactory.tableView.component
      )
    verify(databaseInspectorView).focusTab(eq(TabId.TableTab(databaseId1, testSqliteTable.name)))
  }

  fun testAddNewDatabaseAlphabeticOrder() {
    // Act
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema2))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId2)
    }

    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema3))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId3)
    }

    // Assert
    orderVerifier.verify(databaseInspectorView).updateDatabases(
      listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId1, true), testSqliteSchema1, 0))
    )
    orderVerifier.verify(databaseInspectorView).updateDatabases(
      listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId2, true), testSqliteSchema2, 1))
    )
    orderVerifier.verify(databaseInspectorView).updateDatabases(
      listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId3, true), testSqliteSchema3, 0))
    )
  }

  fun testNewDatabaseIsAddedToEvaluator() {
    // Prepare
    databaseInspectorView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val evaluatorView = viewsFactory.createEvaluatorView(project, FakeSchemaProvider(), viewsFactory.tableView)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema2))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId2)
    }

    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema3))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId3)
    }

    // Assert
    verify(evaluatorView).setDatabases(listOf(), null)
    verify(evaluatorView).setDatabases(listOf(databaseId1), databaseId1)
    verify(evaluatorView).setDatabases(listOf(databaseId1, databaseId2), databaseId1)
    verify(evaluatorView).setDatabases(listOf(databaseId3, databaseId1, databaseId2), databaseId1)
  }

  fun testDatabaseIsUpdatedInEvaluatorTabAfterSchemaChanges() {
    // Prepare
    databaseInspectorView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val databaseInspectorView = viewsFactory.databaseInspectorView
    val evaluatorView = viewsFactory.sqliteEvaluatorView

    val newSchema = SqliteSchema(listOf(SqliteTable("tab", emptyList(), null, false)))

    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    // Act
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(newSchema))
    databaseInspectorView.viewListeners.first().refreshAllOpenDatabasesSchemaActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(evaluatorView).schemaChanged(databaseId1)
  }

  fun testRemoveDatabase() {
    // Prepare
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
      databaseInspectorController.addSqliteDatabase(databaseId2)
    }

    databaseInspectorView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    val evaluatorView = viewsFactory.sqliteEvaluatorView
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // verify initial state
    verify(evaluatorView).setDatabases(listOf(databaseId1, databaseId2), databaseId1)

    // Act
    runDispatching {
      databaseInspectorController.closeDatabase(databaseId1)
    }

    // Assert
    verify(mockDatabaseConnection).close()
    verify(evaluatorView).setDatabases(listOf(databaseId2), databaseId2)
  }

  fun testRemoveFileDatabase() {
    // Prepare
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseIdFile)
    }

    // Act
    runDispatching {
      databaseInspectorController.closeDatabase(databaseIdFile)
    }

    // Assert
    orderVerifier.verify(mockDatabaseConnection).close()
    runDispatching { orderVerifier.verify(fileDatabaseManager).cleanUp(databaseIdFile.databaseFileData) }
  }

  fun testTabsAssociatedWithDatabaseAreRemovedWhenDatabasedIsRemoved() {
    // Prepare
    val schema = SqliteSchema(listOf(SqliteTable("table1", emptyList(), null, false), testSqliteTable))
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(schema))
    whenever(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(FakeSqliteResultSet()))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
      databaseInspectorController.addSqliteDatabase(databaseId2)
    }

    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId2, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    runDispatching {
      databaseInspectorController.closeDatabase(databaseId1)
    }

    // Assert
    verify(databaseInspectorView).closeTab(eq(TabId.TableTab(databaseId1, testSqliteTable.name)))
    verify(databaseInspectorView, times(0)).closeTab(eq(TabId.TableTab(databaseId2, testSqliteTable.name)))
  }

  fun testAllTabsAreRemovedWhenLastDatabasedIsRemoved() {
    // Prepare
    val schema = SqliteSchema(listOf(SqliteTable("table1", emptyList(), null, false), testSqliteTable))
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(schema))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
      databaseInspectorController.addSqliteDatabase(databaseId2)
    }

    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId2, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    databaseInspectorView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val evaluatorTabId = databaseInspectorView.lastDisplayedResultSetTabId!!
    assert(evaluatorTabId is TabId.AdHocQueryTab)

    // Act
    runDispatching {
      databaseInspectorController.closeDatabase(databaseId1)
    }
    runDispatching {
      databaseInspectorController.closeDatabase(databaseId2)
    }

    // Assert
    verify(databaseInspectorView).closeTab(eq(TabId.TableTab(databaseId1, testSqliteTable.name)))
    verify(databaseInspectorView).closeTab(eq(TabId.TableTab(databaseId2, testSqliteTable.name)))
    verify(databaseInspectorView).closeTab(evaluatorTabId)
  }

  fun testUpdateExistingDatabaseAddTables() {
    // Prepare
    val schema = SqliteSchema(emptyList())
    val newSchema = SqliteSchema(listOf(SqliteTable("table", emptyList(), null,false)))
    val evaluatorView = viewsFactory.sqliteEvaluatorView

    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(schema))
    whenever(mockDatabaseConnection.execute(SqliteStatement(SqliteStatementType.INSERT, "INSERT INTO t VALUES (42)")))
      .thenReturn(Futures.immediateFuture(Unit))

    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    databaseInspectorView.viewListeners.first().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(newSchema))

    evaluatorView.listeners.forEach {
      it.onDatabaseSelected(databaseId1)
      it.sqliteStatementTextChangedInvoked("INSERT INTO t VALUES (42)")
      it.evaluateCurrentStatement()
    }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(databaseInspectorView).updateDatabaseSchema(
      ViewDatabase(databaseId1, true),
      listOf(AddTable(IndexedSqliteTable(SqliteTable("table", emptyList(), null, false), 0), emptyList()))
    )
  }

  fun testUpdateSchemaUpdatesModel() {
    // Prepare
    val databaseId = SqliteDatabaseId.fromLiveDatabase("path", 0)
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection)
    }

    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId)
    }

    // Act
    runDispatching {
      databaseInspectorController.runSqlStatement(
        databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "CREATE TABLE t2 (c1 int not null primary key)")
      )
    }

    // Assert
    val table = databaseInspectorModel.getDatabaseSchema(databaseId)!!.tables.find { it.name == "t2" }!!
    assertSize(1, table.columns)
    assertEquals(SqliteColumn("c1", SqliteAffinity.INTEGER, false, true), table.columns.first())
  }

  fun testCreateTableUpdatesSchema() {
    // Prepare
    val databaseId = SqliteDatabaseId.fromLiveDatabase("path", 0)
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection)
    }

    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId)
    }

    // Act
    runDispatching {
      databaseInspectorController.runSqlStatement(
        databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "CREATE TABLE t2 (c1 int not null primary key)")
      )
    }

    // Assert
    val column = SqliteColumn("c1", SqliteAffinity.INTEGER, false, true)
    val table = SqliteTable("t2", listOf(column), null, false)

    verify(databaseInspectorView).updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(AddTable(IndexedSqliteTable(table, 1), listOf(IndexedSqliteColumn(column, 0))))
    )
  }

  // TODO(b/186423143): Re-enable ALTER RENAME tests
  fun ignore_testAlterTableRenameTableUpdatesSchema() {
    // Prepare
    val databaseId = SqliteDatabaseId.fromLiveDatabase("path", 0)
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection)
    }

    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId)
    }

    // Act
    runDispatching {
      databaseInspectorController.runSqlStatement(databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t1 RENAME TO t2"))
    }

    // Assert
    val column = SqliteColumn("c1", SqliteAffinity.BLOB, true, false)
    val tableToRemove = SqliteTable("t1", listOf(column), RowIdName._ROWID_, false)
    val tableToAdd = SqliteTable("t2", listOf(column), RowIdName._ROWID_, false)

    verify(databaseInspectorView).updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(RemoveTable(tableToRemove.name), AddTable(IndexedSqliteTable(tableToAdd, 0), listOf(IndexedSqliteColumn(column, 0))))
    )
  }

  fun testAlterTableAddColumnUpdatesSchema() {
    // Prepare
    val databaseId = SqliteDatabaseId.fromLiveDatabase("path", 0)
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection)
    }

    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId)
    }

    // Act
    runDispatching {
      databaseInspectorController.runSqlStatement(databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t1 ADD c2 TEXT"))
    }

    // Assert
    val columnAlreadyThere = SqliteColumn("c1", SqliteAffinity.BLOB, true, false)
    val columnToAdd = SqliteColumn("c2", SqliteAffinity.TEXT, true, false)

    val table = SqliteTable("t1", listOf(columnAlreadyThere, columnToAdd), RowIdName._ROWID_, false)

    verify(databaseInspectorView).updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(AddColumns(table.name, listOf(IndexedSqliteColumn(columnToAdd, 1)), table))
    )
  }

  // TODO(b/186423143): Re-enable ALTER RENAME tests
  fun `ignore_test AlterTableAddColumn AlterTableRenameTable UpdatesSchema`() {
    // Prepare
    val databaseId = SqliteDatabaseId.fromLiveDatabase("path", 0)
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection)
    }

    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId)
    }

    // Act
    runDispatching {
      databaseInspectorController.runSqlStatement(databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t1 ADD c2 TEXT"))
    }

    // Assert
    val columnAlreadyThere = SqliteColumn("c1", SqliteAffinity.BLOB, true, false)
    val columnToAdd = SqliteColumn("c2", SqliteAffinity.TEXT, true, false)

    val table = SqliteTable("t1", listOf(columnAlreadyThere, columnToAdd), RowIdName._ROWID_, false)

    verify(databaseInspectorView).updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(AddColumns(table.name, listOf(IndexedSqliteColumn(columnToAdd, 1)), table))
    )

    // Act
    runDispatching {
      databaseInspectorController.runSqlStatement(databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t1 RENAME TO t2"))
    }

    // Assert
    val column1 = SqliteColumn("c1", SqliteAffinity.BLOB, true, false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, true, false)
    val tableToRemove = SqliteTable("t1", listOf(column1, column2), RowIdName._ROWID_, false)
    val tableToAdd = SqliteTable("t2", listOf(column1, column2), RowIdName._ROWID_, false)

    verify(databaseInspectorView).updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(
        RemoveTable(tableToRemove.name),
        AddTable(
          IndexedSqliteTable(tableToAdd, 0),
          listOf(IndexedSqliteColumn(column1, 0), IndexedSqliteColumn(column2, 1))
        )
      )
    )
  }

  fun testDropTableUpdatesSchema() {
    // Prepare
    val databaseId = SqliteDatabaseId.fromLiveDatabase("path", 0)
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection)
    }

    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId)
    }

    // Act
    runDispatching {
      databaseInspectorController.runSqlStatement(databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "DROP TABLE t1"))
    }

    // Assert
    val column = SqliteColumn("c1", SqliteAffinity.BLOB, true, false)
    val tableToRemove = SqliteTable("t1", listOf(column), RowIdName._ROWID_, false)

    verify(databaseInspectorView).updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(RemoveTable(tableToRemove.name))
    )
  }

  // TODO(b/186423143): Re-enable ALTER RENAME tests
  fun `ignore_test CreateTable AddColumn RenameTable AddColumn UpdatesSchema`() {
    // Prepare
    val databaseId = SqliteDatabaseId.fromLiveDatabase("path", 0)
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection)
    }

    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId)
    }

    // Act
    runDispatching {
      databaseInspectorController.runSqlStatement(databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "CREATE TABLE t0 (c1 TEXT)"))
    }

    // Assert
    val column = SqliteColumn("c1", SqliteAffinity.TEXT, true, false)
    val tableToAdd = SqliteTable("t0", listOf(column), RowIdName._ROWID_, false)

    verify(databaseInspectorView).updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(AddTable(IndexedSqliteTable(tableToAdd, 0), listOf(IndexedSqliteColumn(column, 0))))
    )

    // Act
    runDispatching {
      databaseInspectorController.runSqlStatement(databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t0 ADD c2 TEXT"))
    }

    // Assert
    val columnToAdd = SqliteColumn("c2", SqliteAffinity.TEXT, true, false)
    val table = SqliteTable("t0", listOf(column, columnToAdd), RowIdName._ROWID_, false)

    verify(databaseInspectorView).updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(AddColumns(table.name, listOf(IndexedSqliteColumn(columnToAdd, 1)), table))
    )

    // Act
    runDispatching {
      databaseInspectorController.runSqlStatement(databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t0 RENAME TO t2"))
    }

    // Assert
    val tableToRemove = SqliteTable("t0", listOf(column, columnToAdd), RowIdName._ROWID_, false)
    val tableToAdd2 = SqliteTable("t2", listOf(column, columnToAdd), RowIdName._ROWID_, false)

    verify(databaseInspectorView).updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(
        RemoveTable(tableToRemove.name),
        AddTable(
          IndexedSqliteTable(tableToAdd2, 1),
          listOf(IndexedSqliteColumn(column, 0), IndexedSqliteColumn(columnToAdd, 1))
        )
      )
    )

    // Act
    runDispatching {
      databaseInspectorController.runSqlStatement(databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t2 ADD c0 TEXT"))
    }

    // Assert
    val columnToAdd2 = SqliteColumn("c0", SqliteAffinity.TEXT, true, false)
    val table2 = SqliteTable("t2", listOf(column, columnToAdd, columnToAdd2), RowIdName._ROWID_, false)

    verify(databaseInspectorView).updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(AddColumns(table2.name, listOf(IndexedSqliteColumn(columnToAdd2, 2)), table2))
    )
  }

  fun testRefreshAllOpenDatabasesSchemaActionInvokedUpdatesSchemas() {
    // Prepare
    val sqliteSchema = SqliteSchema(listOf(SqliteTable("tab", emptyList(), null, false)))

    val sqliteSchemaUpdated = SqliteSchema(listOf(SqliteTable("tab-updated", emptyList(), null, false)))

    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(sqliteSchema))

    // Act
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
      databaseInspectorController.addSqliteDatabase(databaseId2)
    }

    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(sqliteSchemaUpdated))

    databaseInspectorView.viewListeners.first().refreshAllOpenDatabasesSchemaActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(databaseInspectorModel).addDatabaseSchema(databaseId1, sqliteSchema)
    verify(databaseInspectorModel).addDatabaseSchema(databaseId2, sqliteSchema)

    verify(databaseInspectorModel).updateSchema(databaseId1, sqliteSchemaUpdated)
    verify(databaseInspectorModel).updateSchema(databaseId2, sqliteSchemaUpdated)

    verify(databaseInspectorView).updateDatabaseSchema(
      ViewDatabase(databaseId1, true),
      listOf(
        RemoveTable("tab"),
        AddTable(IndexedSqliteTable(SqliteTable("tab-updated", emptyList(), null, false), 0), emptyList())
      )
    )

    verify(databaseInspectorView).updateDatabaseSchema(
      ViewDatabase(databaseId2, true),
      listOf(
        RemoveTable("tab"),
        AddTable(IndexedSqliteTable(SqliteTable("tab-updated", emptyList(), null, false), 0), emptyList())
      )
    )
  }

  fun testRefreshAllOpenDatabasesSchemaActionInvokedWithClosedDbs() {
    // Prepare
    val sqliteSchema = SqliteSchema(listOf(SqliteTable("tab", emptyList(), null, false)))
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(sqliteSchema))

    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }
    whenever(mockDatabaseConnection.readSchema()).thenThrow(LiveInspectorException::class.java)

    // Act
    databaseInspectorView.viewListeners.first().refreshAllOpenDatabasesSchemaActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(databaseInspectorModel).addDatabaseSchema(databaseId1, sqliteSchema)
    verify(databaseInspectorModel).removeDatabaseSchema(databaseId1)
    runDispatching { verify(databaseRepository).closeDatabase(databaseId1) }

    verify(databaseInspectorView).updateDatabases(
      listOf(
        DatabaseDiffOperation.AddDatabase(
          ViewDatabase(databaseId1, true),
          SqliteSchema(listOf(SqliteTable("tab", emptyList(), null, false))),
          0
        )
      )
    )

    verify(databaseInspectorView).updateDatabases(
      listOf(
        DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId1, false),null, 0),
        DatabaseDiffOperation.RemoveDatabase(ViewDatabase(databaseId1, true))
      )
    )
  }

  fun testWhenSchemaDiffFailsViewIsRecreated() {
    // Prepare
    val databaseId = SqliteDatabaseId.fromFileDatabase(databaseFileData)
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))

    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, mockDatabaseConnection)
    }

    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId)
    }

    whenever(databaseInspectorView.updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(AddTable(IndexedSqliteTable(testSqliteTable, 0), emptyList())))
    ).thenThrow(IllegalStateException::class.java)

    // Act
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema(listOf(testSqliteTable))))

    runDispatching {
      databaseInspectorView.viewListeners.first().refreshAllOpenDatabasesSchemaActionInvoked()
    }

    // Verify
    verify(databaseInspectorView).updateDatabases(listOf(DatabaseDiffOperation.RemoveDatabase(ViewDatabase(databaseId, true))))
    verify(databaseInspectorView).updateDatabases(
      listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId, true), SqliteSchema(listOf(testSqliteTable)), 0))
    )
  }

  fun testDisposeCancelsExecutionFuture() {
    // Prepare
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }
    val executionFuture = SettableFuture.create<SqliteResultSet>()
    whenever(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(executionFuture)

    // Act
    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    //verify that future is in use now
    verify(mockDatabaseConnection).query(any(SqliteStatement::class.java))

    databaseInspectorView.viewListeners.single().closeTabActionInvoked(TabId.TableTab(databaseId1, testSqliteTable.name))

    // Assert
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertThat(executionFuture.isCancelled).isTrue()
  }

  fun testOpenTableAnalytics() {
    // Prepare
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    // Act
    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(trackerService).trackStatementExecuted(
      AppInspectionEvent.DatabaseInspectorEvent.ConnectivityState.CONNECTIVITY_ONLINE,
      AppInspectionEvent.DatabaseInspectorEvent.StatementContext.SCHEMA_STATEMENT_CONTEXT
    )
  }

  fun testRefreshSchemaAnalytics() {
    // Act
    databaseInspectorView.viewListeners.first().refreshAllOpenDatabasesSchemaActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(trackerService).trackTargetRefreshed(AppInspectionEvent.DatabaseInspectorEvent.TargetType.SCHEMA_TARGET)
  }

  fun testDatabasePossiblyChangedUpdatesAllSchemasAndTabs() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema(emptyList())))
    whenever(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(sqliteResultSet))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
      databaseInspectorController.addSqliteDatabase(databaseId2)
    }

    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema(listOf(testSqliteTable))))

    // open tabel tab
    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    runDispatching {
      // open evaluator tab
      databaseInspectorController.runSqlStatement(databaseId1, SqliteStatement(SqliteStatementType.SELECT, "fake stmt"))
    }

    // enable live updates in table tab
    viewsFactory.tableView.listeners[0].toggleLiveUpdatesInvoked()
    // enable live updates in evaluator tab
    viewsFactory.tableView.listeners[1].toggleLiveUpdatesInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    runDispatching {
      databaseInspectorController.databasePossiblyChanged()
    }

    // Assert
    // update schemas
    verify(databaseInspectorModel).updateSchema(databaseId1, SqliteSchema(listOf(testSqliteTable)))
    verify(databaseInspectorModel).updateSchema(databaseId2, SqliteSchema(listOf(testSqliteTable)))
    verify(databaseInspectorView).updateDatabaseSchema(ViewDatabase(databaseId1, true), listOf(AddTable(IndexedSqliteTable(testSqliteTable, 0), emptyList())))
    verify(databaseInspectorView).updateDatabaseSchema(ViewDatabase(databaseId2, true), listOf(AddTable(IndexedSqliteTable(testSqliteTable, 0), emptyList())))

    // update tabs
    // each invocation is repeated twice because there are two tabs open
    // 1st invocation by setUp, 2nd by toggleLiveUpdatesInvoked, 3rd by dataPossiblyChanged
    verify(viewsFactory.tableView, times(6)).showTableColumns(sqliteResultSet._columns.toViewColumns())
    // invocation by setUp
    verify(viewsFactory.tableView, times(2)).updateRows(sqliteResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
    // 1st invocation by toggleLiveUpdatesInvoked, 2nd by dataPossiblyChanged
    verify(viewsFactory.tableView, times(4)).updateRows(emptyList())
    // invocation by setUp
    verify(viewsFactory.tableView, times(2)).startTableLoading()
  }

  fun testTableTabsAreRestored() {
    // Prepare
    val table1 = SqliteTable("table1", emptyList(), null, false)
    val table2 = SqliteTable("table2", emptyList(), null, false)
    val schema = SqliteSchema(listOf(table1, table2, testSqliteTable))
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(schema))
    whenever(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(FakeSqliteResultSet()))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId1, table1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId1, table2)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    runDispatching {
      databaseInspectorController.closeDatabase(databaseId1)
    }

    // Assert that tabs closed
    verify(databaseInspectorView).closeTab(eq(TabId.TableTab(databaseId1, testSqliteTable.name)))
    verify(databaseInspectorView).closeTab(eq(TabId.TableTab(databaseId1, table1.name)))
    verify(databaseInspectorView).closeTab(eq(TabId.TableTab(databaseId1, table2.name)))
    Mockito.reset(databaseInspectorView)

    runDispatching { databaseRepository.addDatabaseConnection(databaseId1, mockDatabaseConnection) }

    // Act: restore state and re-add db
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    // Assert that tabs are readded
    verify(databaseInspectorView)
      .openTab(
        TabId.TableTab(databaseId1, testSqliteTable.name),
        testSqliteTable.name,
        StudioIcons.DatabaseInspector.TABLE,
        viewsFactory.tableView.component
      )

    verify(databaseInspectorView)
      .openTab(
        TabId.TableTab(databaseId1, table1.name),
        table1.name,
        StudioIcons.DatabaseInspector.TABLE,
        viewsFactory.tableView.component
      )

    verify(databaseInspectorView)
      .openTab(
        TabId.TableTab(databaseId1, table2.name),
        table2.name,
        StudioIcons.DatabaseInspector.TABLE,
        viewsFactory.tableView.component
      )
  }

  fun testInMemoryDbsTableTabsAreNotRestored() {
    // Prepare
    val table1 = SqliteTable("table1", emptyList(), null, false)
    val table2 = SqliteTable("table2", emptyList(), null, false)
    val schema = SqliteSchema(listOf(table1, table2, testSqliteTable))
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(schema))
    whenever(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(FakeSqliteResultSet()))
    val inMemoryDbId = SqliteDatabaseId.fromLiveDatabase(":memory: { 123 }", 0)
    runDispatching {
      databaseRepository.addDatabaseConnection(inMemoryDbId, mockDatabaseConnection)
      databaseInspectorController.addSqliteDatabase(inMemoryDbId)
    }

    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(inMemoryDbId, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(inMemoryDbId, table1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(inMemoryDbId, table2)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    runDispatching {
      databaseInspectorController.closeDatabase(inMemoryDbId)
    }

    // Assert that tabs closed
    verify(databaseInspectorView).closeTab(eq(TabId.TableTab(inMemoryDbId, testSqliteTable.name)))
    verify(databaseInspectorView).closeTab(eq(TabId.TableTab(inMemoryDbId, table1.name)))
    verify(databaseInspectorView).closeTab(eq(TabId.TableTab(inMemoryDbId, table2.name)))
    Mockito.reset(databaseInspectorView)

    runDispatching { databaseRepository.addDatabaseConnection(inMemoryDbId, mockDatabaseConnection) }

    // Act: restore db
    runDispatching {
      databaseInspectorController.addSqliteDatabase(inMemoryDbId)
    }

    // Assert that tabs are not restored
    verify(databaseInspectorView, times(0)).openTab(
      any(TabId::class.java),
      anyString(),
      any(Icon::class.java),
      any(JComponent::class.java)
    )
  }

  fun testAdHoqQueryTabsAreRestored() {
    // Prepare
    val schema = SqliteSchema(listOf(SqliteTable("table1", emptyList(), null, false)))
    val id = SqliteDatabaseId.LiveSqliteDatabaseId("path", "name", 1)
    runDispatching { databaseRepository.addDatabaseConnection(id, FakeDatabaseConnection(schema)) }

    val selectStatement = createSqliteStatement(project, "SELECT * FROM table1")
    val insertStatement = createSqliteStatement(project, "INSERT INTO table VALUES(1)")
    // Act: open AdHoq Tab
    runDispatching {
      databaseInspectorController.addSqliteDatabase(id)
      databaseInspectorController.runSqlStatement(id, selectStatement)
      databaseInspectorController.runSqlStatement(id, insertStatement)
    }

    // Close database and re-open it
    runDispatching { databaseInspectorController.closeDatabase(id) }

    val restartedDbId = SqliteDatabaseId.LiveSqliteDatabaseId("path", "name", 2)
    val databaseConnection = DatabaseConnectionWrapper(FakeDatabaseConnection(schema))
    runDispatching {
      databaseRepository.addDatabaseConnection(restartedDbId, databaseConnection)
    }

    runDispatching {
      databaseInspectorController.addSqliteDatabase(restartedDbId)
    }

    // Verify
    // first 2 invocations are to open the tabs, second two to restore them
    verify(viewsFactory.databaseInspectorView, times(4))
      .openTab(
        any(TabId.AdHocQueryTab::class.java),
        anyString(),
        any(Icon::class.java),
        any(JComponent::class.java)
      )

    // Check that INSERT statement was not executed, even though tab was restored
    assertThat(databaseConnection.executedSqliteStatements).containsExactly(selectStatement.sqliteStatementText)
  }

  fun testUpdateSchemaAddsNewTableOnlyOnceIfCalledConcurrently() {
    // Prepare
    databaseInspectorView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val newSchema = SqliteSchema(listOf(SqliteTable("tab", emptyList(), null, false)))

    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    // Act
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(newSchema))
    databaseInspectorView.viewListeners.first().refreshAllOpenDatabasesSchemaActionInvoked()
    databaseInspectorView.viewListeners.first().refreshAllOpenDatabasesSchemaActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(databaseInspectorView, times(1)).updateDatabaseSchema(
      ViewDatabase(databaseId1, true),
      listOf(AddTable(IndexedSqliteTable(SqliteTable("tab", emptyList(), null, false), 0), emptyList()))
    )
  }

  fun testClosedDatabasesAreAddedToView() {
    // Prepare
    val db1 = SqliteDatabaseId.fromLiveDatabase("db", 1)
    databaseInspectorModel.addDatabaseSchema(db1, testSqliteSchema1)

    // Act
    databaseInspectorModel.removeDatabaseSchema(db1)

    // Assert
    verify(databaseInspectorView).updateDatabases(listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase (db1, true), testSqliteSchema1, 0)))
    verify(databaseInspectorView).updateDatabases(listOf(
      DatabaseDiffOperation.AddDatabase(ViewDatabase(db1, false), null, 0),
      DatabaseDiffOperation.RemoveDatabase(ViewDatabase (db1, true))
    ))
  }

  fun testClosedDatabasesAreRemovedOnceReopened() {
    // Prepare
    val db1 = SqliteDatabaseId.fromLiveDatabase("db", 1)

    // Act
    databaseInspectorModel.removeDatabaseSchema(db1)
    databaseInspectorModel.addDatabaseSchema(db1, testSqliteSchema1)

    // Assert
    verify(databaseInspectorView).updateDatabases(listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase (db1, false), null, 0)))
    verify(databaseInspectorView).updateDatabases(listOf(
      DatabaseDiffOperation.AddDatabase(ViewDatabase(db1, true), testSqliteSchema1, 0),
      DatabaseDiffOperation.RemoveDatabase(ViewDatabase (db1, false))
    ))
  }

  fun testKeepConnectionOpenUpdatesSuccessfully() {
    // Prepare
    val databaseInspectorClientCommandChannel = object : DatabaseInspectorClientCommandsChannel {
      override fun keepConnectionsOpen(keepOpen: Boolean): ListenableFuture<Boolean?> = Futures.immediateFuture(true)
      override fun acquireDatabaseLock(databaseId: Int): ListenableFuture<Int?> = Futures.immediateFuture(null)
      override fun releaseDatabaseLock(lockId: Int): ListenableFuture<Unit> = Futures.immediateFuture(null)
    }

    runDispatching { databaseInspectorController.startAppInspectionSession(databaseInspectorClientCommandChannel, mock(), processDescriptor, processDescriptor.name) }

    // Act
    databaseInspectorView.viewListeners.first().toggleKeepConnectionOpenActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(databaseInspectorView).updateKeepConnectionOpenButton(true)
  }

  fun testKeepConnectionOpenDoesNotUpdateIfOperationFails() {
    // Prepare
    val databaseInspectorClientCommandChannel = object : DatabaseInspectorClientCommandsChannel {
      override fun keepConnectionsOpen(keepOpen: Boolean): ListenableFuture<Boolean?> = Futures.immediateFuture(null)
      override fun acquireDatabaseLock(databaseId: Int): ListenableFuture<Int?> = Futures.immediateFuture(null)
      override fun releaseDatabaseLock(lockId: Int): ListenableFuture<Unit> = Futures.immediateFuture(null)
    }

    runDispatching { databaseInspectorController.startAppInspectionSession(databaseInspectorClientCommandChannel, mock(), processDescriptor, processDescriptor.name) }

    // Act
    databaseInspectorView.viewListeners.first().toggleKeepConnectionOpenActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(databaseInspectorView, times(0)).updateKeepConnectionOpenButton(true)
    verify(databaseInspectorView, times(1)).updateKeepConnectionOpenButton(false)
  }

  fun testKeepConnectionOpenIsFalseByDefault() {
    // Assert
    verify(databaseInspectorView).updateKeepConnectionOpenButton(false)
  }

  fun testSetDatabaseInspectorClientCommandsChannelUpdatesInspectorState() {
    // Prepare
    val invocations = mutableListOf<Boolean>()
    val databaseInspectorClientCommandChannel = object : DatabaseInspectorClientCommandsChannel {
      override fun keepConnectionsOpen(keepOpen: Boolean): ListenableFuture<Boolean?> {
        invocations.add(keepOpen)
        return Futures.immediateFuture(keepOpen)
      }
      override fun acquireDatabaseLock(databaseId: Int): ListenableFuture<Int?> = Futures.immediateFuture(null)
      override fun releaseDatabaseLock(lockId: Int): ListenableFuture<Unit> = Futures.immediateFuture(null)
    }

    // Act
    runDispatching { databaseInspectorController.startAppInspectionSession(databaseInspectorClientCommandChannel, mock(), processDescriptor, processDescriptor.name) }

    // Assert
    assertEquals(listOf(false), invocations)
  }

  fun testViewTabsHaveViewIcons() {
    // Prepare
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    // Act
    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId1, SqliteTable("view", emptyList(), null, true))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(databaseInspectorView).openTab(
      TabId.TableTab(databaseId1, "view"),
      "view",
      StudioIcons.DatabaseInspector.VIEW,
      viewsFactory.tableView.component
    )
  }

  fun testDatabaseTablesAreClosedWhenDatabaseIsClosed() {
    // Prepare
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    whenever(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(FakeSqliteResultSet()))
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
      databaseInspectorController.addSqliteDatabase(databaseId2)
    }

    // Act
    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    databaseInspectorView.viewListeners.single().tableNodeActionInvoked(databaseId2, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(databaseInspectorView).openTab(
      TabId.TableTab(databaseId1, testSqliteTable.name),
      testSqliteTable.name,
      StudioIcons.DatabaseInspector.TABLE,
      viewsFactory.tableView.component
    )
    verify(databaseInspectorView).openTab(
      TabId.TableTab(databaseId2, testSqliteTable.name),
      testSqliteTable.name,
      StudioIcons.DatabaseInspector.TABLE,
      viewsFactory.tableView.component
    )

    // Act
    databaseInspectorModel.removeDatabaseSchema(databaseId1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(databaseInspectorView).closeTab(TabId.TableTab(databaseId1, testSqliteTable.name))
    verify(databaseInspectorView, times(0)).closeTab(TabId.TableTab(databaseId2, testSqliteTable.name))
  }

  fun testGetSchemaErrorsFromLiveInspectorAreNotReported() {
    // Prepare
    whenever(mockDatabaseConnection.readSchema()).thenReturn(immediateFailedFuture(LiveInspectorException("message", "stack")))

    // Act
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    // Assert
    orderVerifier.verify(databaseInspectorView, times(0)).reportError(any(String::class.java), any(Throwable::class.java))
  }

  fun testGetSchemaConnectionErrorsAreNotReported() {
    // Prepare
    whenever(mockDatabaseConnection.readSchema()).thenReturn(immediateFailedFuture(AppInspectionConnectionException("Connection closed")))

    // Act
    runDispatching {
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    // Assert
    orderVerifier.verify(databaseInspectorView, times(0)).reportError(any(String::class.java), any(Throwable::class.java))
  }

  fun testSchemaIsFiltered() {
    // Prepare
    val fileDatabaseId = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("file")))
    val schema = SqliteSchema(listOf(
      SqliteTable("android_metadata", emptyList(), null, false),
      SqliteTable("sqlite_sequence", emptyList(), null, false))
    )
    whenever(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(schema))
    runDispatching { databaseRepository.addDatabaseConnection(fileDatabaseId, mockDatabaseConnection) }

    // Act
    runDispatching { databaseInspectorController.addSqliteDatabase(fileDatabaseId) }

    // Assert
    assertEquals(SqliteSchema(emptyList()), databaseInspectorModel.getDatabaseSchema(fileDatabaseId))
  }

  fun testRefreshButtonDisabledWhenFileDatabaseIsOpen() {
    // Prepare
    val fileDatabaseId = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("file")))
    val liveDatabaseId = SqliteDatabaseId.fromLiveDatabase("path", 0)
    val schema = SqliteSchema(emptyList())

    // Act
    databaseInspectorModel.addDatabaseSchema(fileDatabaseId, schema)
    databaseInspectorModel.removeDatabaseSchema(fileDatabaseId)
    databaseInspectorModel.addDatabaseSchema(liveDatabaseId, schema)

    // Assert
    orderVerifier.verify(databaseInspectorView).setRefreshButtonState(false)
    orderVerifier.verify(databaseInspectorView).setRefreshButtonState(true)
  }

  fun testEnterOfflineModeSuccess() {
    // Prepare
    val projectService = mock(DatabaseInspectorProjectService::class.java)
    whenever(projectService.openSqliteDatabase(any())).thenReturn(Futures.immediateFuture(Unit))
    project.registerServiceInstance(DatabaseInspectorProjectService::class.java, projectService)

    val inOrderVerifier = inOrder(projectService, fileDatabaseManager)

    val previousFlagState = DatabaseInspectorFlagController.isOpenFileEnabled

    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1) as SqliteDatabaseId.LiveSqliteDatabaseId
    val databaseId2 = SqliteDatabaseId.fromLiveDatabase(":memory: { 123 }", 2)
    val databaseId3 = SqliteDatabaseId.fromLiveDatabase("db3", 3) as SqliteDatabaseId.LiveSqliteDatabaseId
    val databaseId4 = SqliteDatabaseId.fromLiveDatabase("db4", 4) as SqliteDatabaseId.LiveSqliteDatabaseId

    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId1, realDatabaseConnection)
      databaseRepository.addDatabaseConnection(databaseId2, realDatabaseConnection)
      databaseRepository.addDatabaseConnection(databaseId3, realDatabaseConnection)
      databaseRepository.addDatabaseConnection(databaseId4, realDatabaseConnection)

      databaseInspectorController.addSqliteDatabase(databaseId1)
      databaseInspectorController.addSqliteDatabase(databaseId2)
      databaseInspectorController.addSqliteDatabase(databaseId3)
      databaseInspectorController.addSqliteDatabase(databaseId4)

      databaseInspectorController.closeDatabase(databaseId4)
    }

    fileDatabaseManager.downloadTime = 100

    val ideServices = mock<AppInspectionIdeServices>()
    `when`(ideServices.isTabSelected(DatabaseInspectorTabProvider.DATABASE_INSPECTOR_ID)).thenReturn(true)

    runDispatching { databaseInspectorController.startAppInspectionSession(mock(), ideServices, processDescriptor, processDescriptor.name) }

    // Act
    runDispatching(edtExecutor.asCoroutineDispatcher()) {
      databaseInspectorController.stopAppInspectionSession("processName", processDescriptor)
      databaseInspectorController.downloadAndOpenOfflineDatabasesJob!!.join()
    }

    // Assert
    runDispatching {
      inOrderVerifier.verify(fileDatabaseManager).loadDatabaseFileData("processName", processDescriptor, databaseId1)
      inOrderVerifier.verify(fileDatabaseManager).loadDatabaseFileData("processName", processDescriptor, databaseId3)
      inOrderVerifier.verify(fileDatabaseManager).loadDatabaseFileData("processName", processDescriptor, databaseId4)
      inOrderVerifier.verify(projectService, times(3)).openSqliteDatabase(any())
    }
    inOrderVerifier.verifyNoMoreInteractions()

    verify(databaseInspectorView).showEnterOfflineModePanel(0, 3)
    verify(databaseInspectorView).showEnterOfflineModePanel(1, 3)
    verify(databaseInspectorView).showEnterOfflineModePanel(2, 3)
    verify(databaseInspectorView).showEnterOfflineModePanel(3, 3)

    // metrics
    val offlineModeMetadata = trackerService.metadata

    assertNotNull(offlineModeMetadata)
    assertEquals(sqliteFile.length*3, offlineModeMetadata!!.totalDownloadSizeBytes)
    assertTrue(offlineModeMetadata.totalDownloadTimeMs >= 300)
  }

  fun testEnterOfflineAbortedWhenDatabaseInspectorNotVisible() {
    // Prepare
    val projectService = mock(DatabaseInspectorProjectService::class.java)
    whenever(projectService.openSqliteDatabase(any())).thenReturn(Futures.immediateFuture(Unit))
    project.registerServiceInstance(DatabaseInspectorProjectService::class.java, projectService)

    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1) as SqliteDatabaseId.LiveSqliteDatabaseId

    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId1, realDatabaseConnection)
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }
    fileDatabaseManager.downloadTime = 100

    val ideServices = mock<AppInspectionIdeServices>()
    `when`(ideServices.isTabSelected(DatabaseInspectorTabProvider.DATABASE_INSPECTOR_ID)).thenReturn(false)

    runDispatching { databaseInspectorController.startAppInspectionSession(mock(), ideServices, processDescriptor, processDescriptor.name) }

    // Act
    runDispatching(edtExecutor.asCoroutineDispatcher()) {
      databaseInspectorController.stopAppInspectionSession("processName", processDescriptor)
      assertNull(databaseInspectorController.downloadAndOpenOfflineDatabasesJob)
    }

    // metrics
    val offlineModeMetadata = trackerService.metadata
    assertNull(offlineModeMetadata)
  }

  fun testEnterOfflineModeJobCanceled() {
    // Prepare
    val projectService = mock(DatabaseInspectorProjectService::class.java)
    // return future that never completes
    whenever(projectService.openSqliteDatabase(any())).thenReturn(SettableFuture.create())
    project.registerServiceInstance(DatabaseInspectorProjectService::class.java, projectService)

    val previousFlagState = DatabaseInspectorFlagController.isOpenFileEnabled

    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1) as SqliteDatabaseId.LiveSqliteDatabaseId

    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId1, realDatabaseConnection)
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    val ideServices = mock<AppInspectionIdeServices>()
    `when`(ideServices.isTabSelected(DatabaseInspectorTabProvider.DATABASE_INSPECTOR_ID)).thenReturn(true)

    runDispatching { databaseInspectorController.startAppInspectionSession(mock(), ideServices, processDescriptor, processDescriptor.name) }

    // Act
    runDispatching(edtExecutor.asCoroutineDispatcher()) {
      databaseInspectorController.stopAppInspectionSession("processName", processDescriptor)
    }
    runDispatching {
      databaseInspectorController.downloadAndOpenOfflineDatabasesJob!!.cancelAndJoin()
    }

    // Assert
    verify(databaseInspectorView).showOfflineModeUnavailablePanel()
  }

  fun testEnterOfflineModeUserCanceled() {
    // Prepare
    val projectService = mock(DatabaseInspectorProjectService::class.java)
    // return future that never completes
    whenever(projectService.openSqliteDatabase(any())).thenReturn(SettableFuture.create())
    project.registerServiceInstance(DatabaseInspectorProjectService::class.java, projectService)

    val previousFlagState = DatabaseInspectorFlagController.isOpenFileEnabled

    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1) as SqliteDatabaseId.LiveSqliteDatabaseId

    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId1, realDatabaseConnection)
      databaseInspectorController.addSqliteDatabase(databaseId1)
    }

    val ideServices = mock<AppInspectionIdeServices>()
    `when`(ideServices.isTabSelected(DatabaseInspectorTabProvider.DATABASE_INSPECTOR_ID)).thenReturn(true)

    runDispatching { databaseInspectorController.startAppInspectionSession(mock(), ideServices, processDescriptor, processDescriptor.name) }

    // Act
    runDispatching(edtExecutor.asCoroutineDispatcher()) {
      databaseInspectorController.stopAppInspectionSession("processName", processDescriptor)
    }

    databaseInspectorView.viewListeners.first().cancelOfflineModeInvoked()
    runDispatching { databaseInspectorController.downloadAndOpenOfflineDatabasesJob!!.join() }

    // Assert
    verify(databaseInspectorView).showOfflineModeUnavailablePanel()
  }

  fun testShowOfflineModeUnavailablePanelIfNoDbsAreDownloaded() {
    // Prepare
    val projectService = mock(DatabaseInspectorProjectService::class.java)
    whenever(projectService.openSqliteDatabase(any())).thenReturn(Futures.immediateFuture(Unit))
    project.registerServiceInstance(DatabaseInspectorProjectService::class.java, projectService)

    val previousFlagState = DatabaseInspectorFlagController.isOpenFileEnabled

    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1) as SqliteDatabaseId.LiveSqliteDatabaseId

    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId1, realDatabaseConnection)
      databaseInspectorController.addSqliteDatabase(databaseId1)

      whenever(fileDatabaseManager.loadDatabaseFileData("processName", processDescriptor, databaseId1))
        .thenThrow(FileDatabaseException::class.java)
    }

    val ideServices = mock<AppInspectionIdeServices>()
    `when`(ideServices.isTabSelected(DatabaseInspectorTabProvider.DATABASE_INSPECTOR_ID)).thenReturn(true)

    runDispatching { databaseInspectorController.startAppInspectionSession(mock(), ideServices, processDescriptor, processDescriptor.name) }

    // Act
    runDispatching(edtExecutor.asCoroutineDispatcher()) {
      databaseInspectorController.stopAppInspectionSession("processName", processDescriptor)
      databaseInspectorController.downloadAndOpenOfflineDatabasesJob!!.join()
    }

    // Assert
    verify(databaseInspectorView).showOfflineModeUnavailablePanel()
  }

  fun testShowOfflineModeUnavailablePanelIfNoLiveDbsAreOpen() {
    // Prepare
    val projectService = mock(DatabaseInspectorProjectService::class.java)
    whenever(projectService.openSqliteDatabase(any())).thenReturn(Futures.immediateFuture(Unit))
    project.registerServiceInstance(DatabaseInspectorProjectService::class.java, projectService)

    val ideServices = mock<AppInspectionIdeServices>()
    `when`(ideServices.isTabSelected(DatabaseInspectorTabProvider.DATABASE_INSPECTOR_ID)).thenReturn(true)

    runDispatching { databaseInspectorController.startAppInspectionSession(mock(), ideServices, processDescriptor, processDescriptor.name) }

    // Act
    runDispatching(edtExecutor.asCoroutineDispatcher()) {
      databaseInspectorController.stopAppInspectionSession("processName", processDescriptor)
      databaseInspectorController.downloadAndOpenOfflineDatabasesJob!!.join()
    }

    // Assert
    verify(databaseInspectorView).showOfflineModeUnavailablePanel()
  }

  fun testDatabaseNotAddedIfNotFoundInRepository() {
    val newDatabaseId = SqliteDatabaseId.fromLiveDatabase("new-db", 99)
    runDispatching {
      databaseInspectorController.addSqliteDatabase(newDatabaseId)
    }

    assertFalse(databaseRepository.openDatabases.contains(newDatabaseId))

    // `updateDatabases` is invoked once with empty list when the controller adds the listener to the view.
    // here we are testing that it is not invoked more then once.
    verify(databaseInspectorView, times(1)).updateDatabases(any())
  }

  fun testOfflineDatabasesNotOpenedIfFlagDisabled() = runWithState(offlineModeEnabled = false) {
    // Act
    runDispatching(edtExecutor.asCoroutineDispatcher()) {
      databaseInspectorController.stopAppInspectionSession("processName", processDescriptor)
    }

    // Assert
    verifyNoMoreInteractions(offlineModeManager)
  }

  private fun runWithState(offlineModeEnabled: Boolean, block: () -> Unit) {
    val originalState = DatabaseInspectorSettings.getInstance().isOfflineModeEnabled
    DatabaseInspectorSettings.getInstance().isOfflineModeEnabled = offlineModeEnabled

    block()

    DatabaseInspectorSettings.getInstance().isOfflineModeEnabled = originalState
  }
}