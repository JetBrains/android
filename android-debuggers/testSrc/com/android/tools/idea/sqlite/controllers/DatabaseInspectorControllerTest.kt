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
import com.android.tools.idea.device.fs.DeviceFileId
import com.android.tools.idea.device.fs.DownloadProgress
import com.android.tools.idea.sqlite.DatabaseInspectorProjectService
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorModel
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorView
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorViewsFactory
import com.android.tools.idea.sqlite.mocks.MockSchemaProvider
import com.android.tools.idea.sqlite.model.FileSqliteDatabase
import com.android.tools.idea.sqlite.model.LiveSqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.registerServiceInstance
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.concurrency.SameThreadExecutor
import org.mockito.InOrder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import java.util.concurrent.Executor
import javax.swing.JComponent

class DatabaseInspectorControllerTest : PlatformTestCase() {
  private lateinit var mockSqliteView: MockDatabaseInspectorView
  private lateinit var edtExecutor: EdtExecutorService
  private lateinit var taskExecutor: Executor
  private lateinit var sqliteController: DatabaseInspectorControllerImpl
  private lateinit var orderVerifier: InOrder

  private lateinit var mockViewFactory: MockDatabaseInspectorViewsFactory

  private lateinit var sqliteDatabase1: SqliteDatabase
  private lateinit var sqliteDatabase2: SqliteDatabase
  private lateinit var sqliteDatabase3: SqliteDatabase

  private lateinit var testSqliteSchema1: SqliteSchema
  private lateinit var testSqliteSchema2: SqliteSchema
  private lateinit var testSqliteSchema3: SqliteSchema

  private lateinit var mockDatabaseConnection: DatabaseConnection

  private val testSqliteTable = SqliteTable("testTable", arrayListOf(), null, true)
  private lateinit var sqliteResultSet: SqliteResultSet

  private lateinit var tempDirTestFixture: TempDirTestFixture

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
    taskExecutor = SameThreadExecutor.INSTANCE

    sqliteController = DatabaseInspectorControllerImpl(
      project,
      MockDatabaseInspectorModel(),
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
  }

  override fun tearDown() {
    super.tearDown()
    Disposer.dispose(sqliteController)
    tempDirTestFixture.tearDown()
  }

  fun testAddSqliteDatabase() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))

    // Act
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(mockSqliteView).startLoading("Getting database...")
    orderVerifier.verify(mockDatabaseConnection).readSchema()
    orderVerifier.verify(mockSqliteView).stopLoading()
  }

  fun testAddSqliteDatabaseFailure() {
    // Prepare
    val exception = IllegalStateException()
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))

    // Act
    sqliteController.addSqliteDatabase(Futures.immediateFailedFuture(exception))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(mockSqliteView).reportError("Error getting database", exception)
    orderVerifier.verifyNoMoreInteractions()
  }

  fun testAddSqliteDatabaseFailureReadSchema() {
    // Prepare
    val exception = IllegalStateException()
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFailedFuture(exception))

    // Act
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(mockSqliteView).startLoading("Getting database...")
    orderVerifier.verify(mockDatabaseConnection).readSchema()
    orderVerifier.verify(mockSqliteView).reportError("Error reading Sqlite database", exception)
    orderVerifier.verifyNoMoreInteractions()
  }

  fun testAddSqliteDatabaseWhenControllerIsDisposed() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))

    // Act
    Disposer.dispose(sqliteController)
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(mockSqliteView).startLoading("Getting database...")
    orderVerifier.verifyNoMoreInteractions()
  }

  fun testDisplayResultSetIsCalledForTable() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

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
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

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
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

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
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

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
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

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
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema2))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase2))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema3))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase3))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

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
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema2))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase2))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema3))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase3))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val evaluatorView = mockViewFactory.createEvaluatorView(project, MockSchemaProvider(), mockViewFactory.tableView)
    verify(evaluatorView).addDatabase(sqliteDatabase1, 0)
    verify(evaluatorView).addDatabase(sqliteDatabase2, 1)
    verify(evaluatorView).addDatabase(sqliteDatabase3, 0)
  }

  fun testRemoveDatabase() {
    // Prepare
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

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
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

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
    `when`(mockDatabaseConnection.execute(SqliteStatement("INSERT"))).thenReturn(Futures.immediateFuture(null))

    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))

    mockSqliteView.viewListeners.first().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(newSchema))
    evaluatorView.listeners.forEach { it.evaluateSqlActionInvoked(sqliteDatabase1, "INSERT") }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockSqliteView).updateDatabase(
      sqliteDatabase1,
      listOf(SqliteTable("table", emptyList(), null, false))
    )
  }

  fun testReDownloadFileUpdatesView() {
    // Prepare
    val deviceFileId = DeviceFileId("deviceId", "filePath")
    val virtualFile = tempDirTestFixture.createFile("db")
    deviceFileId.storeInVirtualFile(virtualFile)
    val fileDatabase = FileSqliteDatabase("db", mockDatabaseConnection, virtualFile)

    `when`(mockDatabaseConnection.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

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
}