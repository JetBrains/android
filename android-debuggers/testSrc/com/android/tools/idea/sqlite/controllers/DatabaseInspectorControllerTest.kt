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
import com.android.tools.idea.appinspection.inspector.api.AppInspectionConnectionException
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.DatabaseInspectorClientCommandsChannel
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.databaseConnection.live.LiveInspectorException
import com.android.tools.idea.sqlite.fileType.SqliteTestUtil
import com.android.tools.idea.sqlite.mocks.DatabaseConnectionWrapper
import com.android.tools.idea.sqlite.mocks.MockDatabaseConnection
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorModel
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorView
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorViewsFactory
import com.android.tools.idea.sqlite.mocks.MockDatabaseRepository
import com.android.tools.idea.sqlite.mocks.MockSchemaProvider
import com.android.tools.idea.sqlite.mocks.MockSqliteResultSet
import com.android.tools.idea.sqlite.model.LiveSqliteDatabase
import com.android.tools.idea.sqlite.model.RowIdName
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.createSqliteStatement
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InOrder
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.util.concurrent.Executor
import javax.swing.Icon
import javax.swing.JComponent

class DatabaseInspectorControllerTest : HeavyPlatformTestCase() {
  private lateinit var mockSqliteView: MockDatabaseInspectorView
  private lateinit var edtExecutor: EdtExecutorService
  private lateinit var edtDispatcher: CoroutineDispatcher
  private lateinit var taskExecutor: Executor
  private lateinit var sqliteController: DatabaseInspectorControllerImpl
  private lateinit var orderVerifier: InOrder
  private lateinit var sqliteUtil: SqliteTestUtil

  private lateinit var mockViewFactory: MockDatabaseInspectorViewsFactory

  private lateinit var databaseId1: SqliteDatabaseId
  private lateinit var databaseId2: SqliteDatabaseId
  private lateinit var databaseId3: SqliteDatabaseId

  private lateinit var sqliteDatabase1: SqliteDatabase
  private lateinit var sqliteDatabase2: SqliteDatabase
  private lateinit var sqliteDatabase3: SqliteDatabase

  private lateinit var testSqliteSchema1: SqliteSchema
  private lateinit var testSqliteSchema2: SqliteSchema
  private lateinit var testSqliteSchema3: SqliteSchema

  private lateinit var mockDatabaseConnection: DatabaseConnection
  private lateinit var realDatabaseConnection: DatabaseConnection
  private lateinit var sqliteFile: VirtualFile

  private val testSqliteTable = SqliteTable("testTable", arrayListOf(), null, false)
  private lateinit var sqliteResultSet: SqliteResultSet

  private lateinit var tempDirTestFixture: TempDirTestFixture

  private lateinit var mockDatabaseInspectorModel: MockDatabaseInspectorModel
  private lateinit var databaseRepository: MockDatabaseRepository

  private lateinit var mockTrackerService: DatabaseInspectorAnalyticsTracker

