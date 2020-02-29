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
import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.device.fs.DeviceFileId
import com.android.tools.idea.device.fs.DownloadProgress
import com.android.tools.idea.sqlite.DatabaseInspectorProjectService
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.EmptySqliteResultSet
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.fileType.SqliteTestUtil
import com.android.tools.idea.sqlite.getJdbcDatabaseConnection
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorModel
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorView
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorViewsFactory
import com.android.tools.idea.sqlite.mocks.MockSchemaProvider
import com.android.tools.idea.sqlite.model.FileSqliteDatabase
import com.android.tools.idea.sqlite.model.LiveSqliteDatabase
import com.android.tools.idea.sqlite.model.RowIdName
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.mainView.AddColumns
import com.android.tools.idea.sqlite.ui.mainView.AddTable
import com.android.tools.idea.sqlite.ui.mainView.IndexedSqliteColumn
import com.android.tools.idea.sqlite.ui.mainView.IndexedSqliteTable
import com.android.tools.idea.sqlite.ui.mainView.RemoveTable
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.android.tools.idea.testing.runDispatching
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.mockito.InOrder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import java.util.concurrent.Executor
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

  private lateinit var sqliteDatabase1: SqliteDatabase
  private lateinit var sqliteDatabase2: SqliteDatabase
  private lateinit var sqliteDatabase3: SqliteDatabase

  private lateinit var testSqliteSchema1: SqliteSchema
  private lateinit var testSqliteSchema2: SqliteSchema
  private lateinit var testSqliteSchema3: SqliteSchema

  private lateinit var mockDatabaseConnection: DatabaseConnection
  private lateinit var realDatabaseConnection: DatabaseConnection
  private lateinit var sqliteFile: VirtualFile

  private val testSqliteTable = SqliteTable("testTable", arrayListOf(), null, true)
  private lateinit var sqliteResultSet: SqliteResultSet

  private lateinit var tempDirTestFixture: TempDirTestFixture

  private lateinit var mockDatabaseInspectorModel: MockDatabaseInspectorModel

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

    sqliteController = DatabaseInspectorControllerImpl(
      project,
      mockDatabaseInspectorModel,
      mockViewFactory,
      edtExecutor,
      taskExecutor
    )
    sqliteController.setUp()

    sqliteResultSet = mock(SqliteResultSet::class.java)
    `when`(sqliteResultSet.columns).thenReturn(Futures.immediateFuture(testSqliteTable.columns))

    mockDatabaseConnection = mock(DatabaseConnection::class.java)
    `when`(mockDatabaseConnection.close()).thenReturn(Futures.immediateFuture(null))
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(sqliteResultSet))

    sqliteDatabase1 = LiveSqliteDatabase("db1", mockDatabaseConnection)
    sqliteDatabase2 = LiveSqliteDatabase("db2", mockDatabaseConnection)
    sqliteDatabase3 = LiveSqliteDatabase("db", mockDatabaseConnection)

    orderVerifier = inOrder(mockSqliteView, mockDatabaseConnection)

    sqliteUtil = SqliteTestUtil(
      IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()

    sqliteFile = sqliteUtil.createTestSqliteDatabase("db-name", "t1", listOf("c1"), emptyList(), false)
    realDatabaseConnection = pumpEventsAndWaitForFuture(
      getJdbcDatabaseConnection(sqliteFile, FutureCallbackExecutor.wrap(taskExecutor))
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
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase1))
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
        val deferred = CompletableDeferred<SqliteDatabase>()
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
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFailedFuture(exception))

    // Act
    val result = runCatching {
      runDispatching {
        sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase1))
      }
    }

    // Assert
    assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    assertThat(result.exceptionOrNull()).hasMessageThat().isEqualTo("expected")
    orderVerifier.verify(mockSqliteView).startLoading("Getting database...")
    orderVerifier.verify(mockDatabaseConnection).readSchema()
    orderVerifier.verify(mockSqliteView).reportError("Error reading Sqlite database", exception)
    orderVerifier.verifyNoMoreInteractions()
  }

  fun testAddSqliteDatabaseWhenControllerIsDisposed() {

    runDispatching {
      val deferredDatabase = CompletableDeferred<SqliteDatabase>()

      val job = launch(edtDispatcher) {
        sqliteController.addSqliteDatabase(deferredDatabase)
      }

      launch(edtDispatcher) {
        // Simulate the job being cancelled while the schema is computed.
        job.cancel()
        deferredDatabase.complete(sqliteDatabase1)
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
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase1))
    }

    // Act
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(sqliteDatabase1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockSqliteView).openTab(
      eq(TabId.TableTab(sqliteDatabase1, testSqliteTable.name)),
      eq(testSqliteTable.name), any(JComponent::class.java)
    )
  }

  fun testDisplayResultSetIsCalledForEvaluatorView() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase1))
    }

    // Act
    mockSqliteView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockSqliteView)
      .openTab(any(TabId.AdHocQueryTab::class.java), any(String::class.java), any(JComponent::class.java))
  }

  fun testCloseTabIsCalledForTable() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase1))
    }

    // Act
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(sqliteDatabase1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    mockSqliteView.viewListeners.single().closeTabActionInvoked(TabId.TableTab(sqliteDatabase1, testSqliteTable.name))

    // Assert
    verify(mockViewFactory).createTableView()
    verify(mockSqliteView).closeTab(eq(TabId.TableTab(sqliteDatabase1, testSqliteTable.name)))
  }

  fun testCloseTabIsCalledForEvaluatorView() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase1))
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

  fun testFocusTabIsCalled() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase1))
    }

    // Act
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(sqliteDatabase1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(sqliteDatabase1, testSqliteTable)

    // Assert
    verify(mockViewFactory).createTableView()
    verify(mockSqliteView)
      .openTab(
        eq(TabId.TableTab(sqliteDatabase1, testSqliteTable.name)),
        eq(testSqliteTable.name), any(JComponent::class.java)
      )
    verify(mockSqliteView).focusTab(eq(TabId.TableTab(sqliteDatabase1, testSqliteTable.name)))
  }

  fun testAddNewDatabaseAlphabeticOrder() {
    // Act
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase1))
    }

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema2))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase2))
    }

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema3))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase3))
    }

    // Assert
    orderVerifier.verify(mockSqliteView).addDatabaseSchema(sqliteDatabase1, testSqliteSchema1, 0)
    orderVerifier.verify(mockSqliteView).addDatabaseSchema(sqliteDatabase2, testSqliteSchema2, 1)
    orderVerifier.verify(mockSqliteView).addDatabaseSchema(sqliteDatabase3, testSqliteSchema3, 0)
  }

  fun testNewDatabaseIsAddedToEvaluator() {
    // Prepare
    mockSqliteView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase1))
    }

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema2))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase2))
    }

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema3))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase3))
    }

    // Assert
    val evaluatorView = mockViewFactory.createEvaluatorView(project, MockSchemaProvider(), mockViewFactory.tableView)
    verify(evaluatorView).addDatabase(sqliteDatabase1, 0)
    verify(evaluatorView).addDatabase(sqliteDatabase2, 1)
    verify(evaluatorView).addDatabase(sqliteDatabase3, 0)
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
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase1))
    }

    // Act
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(newSchema))
    databaseInspectorView.viewListeners.first().refreshAllOpenDatabasesSchemaActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(evaluatorView).schemaChanged(sqliteDatabase1)
  }

  fun testRemoveDatabase() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase1))
    }

    mockSqliteView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    val evaluatorView = mockViewFactory.createEvaluatorView(project, MockSchemaProvider(), mockViewFactory.tableView)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    mockSqliteView.viewListeners.first().removeDatabaseActionInvoked(sqliteDatabase1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockDatabaseConnection).dispose()
    verify(evaluatorView).removeDatabase(0)
    verify(mockSqliteView).removeDatabaseSchema(sqliteDatabase1)
  }

  fun testTablesAreRemovedWhenDatabasedIsRemoved() {
    // Prepare
    val schema = SqliteSchema(listOf(SqliteTable("table1", emptyList(), null, false), testSqliteTable))
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(schema))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase1))
    }

    mockSqliteView.viewListeners.single().tableNodeActionInvoked(sqliteDatabase1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    mockSqliteView.viewListeners.first().removeDatabaseActionInvoked(sqliteDatabase1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockSqliteView).closeTab(eq(TabId.TableTab(sqliteDatabase1, testSqliteTable.name)))
  }

  fun testUpdateExistingDatabaseAddTables() {
    // Prepare
    val schema = SqliteSchema(emptyList())
    val newSchema = SqliteSchema(listOf(SqliteTable("table", emptyList(), null,false)))
    val evaluatorView = mockViewFactory.sqliteEvaluatorView

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(schema))
    `when`(mockDatabaseConnection.execute(SqliteStatement("INSERT")))
      .thenReturn(Futures.immediateFuture(
        EmptySqliteResultSet()))

    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase1))
    }

    mockSqliteView.viewListeners.first().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(newSchema))
    evaluatorView.listeners.forEach { it.evaluateSqlActionInvoked(sqliteDatabase1, "INSERT") }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockSqliteView).updateDatabaseSchema(
      sqliteDatabase1,
      listOf(AddTable(IndexedSqliteTable(SqliteTable("table", emptyList(), null, false), 0), emptyList()))
    )
  }

  fun testUpdateSchemaUpdatesModel() {
    // Prepare
    val sqliteDatabase = FileSqliteDatabase("db", realDatabaseConnection, sqliteFile)

    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase))
    }

    // Act
    runDispatching {
      sqliteController.runSqlStatement(sqliteDatabase, SqliteStatement("CREATE TABLE t2 (c1 int not null primary key)"))
    }

    // Assert
    val table = mockDatabaseInspectorModel.openDatabases[sqliteDatabase]!!.tables.find { it.name == "t2" }!!
    assertSize(1, table.columns)
    assertEquals(SqliteColumn("c1", SqliteAffinity.INTEGER, false, true), table.columns.first())
  }

  fun testCreateTableUpdatesSchema() {
    // Prepare
    val sqliteDatabase = FileSqliteDatabase("db", realDatabaseConnection, sqliteFile)

    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase))
    }

    // Act
    runDispatching {
      sqliteController.runSqlStatement(sqliteDatabase, SqliteStatement("CREATE TABLE t2 (c1 int not null primary key)"))
    }

    // Assert
    val column = SqliteColumn("c1", SqliteAffinity.INTEGER, false, true)
    val table = SqliteTable("t2", listOf(column), null, false)

    verify(mockSqliteView).updateDatabaseSchema(
      sqliteDatabase,
      listOf(AddTable(IndexedSqliteTable(table, 1), listOf(IndexedSqliteColumn(column, 0))))
    )
  }

  fun testAlterTableRenameTableUpdatesSchema() {
    // Prepare
    val sqliteDatabase = FileSqliteDatabase("db", realDatabaseConnection, sqliteFile)

    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase))
    }

    // Act
    runDispatching {
      sqliteController.runSqlStatement(sqliteDatabase, SqliteStatement("ALTER TABLE t1 RENAME TO t2"))
    }

    // Assert
    val column = SqliteColumn("c1", SqliteAffinity.BLOB, true, false)
    val tableToRemove = SqliteTable("t1", listOf(column), RowIdName._ROWID_, false)
    val tableToAdd = SqliteTable("t2", listOf(column), RowIdName._ROWID_, false)

    verify(mockSqliteView).updateDatabaseSchema(
      sqliteDatabase,
      listOf(RemoveTable(tableToRemove.name), AddTable(IndexedSqliteTable(tableToAdd, 0), listOf(IndexedSqliteColumn(column, 0))))
    )
  }

  fun testAlterTableAddColumnUpdatesSchema() {
    // Prepare
    val sqliteDatabase = FileSqliteDatabase("db", realDatabaseConnection, sqliteFile)

    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase))
    }

    // Act
    runDispatching {
      sqliteController.runSqlStatement(sqliteDatabase, SqliteStatement("ALTER TABLE t1 ADD c2 TEXT"))
    }

    // Assert
    val columnAlreadyThere = SqliteColumn("c1", SqliteAffinity.BLOB, true, false)
    val columnToAdd = SqliteColumn("c2", SqliteAffinity.TEXT, true, false)

    val table = SqliteTable("t1", listOf(columnAlreadyThere, columnToAdd), RowIdName._ROWID_, false)

    verify(mockSqliteView).updateDatabaseSchema(
      sqliteDatabase,
      listOf(AddColumns(table.name, listOf(IndexedSqliteColumn(columnToAdd, 1)), table))
    )
  }

  fun `test AlterTableAddColumn AlterTableRenameTable UpdatesSchema`() {
    // Prepare
    val sqliteDatabase = FileSqliteDatabase("db", realDatabaseConnection, sqliteFile)

    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase))
    }

    // Act
    runDispatching {
      sqliteController.runSqlStatement(sqliteDatabase, SqliteStatement("ALTER TABLE t1 ADD c2 TEXT"))
    }

    // Assert
    val columnAlreadyThere = SqliteColumn("c1", SqliteAffinity.BLOB, true, false)
    val columnToAdd = SqliteColumn("c2", SqliteAffinity.TEXT, true, false)

    val table = SqliteTable("t1", listOf(columnAlreadyThere, columnToAdd), RowIdName._ROWID_, false)

    verify(mockSqliteView).updateDatabaseSchema(
      sqliteDatabase,
      listOf(AddColumns(table.name, listOf(IndexedSqliteColumn(columnToAdd, 1)), table))
    )

    // Act
    runDispatching {
      sqliteController.runSqlStatement(sqliteDatabase, SqliteStatement("ALTER TABLE t1 RENAME TO t2"))
    }

    // Assert
    val column1 = SqliteColumn("c1", SqliteAffinity.BLOB, true, false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, true, false)
    val tableToRemove = SqliteTable("t1", listOf(column1, column2), RowIdName._ROWID_, false)
    val tableToAdd = SqliteTable("t2", listOf(column1, column2), RowIdName._ROWID_, false)

    verify(mockSqliteView).updateDatabaseSchema(
      sqliteDatabase,
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
    val sqliteDatabase = FileSqliteDatabase("db", realDatabaseConnection, sqliteFile)

    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase))
    }

    // Act
    runDispatching {
      sqliteController.runSqlStatement(sqliteDatabase, SqliteStatement("DROP TABLE t1"))
    }

    // Assert
    val column = SqliteColumn("c1", SqliteAffinity.BLOB, true, false)
    val tableToRemove = SqliteTable("t1", listOf(column), RowIdName._ROWID_, false)

    verify(mockSqliteView).updateDatabaseSchema(
      sqliteDatabase,
      listOf(RemoveTable(tableToRemove.name))
    )
  }

  fun `test CreateTable AddColumn RenameTable AddColumn UpdatesSchema`() {
    // Prepare
    val sqliteDatabase = FileSqliteDatabase("db", realDatabaseConnection, sqliteFile)

    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase))
    }

    // Act
    runDispatching {
      sqliteController.runSqlStatement(sqliteDatabase, SqliteStatement("CREATE TABLE t0 (c1 TEXT)"))
    }

    // Assert
    val column = SqliteColumn("c1", SqliteAffinity.TEXT, true, false)
    val tableToAdd = SqliteTable("t0", listOf(column), RowIdName._ROWID_, false)

    verify(mockSqliteView).updateDatabaseSchema(
      sqliteDatabase,
      listOf(AddTable(IndexedSqliteTable(tableToAdd, 0), listOf(IndexedSqliteColumn(column, 0))))
    )

    // Act
    runDispatching {
      sqliteController.runSqlStatement(sqliteDatabase, SqliteStatement("ALTER TABLE t0 ADD c2 TEXT"))
    }

    // Assert
    val columnToAdd = SqliteColumn("c2", SqliteAffinity.TEXT, true, false)
    val table = SqliteTable("t0", listOf(column, columnToAdd), RowIdName._ROWID_, false)

    verify(mockSqliteView).updateDatabaseSchema(
      sqliteDatabase,
      listOf(AddColumns(table.name, listOf(IndexedSqliteColumn(columnToAdd, 1)), table))
    )

    // Act
    runDispatching {
      sqliteController.runSqlStatement(sqliteDatabase, SqliteStatement("ALTER TABLE t0 RENAME TO t2"))
    }

    // Assert
    val tableToRemove = SqliteTable("t0", listOf(column, columnToAdd), RowIdName._ROWID_, false)
    val tableToAdd2 = SqliteTable("t2", listOf(column, columnToAdd), RowIdName._ROWID_, false)

    verify(mockSqliteView).updateDatabaseSchema(
      sqliteDatabase,
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
      sqliteController.runSqlStatement(sqliteDatabase, SqliteStatement("ALTER TABLE t2 ADD c0 TEXT"))
    }

    // Assert
    val columnToAdd2 = SqliteColumn("c0", SqliteAffinity.TEXT, true, false)
    val table2 = SqliteTable("t2", listOf(column, columnToAdd, columnToAdd2), RowIdName._ROWID_, false)

    verify(mockSqliteView).updateDatabaseSchema(
      sqliteDatabase,
      listOf(AddColumns(table2.name, listOf(IndexedSqliteColumn(columnToAdd2, 0)), table2))
    )
  }

  fun testReDownloadFileUpdatesView() {
    // Prepare
    val deviceFileId = DeviceFileId("deviceId", "filePath")
    val virtualFile = tempDirTestFixture.createFile("db")
    deviceFileId.storeInVirtualFile(virtualFile)
    val fileDatabase = FileSqliteDatabase("db", mockDatabaseConnection, virtualFile)

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase1))
    }

    val mockSqliteExplorerProjectService = mock(DatabaseInspectorProjectService::class.java)
    `when`(mockSqliteExplorerProjectService.reDownloadAndOpenFile(any(FileSqliteDatabase::class.java), any(DownloadProgress::class.java)))
      .thenReturn(Futures.immediateFuture(null))
    project.registerServiceInstance(DatabaseInspectorProjectService::class.java, mockSqliteExplorerProjectService)

    // Act
    mockSqliteView.viewListeners.single().reDownloadDatabaseFileActionInvoked(fileDatabase)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(mockSqliteView).reportSyncProgress(any(String::class.java))
  }

  fun testRefreshAllOpenDatabasesSchemaActionInvokedUpdatesSchemas() {
    // Prepare
    val sqliteSchema = SqliteSchema(listOf(SqliteTable("tab", emptyList(), null, false)))

    val sqliteSchemaUpdated = SqliteSchema(listOf(SqliteTable("tab-updated", emptyList(), null, false)))

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(sqliteSchema))

    // Act
    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase1))
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase2))
    }

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(sqliteSchemaUpdated))

    mockSqliteView.viewListeners.first().refreshAllOpenDatabasesSchemaActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockDatabaseInspectorModel).add(sqliteDatabase1, sqliteSchema)
    verify(mockDatabaseInspectorModel).add(sqliteDatabase2, sqliteSchema)

    verify(mockDatabaseInspectorModel).add(sqliteDatabase1, sqliteSchemaUpdated)
    verify(mockDatabaseInspectorModel).add(sqliteDatabase2, sqliteSchemaUpdated)

    verify(mockSqliteView).updateDatabaseSchema(
      sqliteDatabase1,
      listOf(
        RemoveTable("tab"),
        AddTable(IndexedSqliteTable(SqliteTable("tab-updated", emptyList(), null, false), 0), emptyList())
      )
    )

    verify(mockSqliteView).updateDatabaseSchema(
      sqliteDatabase2,
      listOf(
        RemoveTable("tab"),
        AddTable(IndexedSqliteTable(SqliteTable("tab-updated", emptyList(), null, false), 0), emptyList())
      )
    )
  }

  fun testWhenSchemaDiffFailsViewIsRecreated() {
    // Prepare
    val sqliteDatabase = FileSqliteDatabase("db", mockDatabaseConnection, sqliteFile)
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))

    runDispatching {
      sqliteController.addSqliteDatabase(CompletableDeferred(sqliteDatabase))
    }

    `when`(mockSqliteView.updateDatabaseSchema(
      sqliteDatabase,
      listOf(AddTable(IndexedSqliteTable(testSqliteTable, 0), emptyList())))
    ).thenThrow(IllegalStateException::class.java)

    // Act
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema(listOf(testSqliteTable))))

    runDispatching {
      mockSqliteView.viewListeners.first().refreshAllOpenDatabasesSchemaActionInvoked()
    }

    // Verify
    verify(mockSqliteView).removeDatabaseSchema(sqliteDatabase)
    verify(mockSqliteView).addDatabaseSchema(sqliteDatabase, SqliteSchema(listOf(testSqliteTable)), 0)
  }
}