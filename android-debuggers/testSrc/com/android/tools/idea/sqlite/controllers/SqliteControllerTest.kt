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
import com.android.tools.idea.device.fs.DownloadProgress
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.SqliteService
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorModel
import com.android.tools.idea.sqlite.mocks.MockSchemaProvider
import com.android.tools.idea.sqlite.mocks.MockSqliteEditorViewFactory
import com.android.tools.idea.sqlite.mocks.MockSqliteView
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.android.tools.idea.sqliteExplorer.SqliteExplorerProjectService
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
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

class SqliteControllerTest : PlatformTestCase() {

  private lateinit var sqliteView: MockSqliteView
  private lateinit var edtExecutor: EdtExecutorService
  private lateinit var taskExecutor: Executor
  private lateinit var sqliteController: SqliteControllerImpl
  private lateinit var orderVerifier: InOrder

  private lateinit var viewFactory: MockSqliteEditorViewFactory

  private lateinit var sqliteDatabase1: SqliteDatabase
  private lateinit var sqliteDatabase2: SqliteDatabase
  private lateinit var sqliteDatabase3: SqliteDatabase

  private lateinit var testSqliteSchema1: SqliteSchema
  private lateinit var testSqliteSchema2: SqliteSchema
  private lateinit var testSqliteSchema3: SqliteSchema

  private lateinit var mockSqliteService: SqliteService

  private val testSqliteTable = SqliteTable("testTable", arrayListOf(), true)
  private lateinit var sqliteResultSet: SqliteResultSet

  override fun setUp() {
    super.setUp()

    viewFactory = spy(MockSqliteEditorViewFactory::class.java)

    testSqliteSchema1 = SqliteSchema(emptyList())
    testSqliteSchema2 = SqliteSchema(emptyList())
    testSqliteSchema3 = SqliteSchema(emptyList())

    sqliteView = spy(MockSqliteView::class.java)
    edtExecutor = EdtExecutorService.getInstance()
    taskExecutor = SameThreadExecutor.INSTANCE

    sqliteController = SqliteControllerImpl(
      project,
      MockDatabaseInspectorModel(),
      viewFactory,
      sqliteView,
      edtExecutor,
      taskExecutor
    )
    sqliteController.setUp()

    sqliteResultSet = mock(SqliteResultSet::class.java)
    `when`(sqliteResultSet.columns).thenReturn(Futures.immediateFuture(testSqliteTable.columns))

    mockSqliteService = mock(SqliteService::class.java)
    `when`(mockSqliteService.closeDatabase()).thenReturn(Futures.immediateFuture(null))
    `when`(mockSqliteService.executeQuery(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(sqliteResultSet))

    sqliteDatabase1 = SqliteDatabase("db1", mockSqliteService)
    sqliteDatabase2 = SqliteDatabase("db2", mockSqliteService)
    sqliteDatabase3 = SqliteDatabase("db", mockSqliteService)

    Disposer.register(project, sqliteDatabase1)
    Disposer.register(project, sqliteDatabase2)
    Disposer.register(project, sqliteDatabase3)

    orderVerifier = inOrder(sqliteView, mockSqliteService)
  }

  fun testAddSqliteDatabase() {
    // Prepare
    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))