  override fun setUp() {
    super.setUp()

    tempDirTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    tempDirTestFixture.setUp()

    mockViewFactory = spy(MockDatabaseInspectorViewsFactory())

    testSqliteSchema1 = SqliteSchema(emptyList())
    testSqliteSchema2 = SqliteSchema(emptyList())
    testSqliteSchema3 = SqliteSchema(emptyList())

    mockSqliteView = mockViewFactory.databaseInspectorView
    edtExecutor = EdtExecutorService.getInstance()
    edtDispatcher = edtExecutor.asCoroutineDispatcher()
    taskExecutor = SameThreadExecutor.INSTANCE

    mockDatabaseInspectorModel = spy(MockDatabaseInspectorModel())
    databaseRepository = spy(MockDatabaseRepository(project, edtExecutor))

    mockTrackerService = mock(DatabaseInspectorAnalyticsTracker::class.java)
    project.registerServiceInstance(DatabaseInspectorAnalyticsTracker::class.java, mockTrackerService)

    sqliteController = DatabaseInspectorControllerImpl(
      project,
      mockDatabaseInspectorModel,
      databaseRepository,
      mockViewFactory,
      edtExecutor,
      edtExecutor
    )
    sqliteController.setUp()

    sqliteResultSet = mock(SqliteResultSet::class.java)
    `when`(sqliteResultSet.columns).thenReturn(Futures.immediateFuture(emptyList()))

    mockDatabaseConnection = mock(DatabaseConnection::class.java)
    `when`(mockDatabaseConnection.close()).thenReturn(Futures.immediateFuture(null))
    `when`(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(sqliteResultSet))

    databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1",  1)
    databaseId2 = SqliteDatabaseId.fromLiveDatabase("db2", 2)
    databaseId3 = SqliteDatabaseId.fromLiveDatabase("db", 3)

    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId1, mockDatabaseConnection)
      databaseRepository.addDatabaseConnection(databaseId2, mockDatabaseConnection)
      databaseRepository.addDatabaseConnection(databaseId3, mockDatabaseConnection)
    }

    sqliteDatabase1 = LiveSqliteDatabase(databaseId1, mockDatabaseConnection)
    sqliteDatabase2 = LiveSqliteDatabase(databaseId2, mockDatabaseConnection)
    sqliteDatabase3 = LiveSqliteDatabase(databaseId3, mockDatabaseConnection)

    orderVerifier = inOrder(mockSqliteView, databaseRepository, mockDatabaseConnection)

    sqliteUtil = SqliteTestUtil(
      IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()

    sqliteFile = sqliteUtil.createTestSqliteDatabase("db-name", "t1", listOf("c1"), emptyList(), false)
    realDatabaseConnection = pumpEventsAndWaitForFuture(
      getJdbcDatabaseConnection(testRootDisposable, sqliteFile, FutureCallbackExecutor.wrap(taskExecutor))
    )
  }

  override fun tearDown() {
    Disposer.dispose(sqliteController)
    try {
      pumpEventsAndWaitForFuture(realDatabaseConnection.close())
      sqliteUtil.tearDown()
    } finally {
      tempDirTestFixture.tearDown()
      super.tearDown()
    }
  }

  fun testAddSqliteDatabase() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))

    // Act
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    // Assert
    orderVerifier.verify(mockSqliteView).startLoading("Getting database...")
    orderVerifier.verify(mockDatabaseConnection).readSchema()
    orderVerifier.verify(mockSqliteView).stopLoading()
  }

  fun testAddSqliteDatabaseFailure() {
    // Prepare
    val exception = IllegalStateException("expected")
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))

    // Act
    val result = runCatching {
      runDispatching {
        val deferred = CompletableDeferred<SqliteDatabaseId>()
        deferred.completeExceptionally(exception)
        sqliteController.addSqliteDatabase(deferred)
      }
    }

    // Assert
    assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    assertThat(result.exceptionOrNull()).hasMessageThat().isEqualTo("expected")
    orderVerifier.verify(mockSqliteView).startLoading("Getting database...")

    // Coroutines machinery makes a copy of the exception, so it won't be the same instance as `exception` above.
    orderVerifier.verify(mockSqliteView).reportError(eq("Error getting database"), any(IllegalStateException::class.java))
    orderVerifier.verifyNoMoreInteractions()
  }

  fun testAddSqliteDatabaseFailureReadSchema() {
    // Prepare
    val exception = IllegalStateException("expected")
    `when`(mockDatabaseConnection.readSchema()).thenReturn(immediateFailedFuture(exception))

    // Act
    val result = runCatching {
      runDispatching {
        sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
      }
    }

    // Assert
    assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    assertThat(result.exceptionOrNull()).hasMessageThat().isEqualTo("expected")
    orderVerifier.verify(mockSqliteView).startLoading("Getting database...")
    runDispatching { orderVerifier.verify(databaseRepository).fetchSchema(databaseId1) }
    orderVerifier.verify(mockSqliteView).reportError(eq("Error reading Sqlite database"), any(IllegalStateException::class.java))
    assertEquals("expected", mockSqliteView.errorInvocations.first().second?.message)
    orderVerifier.verifyNoMoreInteractions()
  }

  fun testAddSqliteDatabaseWhenControllerIsDisposed() {

    runDispatching {
      val deferredDatabase = CompletableDeferred<SqliteDatabaseId>()

      val job = launch(edtDispatcher) {
        sqliteController.addSqliteDatabase(deferredDatabase)
      }

      launch(edtDispatcher) {
        // Simulate the job being cancelled while the schema is computed.
        job.cancel()
        deferredDatabase.complete(databaseId1)
      }
    }

    // Assert
    orderVerifier.verify(mockSqliteView).startLoading("Getting database...")
    orderVerifier.verifyNoMoreInteractions()
  }

  fun testDisplayResultSetIsCalledForTable() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    // Act
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockSqliteView).openTab(
      TabId.TableTab(databaseId1, testSqliteTable.name),
      testSqliteTable.name,
      StudioIcons.DatabaseInspector.TABLE,
      mockViewFactory.tableView.component
    )
  }

  fun testDisplayResultSetIsCalledForEvaluatorView() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    // Act
    mockSqliteView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockSqliteView).openTab(
      TabId.AdHocQueryTab(1),
      "New Query [1]",
      StudioIcons.DatabaseInspector.TABLE,
      mockViewFactory.sqliteEvaluatorView.component
    )
  }

  fun testCloseTabIsCalledForTable() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    `when`(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(MockSqliteResultSet()))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    // Act
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    mockSqliteView.viewListeners.single().closeTabActionInvoked(TabId.TableTab(databaseId1, testSqliteTable.name))

    // Assert
    verify(mockViewFactory).createTableView()
    verify(mockSqliteView).closeTab(eq(TabId.TableTab(databaseId1, testSqliteTable.name)))
  }

  fun testCloseTabIsCalledForEvaluatorView() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    // Act
    mockSqliteView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    val tabId = mockSqliteView.lastDisplayedResultSetTabId
    assert(tabId is TabId.AdHocQueryTab)
    mockSqliteView.viewListeners.single().closeTabActionInvoked(tabId!!)

    // Assert
    verify(mockViewFactory).createEvaluatorView(any(Project::class.java), any(SchemaProvider::class.java), any(TableView::class.java))
    verify(mockSqliteView).closeTab(eq(tabId))
  }

  fun testCloseTabInvokedFromTableViewClosesTab() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    `when`(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(MockSqliteResultSet()))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    // Act
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    mockViewFactory.tableView.listeners.single().cancelRunningStatementInvoked()

    // Assert
    verify(mockViewFactory).createTableView()
    verify(mockSqliteView).closeTab(eq(TabId.TableTab(databaseId1, testSqliteTable.name)))
  }

  fun testCloseTabInvokedFromEvaluatorViewClosesTab() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    `when`(mockDatabaseConnection.query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM tab")))
      .thenReturn(Futures.immediateFuture(MockSqliteResultSet()))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    // Act
    runDispatching {
      sqliteController.runSqlStatement(databaseId1, SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM tab"))
    }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    mockViewFactory.tableView.listeners.single().cancelRunningStatementInvoked()

    // Assert
    verify(mockSqliteView).closeTab(any(TabId.AdHocQueryTab::class.java))
  }

  fun testFocusTabIsCalled() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    `when`(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(MockSqliteResultSet()))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    // Act
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)

    // Assert
    verify(mockViewFactory).createTableView()
    verify(mockSqliteView)
      .openTab(
        TabId.TableTab(databaseId1, testSqliteTable.name),
        testSqliteTable.name,
        StudioIcons.DatabaseInspector.TABLE,
        mockViewFactory.tableView.component
      )
    verify(mockSqliteView).focusTab(eq(TabId.TableTab(databaseId1, testSqliteTable.name)))
  }

  fun testAddNewDatabaseAlphabeticOrder() {
    // Act
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema2))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId2))
    }

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema3))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId3))
    }

    // Assert
    orderVerifier.verify(mockSqliteView).updateDatabases(
      listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId1, true), testSqliteSchema1, 0))
    )
    orderVerifier.verify(mockSqliteView).updateDatabases(
      listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId2, true), testSqliteSchema2, 1))
    )
    orderVerifier.verify(mockSqliteView).updateDatabases(
      listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId3, true), testSqliteSchema3, 0))
    )
  }

  fun testNewDatabaseIsAddedToEvaluator() {
    // Prepare
    mockSqliteView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val evaluatorView = mockViewFactory.createEvaluatorView(project, MockSchemaProvider(), mockViewFactory.tableView)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema2))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId2))
    }

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema3))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId3))
    }

    // Assert
    verify(evaluatorView).setDatabases(listOf(), null)
    verify(evaluatorView).setDatabases(listOf(databaseId1), databaseId1)
    verify(evaluatorView).setDatabases(listOf(databaseId1, databaseId2), databaseId1)
    verify(evaluatorView).setDatabases(listOf(databaseId3, databaseId1, databaseId2), databaseId1)
  }

  fun testDatabaseIsUpdatedInEvaluatorTabAfterSchemaChanges() {
    // Prepare
    mockSqliteView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val databaseInspectorView = mockViewFactory.databaseInspectorView
    val evaluatorView = mockViewFactory.sqliteEvaluatorView

    val newSchema = SqliteSchema(listOf(SqliteTable("tab", emptyList(), null, false)))

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    // Act
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(newSchema))
    databaseInspectorView.viewListeners.first().refreshAllOpenDatabasesSchemaActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(evaluatorView).schemaChanged(databaseId1)
  }

  fun testRemoveDatabase() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId2))
    }

    mockSqliteView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    val evaluatorView = mockViewFactory.sqliteEvaluatorView
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // verify initial state
    verify(evaluatorView).setDatabases(listOf(databaseId1, databaseId2), databaseId1)

    // Act
    runDispatching {
      sqliteController.closeDatabase(databaseId1)
    }

    // Assert
    verify(mockDatabaseConnection).close()
    verify(evaluatorView).setDatabases(listOf(databaseId2), databaseId2)
  }

  fun testTabsAssociatedWithDatabaseAreRemovedWhenDatabasedIsRemoved() {
    // Prepare
    val schema = SqliteSchema(listOf(SqliteTable("table1", emptyList(), null, false), testSqliteTable))
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(schema))
    `when`(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(MockSqliteResultSet()))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId2))
    }

    mockSqliteView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(databaseId2, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    runDispatching {
      sqliteController.closeDatabase(databaseId1)
    }

    // Assert
    verify(mockSqliteView).closeTab(eq(TabId.TableTab(databaseId1, testSqliteTable.name)))
    verify(mockSqliteView, times(0)).closeTab(eq(TabId.TableTab(databaseId2, testSqliteTable.name)))
  }

  fun testAllTabsAreRemovedWhenLastDatabasedIsRemoved() {
    // Prepare
    val schema = SqliteSchema(listOf(SqliteTable("table1", emptyList(), null, false), testSqliteTable))
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(schema))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId2))
    }

    mockSqliteView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(databaseId2, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    mockSqliteView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val evaluatorTabId = mockSqliteView.lastDisplayedResultSetTabId!!
    assert(evaluatorTabId is TabId.AdHocQueryTab)

    // Act
    runDispatching {
      sqliteController.closeDatabase(databaseId1)
    }
    runDispatching {
      sqliteController.closeDatabase(databaseId2)
    }

    // Assert
    verify(mockSqliteView).closeTab(eq(TabId.TableTab(databaseId1, testSqliteTable.name)))
    verify(mockSqliteView).closeTab(eq(TabId.TableTab(databaseId2, testSqliteTable.name)))
    verify(mockSqliteView).closeTab(evaluatorTabId)
  }

  fun testUpdateExistingDatabaseAddTables() {
    // Prepare
    val schema = SqliteSchema(emptyList())
    val newSchema = SqliteSchema(listOf(SqliteTable("table", emptyList(), null,false)))
    val evaluatorView = mockViewFactory.sqliteEvaluatorView

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(schema))
    `when`(mockDatabaseConnection.execute(SqliteStatement(SqliteStatementType.INSERT, "INSERT INTO t VALUES (42)")))
      .thenReturn(Futures.immediateFuture(Unit))

    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    mockSqliteView.viewListeners.first().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(newSchema))

    evaluatorView.listeners.forEach {
      it.onDatabaseSelected(databaseId1)
      it.sqliteStatementTextChangedInvoked("INSERT INTO t VALUES (42)")
      it.evaluateCurrentStatement()
    }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockSqliteView).updateDatabaseSchema(
      ViewDatabase(databaseId1, true),
      listOf(AddTable(IndexedSqliteTable(SqliteTable("table", emptyList(), null, false), 0), emptyList()))
    )
  }

  fun testUpdateSchemaUpdatesModel() {
    // Prepare
    val databaseId = SqliteDatabaseId.fromFileDatabase(sqliteFile)
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection)
    }

    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId))
    }

    // Act
    runDispatching {
      sqliteController.runSqlStatement(
        databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "CREATE TABLE t2 (c1 int not null primary key)")
      )
    }

    // Assert
    val table = mockDatabaseInspectorModel.getDatabaseSchema(databaseId)!!.tables.find { it.name == "t2" }!!
    assertSize(1, table.columns)
    assertEquals(SqliteColumn("c1", SqliteAffinity.INTEGER, false, true), table.columns.first())
  }

  fun testCreateTableUpdatesSchema() {
    // Prepare
    val databaseId = SqliteDatabaseId.fromFileDatabase(sqliteFile)
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection)
    }

    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId))
    }

    // Act
    runDispatching {
      sqliteController.runSqlStatement(
        databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "CREATE TABLE t2 (c1 int not null primary key)")
      )
    }

    // Assert
    val column = SqliteColumn("c1", SqliteAffinity.INTEGER, false, true)
    val table = SqliteTable("t2", listOf(column), null, false)

    verify(mockSqliteView).updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(AddTable(IndexedSqliteTable(table, 1), listOf(IndexedSqliteColumn(column, 0))))
    )
  }

  fun testAlterTableRenameTableUpdatesSchema() {
    // Prepare
    val databaseId = SqliteDatabaseId.fromFileDatabase(sqliteFile)
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection)
    }

    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId))
    }

    // Act
    runDispatching {
      sqliteController.runSqlStatement(databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t1 RENAME TO t2"))
    }

    // Assert
    val column = SqliteColumn("c1", SqliteAffinity.BLOB, true, false)
    val tableToRemove = SqliteTable("t1", listOf(column), RowIdName._ROWID_, false)
    val tableToAdd = SqliteTable("t2", listOf(column), RowIdName._ROWID_, false)

    verify(mockSqliteView).updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(RemoveTable(tableToRemove.name), AddTable(IndexedSqliteTable(tableToAdd, 0), listOf(IndexedSqliteColumn(column, 0))))
    )
  }

  fun testAlterTableAddColumnUpdatesSchema() {
    // Prepare
    val databaseId = SqliteDatabaseId.fromFileDatabase(sqliteFile)
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection)
    }

    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId))
    }

    // Act
    runDispatching {
      sqliteController.runSqlStatement(databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t1 ADD c2 TEXT"))
    }

    // Assert
    val columnAlreadyThere = SqliteColumn("c1", SqliteAffinity.BLOB, true, false)
    val columnToAdd = SqliteColumn("c2", SqliteAffinity.TEXT, true, false)

    val table = SqliteTable("t1", listOf(columnAlreadyThere, columnToAdd), RowIdName._ROWID_, false)

    verify(mockSqliteView).updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(AddColumns(table.name, listOf(IndexedSqliteColumn(columnToAdd, 1)), table))
    )
  }

  fun `test AlterTableAddColumn AlterTableRenameTable UpdatesSchema`() {
    // Prepare
    val databaseId = SqliteDatabaseId.fromFileDatabase(sqliteFile)
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection)
    }

    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId))
    }

    // Act
    runDispatching {
      sqliteController.runSqlStatement(databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t1 ADD c2 TEXT"))
    }

    // Assert
    val columnAlreadyThere = SqliteColumn("c1", SqliteAffinity.BLOB, true, false)
    val columnToAdd = SqliteColumn("c2", SqliteAffinity.TEXT, true, false)

    val table = SqliteTable("t1", listOf(columnAlreadyThere, columnToAdd), RowIdName._ROWID_, false)

    verify(mockSqliteView).updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(AddColumns(table.name, listOf(IndexedSqliteColumn(columnToAdd, 1)), table))
    )

    // Act
    runDispatching {
      sqliteController.runSqlStatement(databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t1 RENAME TO t2"))
    }

    // Assert
    val column1 = SqliteColumn("c1", SqliteAffinity.BLOB, true, false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, true, false)
    val tableToRemove = SqliteTable("t1", listOf(column1, column2), RowIdName._ROWID_, false)
    val tableToAdd = SqliteTable("t2", listOf(column1, column2), RowIdName._ROWID_, false)

    verify(mockSqliteView).updateDatabaseSchema(
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
    val databaseId = SqliteDatabaseId.fromFileDatabase(sqliteFile)
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection)
    }

    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId))
    }

    // Act
    runDispatching {
      sqliteController.runSqlStatement(databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "DROP TABLE t1"))
    }

    // Assert
    val column = SqliteColumn("c1", SqliteAffinity.BLOB, true, false)
    val tableToRemove = SqliteTable("t1", listOf(column), RowIdName._ROWID_, false)

    verify(mockSqliteView).updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(RemoveTable(tableToRemove.name))
    )
  }

  fun `test CreateTable AddColumn RenameTable AddColumn UpdatesSchema`() {
    // Prepare
    val databaseId = SqliteDatabaseId.fromFileDatabase(sqliteFile)
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection)
    }

    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId))
    }

    // Act
    runDispatching {
      sqliteController.runSqlStatement(databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "CREATE TABLE t0 (c1 TEXT)"))
    }

    // Assert
    val column = SqliteColumn("c1", SqliteAffinity.TEXT, true, false)
    val tableToAdd = SqliteTable("t0", listOf(column), RowIdName._ROWID_, false)

    verify(mockSqliteView).updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(AddTable(IndexedSqliteTable(tableToAdd, 0), listOf(IndexedSqliteColumn(column, 0))))
    )

    // Act
    runDispatching {
      sqliteController.runSqlStatement(databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t0 ADD c2 TEXT"))
    }

    // Assert
    val columnToAdd = SqliteColumn("c2", SqliteAffinity.TEXT, true, false)
    val table = SqliteTable("t0", listOf(column, columnToAdd), RowIdName._ROWID_, false)

    verify(mockSqliteView).updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(AddColumns(table.name, listOf(IndexedSqliteColumn(columnToAdd, 1)), table))
    )

    // Act
    runDispatching {
      sqliteController.runSqlStatement(databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t0 RENAME TO t2"))
    }

    // Assert
    val tableToRemove = SqliteTable("t0", listOf(column, columnToAdd), RowIdName._ROWID_, false)
    val tableToAdd2 = SqliteTable("t2", listOf(column, columnToAdd), RowIdName._ROWID_, false)

    verify(mockSqliteView).updateDatabaseSchema(
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
      sqliteController.runSqlStatement(databaseId, SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t2 ADD c0 TEXT"))
    }

    // Assert
    val columnToAdd2 = SqliteColumn("c0", SqliteAffinity.TEXT, true, false)
    val table2 = SqliteTable("t2", listOf(column, columnToAdd, columnToAdd2), RowIdName._ROWID_, false)

    verify(mockSqliteView).updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(AddColumns(table2.name, listOf(IndexedSqliteColumn(columnToAdd2, 0)), table2))
    )
  }

  fun testRefreshAllOpenDatabasesSchemaActionInvokedUpdatesSchemas() {
    // Prepare
    val sqliteSchema = SqliteSchema(listOf(SqliteTable("tab", emptyList(), null, false)))

    val sqliteSchemaUpdated = SqliteSchema(listOf(SqliteTable("tab-updated", emptyList(), null, false)))

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(sqliteSchema))

    // Act
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId2))
    }

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(sqliteSchemaUpdated))

    mockSqliteView.viewListeners.first().refreshAllOpenDatabasesSchemaActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockDatabaseInspectorModel).addDatabaseSchema(databaseId1, sqliteSchema)
    verify(mockDatabaseInspectorModel).addDatabaseSchema(databaseId2, sqliteSchema)

    verify(mockDatabaseInspectorModel).updateSchema(databaseId1, sqliteSchemaUpdated)
    verify(mockDatabaseInspectorModel).updateSchema(databaseId2, sqliteSchemaUpdated)

    verify(mockSqliteView).updateDatabaseSchema(
      ViewDatabase(databaseId1, true),
      listOf(
        RemoveTable("tab"),
        AddTable(IndexedSqliteTable(SqliteTable("tab-updated", emptyList(), null, false), 0), emptyList())
      )
    )

    verify(mockSqliteView).updateDatabaseSchema(
      ViewDatabase(databaseId2, true),
      listOf(
        RemoveTable("tab"),
        AddTable(IndexedSqliteTable(SqliteTable("tab-updated", emptyList(), null, false), 0), emptyList())
      )
    )
  }

  fun testWhenSchemaDiffFailsViewIsRecreated() {
    // Prepare
    val databaseId = SqliteDatabaseId.fromFileDatabase(sqliteFile)
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))

    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, mockDatabaseConnection)
    }

    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId))
    }

    `when`(mockSqliteView.updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(AddTable(IndexedSqliteTable(testSqliteTable, 0), emptyList())))
    ).thenThrow(IllegalStateException::class.java)

    // Act
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema(listOf(testSqliteTable))))

    runDispatching {
      mockSqliteView.viewListeners.first().refreshAllOpenDatabasesSchemaActionInvoked()
    }

    // Verify
    verify(mockSqliteView).updateDatabases(listOf(DatabaseDiffOperation.RemoveDatabase(ViewDatabase(databaseId, true))))
    verify(mockSqliteView).updateDatabases(
      listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId, true), SqliteSchema(listOf(testSqliteTable)), 0))
    )
  }

  fun testDisposeCancelsExecutionFuture() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }
    val executionFuture = SettableFuture.create<SqliteResultSet>()
    `when`(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(executionFuture)

    // Act
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    //verify that future is in use now
    verify(mockDatabaseConnection).query(any(SqliteStatement::class.java))

    mockSqliteView.viewListeners.single().closeTabActionInvoked(TabId.TableTab(databaseId1, testSqliteTable.name))

    // Assert
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertThat(executionFuture.isCancelled).isTrue()
  }

  fun testOpenTableAnalytics() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    // Act
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockTrackerService).trackStatementExecuted(AppInspectionEvent.DatabaseInspectorEvent.StatementContext.SCHEMA_STATEMENT_CONTEXT)
  }

  fun testRefreshSchemaAnalytics() {
    // Act
    mockSqliteView.viewListeners.first().refreshAllOpenDatabasesSchemaActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockTrackerService).trackTargetRefreshed(AppInspectionEvent.DatabaseInspectorEvent.TargetType.SCHEMA_TARGET)
  }

  fun testDatabasePossiblyChangedUpdatesAllSchemasAndTabs() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema(emptyList())))
    `when`(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId2))
    }

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema(listOf(testSqliteTable))))

    // open tabel tab
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    runDispatching {
      // open evaluator tab
      sqliteController.runSqlStatement(databaseId1, SqliteStatement(SqliteStatementType.SELECT, "fake stmt"))
    }

    // enable live updates in table tab
    mockViewFactory.tableView.listeners[0].toggleLiveUpdatesInvoked()
    // enable live updates in evaluator tab
    mockViewFactory.tableView.listeners[1].toggleLiveUpdatesInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    runDispatching {
      sqliteController.databasePossiblyChanged()
    }

    // Assert
    // update schemas
    verify(mockDatabaseInspectorModel).updateSchema(databaseId1, SqliteSchema(listOf(testSqliteTable)))
    verify(mockDatabaseInspectorModel).updateSchema(databaseId2, SqliteSchema(listOf(testSqliteTable)))
    verify(mockSqliteView).updateDatabaseSchema(ViewDatabase(databaseId1, true), listOf(AddTable(IndexedSqliteTable(testSqliteTable, 0), emptyList())))
    verify(mockSqliteView).updateDatabaseSchema(ViewDatabase(databaseId2, true), listOf(AddTable(IndexedSqliteTable(testSqliteTable, 0), emptyList())))

    // update tabs
    // each invocation is repeated twice because there are two tabs open
    // 1st invocation by setUp, 2nd by toggleLiveUpdatesInvoked, 3rd by dataPossiblyChanged
    verify(mockViewFactory.tableView, times(6)).showTableColumns(mockResultSet._columns.toViewColumns())
    // invocation by setUp
    verify(mockViewFactory.tableView, times(2)).updateRows(mockResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
    // 1st invocation by toggleLiveUpdatesInvoked, 2nd by dataPossiblyChanged
    verify(mockViewFactory.tableView, times(4)).updateRows(emptyList())
    // invocation by setUp
    verify(mockViewFactory.tableView, times(2)).startTableLoading()
  }

  fun testTableTabsAreRestored() {
    // Prepare
    val table1 = SqliteTable("table1", emptyList(), null, false)
    val table2 = SqliteTable("table2", emptyList(), null, false)
    val schema = SqliteSchema(listOf(table1, table2, testSqliteTable))
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(schema))
    `when`(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(MockSqliteResultSet()))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    mockSqliteView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(databaseId1, table1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(databaseId1, table2)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val savedState = sqliteController.saveState()
    runDispatching {
      sqliteController.closeDatabase(databaseId1)
    }

    // Assert that tabs closed
    verify(mockSqliteView).closeTab(eq(TabId.TableTab(databaseId1, testSqliteTable.name)))
    verify(mockSqliteView).closeTab(eq(TabId.TableTab(databaseId1, table1.name)))
    verify(mockSqliteView).closeTab(eq(TabId.TableTab(databaseId1, table2.name)))
    Mockito.reset(mockSqliteView)

    runDispatching { databaseRepository.addDatabaseConnection(databaseId1, mockDatabaseConnection) }

    // Act: restore state and re-add db
    sqliteController.restoreSavedState(savedState)
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    // Assert that tabs are readded
    verify(mockSqliteView)
      .openTab(
        TabId.TableTab(databaseId1, testSqliteTable.name),
        testSqliteTable.name,
        StudioIcons.DatabaseInspector.TABLE,
        mockViewFactory.tableView.component
      )

    verify(mockSqliteView)
      .openTab(
        TabId.TableTab(databaseId1, table1.name),
        table1.name,
        StudioIcons.DatabaseInspector.TABLE,
        mockViewFactory.tableView.component
      )

    verify(mockSqliteView)
      .openTab(
        TabId.TableTab(databaseId1, table2.name),
        table2.name,
        StudioIcons.DatabaseInspector.TABLE,
        mockViewFactory.tableView.component
      )
  }

  fun testAdHoqQueryTabsAreRestored() {
    // Prepare
    val table1 = SqliteTable("table1", emptyList(), null, false)
    val schema = SqliteSchema(listOf(table1))
    val id = SqliteDatabaseId.LiveSqliteDatabaseId("path", "name", 1)
    runDispatching { databaseRepository.addDatabaseConnection(id, MockDatabaseConnection(schema)) }

    val selectStatement = createSqliteStatement(project, "SELECT * FROM table1")
    val insertStatement = createSqliteStatement(project, "INSERT INTO table VALUES(1)")
    // Act: open AdHoq Tab
    runDispatching {
      sqliteController.addSqliteDatabase(id)
      sqliteController.runSqlStatement(id, selectStatement)
      sqliteController.runSqlStatement(id, insertStatement)
    }

    // Save state and restart
    val savedState = sqliteController.saveState()
    Disposer.dispose(sqliteController)
    mockDatabaseInspectorModel.clearDatabases()

    val newFactory = MockDatabaseInspectorViewsFactory()
    sqliteController = DatabaseInspectorControllerImpl(
      project,
      mockDatabaseInspectorModel,
      databaseRepository,
      newFactory,
      edtExecutor,
      taskExecutor
    )
    sqliteController.setUp()
    sqliteController.restoreSavedState(savedState)

    val restartedDbId = SqliteDatabaseId.LiveSqliteDatabaseId("path", "name", 2)
    val databaseConnection = DatabaseConnectionWrapper(MockDatabaseConnection(schema))
    runDispatching {
      databaseRepository.addDatabaseConnection(restartedDbId, databaseConnection)
    }

    // Act: restore state and re-add db
    runDispatching {
      sqliteController.addSqliteDatabase(restartedDbId)
    }

    // Verify
    verify(newFactory.databaseInspectorView, times(2))
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
    mockSqliteView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val newSchema = SqliteSchema(listOf(SqliteTable("tab", emptyList(), null, false)))

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    // Act
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(newSchema))
    mockSqliteView.viewListeners.first().refreshAllOpenDatabasesSchemaActionInvoked()
    mockSqliteView.viewListeners.first().refreshAllOpenDatabasesSchemaActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockSqliteView, times(1)).updateDatabaseSchema(
      ViewDatabase(databaseId1, true),
      listOf(AddTable(IndexedSqliteTable(SqliteTable("tab", emptyList(), null, false), 0), emptyList()))
    )
  }

  fun testClosedDatabasesAreAddedToView() {
    // Prepare
    val db1 = SqliteDatabaseId.fromLiveDatabase("db", 1)
    mockDatabaseInspectorModel.addDatabaseSchema(db1, testSqliteSchema1)

    // Act
    mockDatabaseInspectorModel.removeDatabaseSchema(db1)

    // Assert
    verify(mockSqliteView).updateDatabases(listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase (db1, true), testSqliteSchema1, 0)))
    verify(mockSqliteView).updateDatabases(listOf(
      DatabaseDiffOperation.AddDatabase(ViewDatabase(db1, false), null, 0),
      DatabaseDiffOperation.RemoveDatabase(ViewDatabase (db1, true))
    ))
  }

  fun testClosedDatabasesAreRemovedOnceReopened() {
    // Prepare
    val db1 = SqliteDatabaseId.fromLiveDatabase("db", 1)

    // Act
    mockDatabaseInspectorModel.removeDatabaseSchema(db1)
    mockDatabaseInspectorModel.addDatabaseSchema(db1, testSqliteSchema1)

    // Assert
    verify(mockSqliteView).updateDatabases(listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase (db1, false), null, 0)))
    verify(mockSqliteView).updateDatabases(listOf(
      DatabaseDiffOperation.AddDatabase(ViewDatabase(db1, true), testSqliteSchema1, 0),
      DatabaseDiffOperation.RemoveDatabase(ViewDatabase (db1, false))
    ))
  }

  fun testKeepConnectionOpenUpdatesSuccessfully() {
    // Prepare
    val databaseInspectorClientCommandChannel = object : DatabaseInspectorClientCommandsChannel {
      override fun keepConnectionsOpen(keepOpen: Boolean): ListenableFuture<Boolean?> {
        return Futures.immediateFuture(true)
      }
    }

    sqliteController.setDatabaseInspectorClientCommandsChannel(databaseInspectorClientCommandChannel)

    // Act
    mockSqliteView.viewListeners.first().toggleKeepConnectionOpenActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockSqliteView).updateKeepConnectionOpenButton(true)
  }

  fun testKeepConnectionOpenDoesNotUpdateIfOperationFails() {
    // Prepare
    val databaseInspectorClientCommandChannel = object : DatabaseInspectorClientCommandsChannel {
      override fun keepConnectionsOpen(keepOpen: Boolean): ListenableFuture<Boolean?> {
        return Futures.immediateFuture(null)
      }
    }

    sqliteController.setDatabaseInspectorClientCommandsChannel(databaseInspectorClientCommandChannel)

    // Act
    mockSqliteView.viewListeners.first().toggleKeepConnectionOpenActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockSqliteView, times(0)).updateKeepConnectionOpenButton(true)
    verify(mockSqliteView, times(1)).updateKeepConnectionOpenButton(false)
  }

  fun testKeepConnectionOpenIsFalseByDefault() {
    // Assert
    verify(mockSqliteView).updateKeepConnectionOpenButton(false)
  }

  fun testSetDatabaseInspectorClientCommandsChannelUpdatesInspectorState() {
    // Prepare
    val invocations = mutableListOf<Boolean>()
    val databaseInspectorClientCommandChannel = object : DatabaseInspectorClientCommandsChannel {
      override fun keepConnectionsOpen(keepOpen: Boolean): ListenableFuture<Boolean?> {
        invocations.add(keepOpen)
        return Futures.immediateFuture(keepOpen)
      }
    }

    // Act
    sqliteController.setDatabaseInspectorClientCommandsChannel(databaseInspectorClientCommandChannel)

    // Assert
    assertEquals(listOf(false), invocations)
  }

  fun testViewTabsHaveViewIcons() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    // Act
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(databaseId1, SqliteTable("view", emptyList(), null, true))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockSqliteView).openTab(
      TabId.TableTab(databaseId1, "view"),
      "view",
      StudioIcons.DatabaseInspector.VIEW,
      mockViewFactory.tableView.component
    )
  }

  fun testDatabaseTablesAreClosedWhenDatabaseIsClosed() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    `when`(mockDatabaseConnection.query(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(MockSqliteResultSet()))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId2))
    }

    // Act
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(databaseId1, testSqliteTable)
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(databaseId2, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockSqliteView).openTab(
      TabId.TableTab(databaseId1, testSqliteTable.name),
      testSqliteTable.name,
      StudioIcons.DatabaseInspector.TABLE,
      mockViewFactory.tableView.component
    )
    verify(mockSqliteView).openTab(
      TabId.TableTab(databaseId2, testSqliteTable.name),
      testSqliteTable.name,
      StudioIcons.DatabaseInspector.TABLE,
      mockViewFactory.tableView.component
    )

    // Act
    mockDatabaseInspectorModel.removeDatabaseSchema(databaseId1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockSqliteView).closeTab(TabId.TableTab(databaseId1, testSqliteTable.name))
    verify(mockSqliteView, times(0)).closeTab(TabId.TableTab(databaseId2, testSqliteTable.name))
  }

  fun testGetSchemaErrorsFromLiveInspectorAreNotReported() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(immediateFailedFuture(LiveInspectorException("message", "stack")))

    // Act
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    // Assert
    orderVerifier.verify(mockSqliteView, times(0)).reportError(any(String::class.java), any(Throwable::class.java))
  }

  fun testGetSchemaConnectionErrorsAreNotReported() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(immediateFailedFuture(AppInspectionConnectionException("Connection closed")))

    // Act
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(databaseId1))
    }

    // Assert
    orderVerifier.verify(mockSqliteView, times(0)).reportError(any(String::class.java), any(Throwable::class.java))
  }
}