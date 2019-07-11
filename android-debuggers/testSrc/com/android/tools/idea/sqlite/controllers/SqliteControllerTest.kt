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
import com.android.testutils.MockitoKt.refEq
import com.android.tools.idea.editors.sqlite.SqliteTestUtil
import com.android.tools.idea.sqlite.SqliteService
import com.android.tools.idea.sqlite.mocks.MockSqliteEditorViewFactory
import com.android.tools.idea.sqlite.mocks.MockSqliteServiceFactory
import com.android.tools.idea.sqlite.mocks.MockSqliteView
import com.android.tools.idea.sqlite.model.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.google.common.util.concurrent.Futures
import com.intellij.concurrency.SameThreadExecutor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.concurrency.EdtExecutorService
import org.mockito.InOrder
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.util.concurrent.Executor
import javax.swing.JComponent

class SqliteControllerTest : PlatformTestCase() {

  private lateinit var sqliteUtil: SqliteTestUtil

  private lateinit var sqliteView: MockSqliteView
  private lateinit var sqliteService: SqliteService
  private lateinit var edtExecutor: EdtExecutorService
  private lateinit var taskExecutor: Executor
  private lateinit var sqliteController: SqliteController
  private lateinit var orderVerifier: InOrder

  private lateinit var sqliteServiceFactory: MockSqliteServiceFactory
  private lateinit var viewFactory: MockSqliteEditorViewFactory

  private lateinit var sqliteFile: VirtualFile

  private val testSqliteTable = SqliteTable("testTable", arrayListOf())
  private lateinit var sqliteResultSet: SqliteResultSet

  override fun setUp() {
    super.setUp()

    sqliteServiceFactory = MockSqliteServiceFactory()
    viewFactory = spy(MockSqliteEditorViewFactory::class.java)

    sqliteUtil = SqliteTestUtil(IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()

    sqliteFile = sqliteUtil.createTestSqliteDatabase()

    sqliteView = spy(MockSqliteView::class.java)
    sqliteService = sqliteServiceFactory.sqliteService
    edtExecutor = EdtExecutorService.getInstance()
    taskExecutor = SameThreadExecutor.INSTANCE
    sqliteController = SqliteController(
      testRootDisposable, sqliteServiceFactory, viewFactory, sqliteView, edtExecutor, taskExecutor
    )
    sqliteController.setUp()

    sqliteResultSet = mock(SqliteResultSet::class.java)
    `when`(sqliteResultSet.columns).thenReturn(Futures.immediateFuture(testSqliteTable.columns))

    `when`(sqliteService.openDatabase()).thenReturn(Futures.immediateFuture(Unit))
    `when`(sqliteService.readTable(testSqliteTable)).thenReturn(Futures.immediateFuture(sqliteResultSet))

    orderVerifier = inOrder(sqliteView, sqliteService)
  }

  override fun tearDown() {
    try {
      sqliteUtil.tearDown()
    } finally {
      super.tearDown()
    }
  }

  fun testOpenSqliteDatabase() {
    // Prepare
    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema.EMPTY))

    // Act
    sqliteController.openSqliteDatabase(sqliteFile)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(sqliteView).startLoading("Opening Sqlite database...")

    orderVerifier.verify(sqliteService).openDatabase()
    orderVerifier.verify(sqliteService).readSchema()

    orderVerifier.verify(sqliteView).stopLoading()
  }

  fun testOpenSqliteDatabaseFailureOpenDatabase() {
    // Prepare
    val throwable = Throwable()
    `when`(sqliteService.openDatabase()).thenReturn(Futures.immediateFailedFuture(throwable))

    // Act
    sqliteController.openSqliteDatabase(sqliteFile)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(sqliteService).openDatabase()
    Mockito.verifyNoMoreInteractions(sqliteService)

    orderVerifier.verify(sqliteView)
      .reportErrorRelatedToService(eq(sqliteService), eq("Error opening Sqlite database"), refEq(throwable))
  }

  fun testOpenSqliteDatabaseFailureReadSchema() {
    // Prepare
    val throwable = Throwable()
    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFailedFuture(throwable))

    // Act
    sqliteController.openSqliteDatabase(sqliteFile)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(sqliteView).reportErrorRelatedToService(eq(sqliteService), eq("Error opening Sqlite database"), refEq(throwable))
  }

  fun testOpenSqliteDatabaseIsDisposed() {
    // Prepare
    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema.EMPTY))

    // Act
    Disposer.dispose(sqliteController)
    sqliteController.openSqliteDatabase(sqliteFile)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(sqliteView, times(0)).stopLoading()
  }

  fun testOpenSqliteDatabaseFailureIsDisposed() {
    // Prepare
    val throwable = Throwable()
    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFailedFuture(throwable))

    // Act
    Disposer.dispose(sqliteController)
    sqliteController.openSqliteDatabase(sqliteFile)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(sqliteView, times(0))
      .reportErrorRelatedToService(eq(sqliteService), eq("Error opening Sqlite database"), refEq(throwable))
  }

  fun testDisplayResultSetIsCalledForTable() {
    // Prepare
    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema.EMPTY))
    sqliteController.openSqliteDatabase(sqliteFile)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    sqliteView.viewListeners.single().tableNodeActionInvoked(testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(sqliteView).displayResultSet(eq(TabId.TableTab(testSqliteTable.name)), eq(testSqliteTable.name), any(JComponent::class.java))
  }

  fun testDisplayResultSetIsCalledForEvaluatorView() {
    // Prepare
    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema.EMPTY))
    sqliteController.openSqliteDatabase(sqliteFile)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    sqliteView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(sqliteView)
      .displayResultSet(any(TabId.AdHocQueryTab::class.java), any(String::class.java), any(JComponent::class.java))
  }

  fun testCloseTabIsCalledForTable() {
    // Prepare
    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema.EMPTY))
    sqliteController.openSqliteDatabase(sqliteFile)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    sqliteView.viewListeners.single().tableNodeActionInvoked(testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    sqliteView.viewListeners.single().closeTableActionInvoked(TabId.TableTab(testSqliteTable.name))

    // Assert
    verify(viewFactory).createTableView()
    verify(sqliteView).closeTab(eq(TabId.TableTab(testSqliteTable.name)))
  }

  fun testCloseTabIsCalledForEvaluatorView() {
    // Prepare
    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema.EMPTY))
    sqliteController.openSqliteDatabase(sqliteFile)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    sqliteView.viewListeners.single().openSqliteEvaluatorTabActionInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    val tabId = sqliteView.lastDisplayedResultSetTabId
    assert(tabId is TabId.AdHocQueryTab)
    sqliteView.viewListeners.single().closeTableActionInvoked(tabId!!)

    // Assert
    verify(viewFactory).createEvaluatorView()
    verify(sqliteView).closeTab(eq(tabId))
  }

  fun testFocusTabIsCalled() {
    // Prepare
    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema.EMPTY))
    sqliteController.openSqliteDatabase(sqliteFile)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    sqliteView.viewListeners.single().tableNodeActionInvoked(testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    sqliteView.viewListeners.single().tableNodeActionInvoked(testSqliteTable)

    // Assert
    verify(viewFactory).createTableView()
    verify(sqliteView)
      .displayResultSet(eq(TabId.TableTab(testSqliteTable.name)), eq(testSqliteTable.name), any(JComponent::class.java))
    verify(sqliteView).focusTab(eq(TabId.TableTab(testSqliteTable.name)))
  }
}