    // Act
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(sqliteView).startLoading("Getting database...")
    orderVerifier.verify(mockSqliteService).readSchema()
    orderVerifier.verify(sqliteView).stopLoading()
  }

  fun testAddSqliteDatabaseFailure() {
    // Prepare
    val exception = IllegalStateException()
    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))

    // Act
    sqliteController.addSqliteDatabase(Futures.immediateFailedFuture(exception))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(sqliteView).reportError("Error getting database", exception)
    orderVerifier.verifyNoMoreInteractions()
  }

  fun testAddSqliteDatabaseFailureReadSchema() {
    // Prepare
    val exception = IllegalStateException()
    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFailedFuture(exception))

    // Act
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(sqliteView).startLoading("Getting database...")
    orderVerifier.verify(mockSqliteService).readSchema()
    orderVerifier.verify(sqliteView).reportError("Error reading Sqlite database", exception)
    orderVerifier.verifyNoMoreInteractions()
  }

  fun testAddSqliteDatabaseWhenControllerIsDisposed() {
    // Prepare
    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))

    // Act
    Disposer.dispose(sqliteController)
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(sqliteView).startLoading("Getting database...")
    orderVerifier.verifyNoMoreInteractions()
  }

  fun testDisplayResultSetIsCalledForTable() {
    // Prepare
    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    sqliteView.viewListeners.single().tableNodeActionInvoked(sqliteDatabase1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(sqliteView).openTab(
      eq(TabId.TableTab(sqliteDatabase1, testSqliteTable.name)),
      eq(testSqliteTable.name), any(JComponent::class.java)
    )
  }

  fun testDisplayResultSetIsCalledForEvaluatorView() {
    // Prepare
    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    sqliteView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(sqliteView)
      .openTab(any(TabId.AdHocQueryTab::class.java), any(String::class.java), any(JComponent::class.java))
  }

  fun testCloseTabIsCalledForTable() {
    // Prepare
    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    sqliteView.viewListeners.single().tableNodeActionInvoked(sqliteDatabase1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    sqliteView.viewListeners.single().closeTabActionInvoked(TabId.TableTab(sqliteDatabase1, testSqliteTable.name))

    // Assert
    verify(viewFactory).createTableView()
    verify(sqliteView).closeTab(eq(TabId.TableTab(sqliteDatabase1, testSqliteTable.name)))
  }

  fun testCloseTabIsCalledForEvaluatorView() {
    // Prepare
    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    sqliteView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    val tabId = sqliteView.lastDisplayedResultSetTabId
    assert(tabId is TabId.AdHocQueryTab)
    sqliteView.viewListeners.single().closeTabActionInvoked(tabId!!)

    // Assert
    verify(viewFactory).createEvaluatorView(any(Project::class.java), any(SchemaProvider::class.java), any(TableView::class.java))
    verify(sqliteView).closeTab(eq(tabId))
  }

  fun testFocusTabIsCalled() {
    // Prepare
    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    sqliteView.viewListeners.single().tableNodeActionInvoked(sqliteDatabase1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    sqliteView.viewListeners.single().tableNodeActionInvoked(sqliteDatabase1, testSqliteTable)

    // Assert
    verify(viewFactory).createTableView()
    verify(sqliteView)
      .openTab(
        eq(TabId.TableTab(sqliteDatabase1, testSqliteTable.name)),
        eq(testSqliteTable.name), any(JComponent::class.java)
      )
    verify(sqliteView).focusTab(eq(TabId.TableTab(sqliteDatabase1, testSqliteTable.name)))
  }

  fun testAddNewDatabaseAlphabeticOrder() {
    // Act
    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema2))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase2))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema3))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase3))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(sqliteView).addDatabaseSchema(sqliteDatabase1, testSqliteSchema1, 0)
    orderVerifier.verify(sqliteView).addDatabaseSchema(sqliteDatabase2, testSqliteSchema2, 1)
    orderVerifier.verify(sqliteView).addDatabaseSchema(sqliteDatabase3, testSqliteSchema3, 0)
  }

  fun testNewDatabaseIsAddedToEvaluator() {
    // Prepare
    sqliteView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema2))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase2))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema3))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase3))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val evaluatorView = viewFactory.createEvaluatorView(project, MockSchemaProvider(), viewFactory.tableView)
    verify(evaluatorView).addDatabase(sqliteDatabase1, 0)
    verify(evaluatorView).addDatabase(sqliteDatabase2, 1)
    verify(evaluatorView).addDatabase(sqliteDatabase3, 0)
  }

  fun testRemoveDatabase() {
    // Prepare
    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    sqliteView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    val evaluatorView = viewFactory.createEvaluatorView(project, MockSchemaProvider(), viewFactory.tableView)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    sqliteView.viewListeners.first().removeDatabaseActionInvoked(sqliteDatabase1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockSqliteService).closeDatabase()
    verify(evaluatorView).removeDatabase(0)
    verify(sqliteView).removeDatabaseSchema(sqliteDatabase1)
    assert(Disposer.isDisposed(sqliteDatabase1))
  }

  fun testTablesAreRemovedWhenDatabasedIsRemoved() {
    // Prepare
    val schema = SqliteSchema(listOf(SqliteTable("table1", emptyList(), false), testSqliteTable))
    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(schema))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    sqliteView.viewListeners.single().tableNodeActionInvoked(sqliteDatabase1, testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    sqliteView.viewListeners.first().removeDatabaseActionInvoked(sqliteDatabase1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(sqliteView).closeTab(eq(TabId.TableTab(sqliteDatabase1, testSqliteTable.name)))
  }

  fun testUpdateExistingDatabaseAddTables() {
    // Prepare
    val schema = SqliteSchema(emptyList())
    val newSchema = SqliteSchema(listOf(SqliteTable("table", emptyList(), false)))
    val evaluatorView = viewFactory.sqliteEvaluatorView

    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(schema))
    `when`(mockSqliteService.executeUpdate(SqliteStatement("INSERT"))).thenReturn(Futures.immediateFuture(0))

    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))

    sqliteView.viewListeners.first().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(newSchema))
    evaluatorView.listeners.forEach { it.evaluateSqlActionInvoked(sqliteDatabase1, "INSERT") }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(sqliteView).updateDatabase(
      sqliteDatabase1,
      listOf(SqliteTable("table", emptyList(), false))
    )
  }

  fun testSyncUpdatesView() {
    // Prepare
    `when`(mockSqliteService.readSchema()).thenReturn(Futures.immediateFuture(testSqliteSchema1))
    sqliteController.addSqliteDatabase(Futures.immediateFuture(sqliteDatabase1))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val mockSqliteExplorerProjectService = mock(SqliteExplorerProjectService::class.java)
    `when`(mockSqliteExplorerProjectService.sync(any(SqliteDatabase::class.java), any(DownloadProgress::class.java)))
      .thenReturn(Futures.immediateFuture(null))
    project.registerServiceInstance(SqliteExplorerProjectService::class.java, mockSqliteExplorerProjectService)

    // Act
    sqliteView.viewListeners.single().syncDatabaseActionInvoked(sqliteDatabase1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(sqliteView).reportSyncProgress(any(String::class.java))
  }
}