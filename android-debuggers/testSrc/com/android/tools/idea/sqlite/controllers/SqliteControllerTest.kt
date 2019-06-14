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
import com.android.tools.idea.sqlite.mocks.MockSqliteView
import com.android.tools.idea.sqlite.model.SqliteModel
import com.android.tools.idea.sqlite.model.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.mainView.SqliteViewListener
import com.google.common.util.concurrent.Futures
import com.intellij.concurrency.SameThreadExecutor
import com.intellij.openapi.util.Disposer
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

  private lateinit var sqliteModel: SqliteModel
  private lateinit var sqliteView: MockSqliteView
  private lateinit var sqliteService: SqliteService
  private lateinit var edtExecutor: EdtExecutorService
  private lateinit var taskExecutor: Executor
  private lateinit var sqliteController: SqliteController
  private lateinit var orderVerifier: InOrder

  private lateinit var viewFactory: MockSqliteEditorViewFactory

  private val testSqliteTable = SqliteTable("testTable", arrayListOf())
  private lateinit var sqliteResultSet: SqliteResultSet

  override fun setUp() {
    super.setUp()

    viewFactory = spy(MockSqliteEditorViewFactory::class.java)

    sqliteUtil = SqliteTestUtil(IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()

    val file = sqliteUtil.createTestSqliteDatabase()
    sqliteModel = SqliteModel(file)

    sqliteView = spy(MockSqliteView::class.java)
    sqliteService = mock(SqliteService::class.java)
    edtExecutor = EdtExecutorService.getInstance()
    taskExecutor = SameThreadExecutor.INSTANCE
    sqliteController = SqliteController(
      testRootDisposable, viewFactory, sqliteModel, sqliteView, sqliteService, edtExecutor, taskExecutor
    )

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

  fun testSetUp() {
    // Prepare
    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema.EMPTY))

    // Act
    sqliteController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(sqliteView).addListener(any(SqliteViewListener::class.java))
    orderVerifier.verify(sqliteView).setUp()
    orderVerifier.verify(sqliteView).startLoading("Opening Sqlite database...")

    orderVerifier.verify(sqliteService).openDatabase()
    orderVerifier.verify(sqliteService).readSchema()

    orderVerifier.verify(sqliteView).stopLoading()
  }

  fun testSetUpFailureOpenDatabase() {
    // Prepare
    val throwable = Throwable()
    `when`(sqliteService.openDatabase()).thenReturn(Futures.immediateFailedFuture(throwable))

    // Act
    sqliteController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(sqliteService).openDatabase()
    Mockito.verifyNoMoreInteractions(sqliteService)

    orderVerifier.verify(sqliteView)
      .reportErrorRelatedToService(eq(sqliteService), eq("Error opening Sqlite database"), refEq(throwable))
  }

  fun testSetUpFailureReadSchema() {
    // Prepare
    val throwable = Throwable()
    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFailedFuture(throwable))

    // Act
    sqliteController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(sqliteView).reportErrorRelatedToService(eq(sqliteService), eq("Error opening Sqlite database"), refEq(throwable))
  }

  fun testSetUpIsDisposed() {
    // Prepare
    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema.EMPTY))

    // Act
    Disposer.dispose(sqliteController)
    sqliteController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(sqliteView, times(0)).stopLoading()
  }

  fun testSetUpFailureIsDisposed() {
    // Prepare
    val throwable = Throwable()
    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFailedFuture(throwable))

    // Act
    Disposer.dispose(sqliteController)
    sqliteController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(sqliteView, times(0))
      .reportErrorRelatedToService(eq(sqliteService), eq("Error opening Sqlite database"), refEq(throwable))
  }

  fun testDisplayTableIsCalled() {
    // Prepare
    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema.EMPTY))
    sqliteController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    sqliteView.viewListeners.single().tableNodeActionInvoked(testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(sqliteView).displayTable(eq(testSqliteTable.name), any(JComponent::class.java))
  }

  fun testCloseTableIsCalled() {
    // Prepare
    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema.EMPTY))
    sqliteController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    sqliteView.viewListeners.single().tableNodeActionInvoked(testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    sqliteView.viewListeners.single().closeTableActionInvoked(testSqliteTable.name)

    // Assert
    verify(viewFactory).createTableView()
    verify(sqliteView).closeTable(eq(testSqliteTable.name))
  }

  fun testFocusTableIsCalled() {
    // Prepare
    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema.EMPTY))
    sqliteController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    sqliteView.viewListeners.single().tableNodeActionInvoked(testSqliteTable)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    sqliteView.viewListeners.single().tableNodeActionInvoked(testSqliteTable)

    // Assert
    verify(viewFactory).createTableView()
    verify(sqliteView).displayTable(eq(testSqliteTable.name), any(JComponent::class.java))
    verify(sqliteView).focusTable(eq(testSqliteTable.name))
  }
}