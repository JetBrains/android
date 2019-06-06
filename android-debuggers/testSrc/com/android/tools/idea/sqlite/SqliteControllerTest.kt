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

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.refEq
import com.android.tools.idea.editors.sqlite.SqliteTestUtil
import com.android.tools.idea.sqlite.controllers.SqliteController
import com.android.tools.idea.sqlite.mocks.MockSqliteEditorViewFactory
import com.android.tools.idea.sqlite.mocks.MockSqliteView
import com.android.tools.idea.sqlite.model.SqliteModel
import com.android.tools.idea.sqlite.model.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.mainView.SqliteView
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

class SqliteControllerTest : PlatformTestCase() {

  private lateinit var sqliteUtil: SqliteTestUtil

  private lateinit var sqliteModel: SqliteModel
  private lateinit var mockSqliteView: MockSqliteView
  private lateinit var spySqliteView: SqliteView
  private lateinit var sqliteService: SqliteService
  private lateinit var edtExecutor: EdtExecutorService
  private lateinit var taskExecutor: Executor
  private lateinit var sqliteController: SqliteController
  private lateinit var orderVerifier: InOrder

  private val viewFactory = MockSqliteEditorViewFactory()

  private val testSqliteTable = SqliteTable("testTable", arrayListOf())

  override fun setUp() {
    super.setUp()

    sqliteUtil = SqliteTestUtil(IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()

    val file = sqliteUtil.createTestSqliteDatabase()
    sqliteModel = SqliteModel(file)

    mockSqliteView = MockSqliteView()
    spySqliteView = spy(mockSqliteView)
    sqliteService = mock(SqliteService::class.java)
    edtExecutor = EdtExecutorService.getInstance()
    taskExecutor = SameThreadExecutor.INSTANCE
    sqliteController = SqliteController(
      testRootDisposable, viewFactory, sqliteModel, spySqliteView, sqliteService, edtExecutor, taskExecutor
    )

    `when`(sqliteService.openDatabase()).thenReturn(Futures.immediateFuture(Unit))

    orderVerifier = inOrder(spySqliteView, sqliteService)
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
    orderVerifier.verify(spySqliteView).addListener(any(SqliteViewListener::class.java))
    orderVerifier.verify(spySqliteView).setUp()
    orderVerifier.verify(spySqliteView).startLoading("Opening Sqlite database...")

    orderVerifier.verify(sqliteService).openDatabase()
    orderVerifier.verify(sqliteService).readSchema()

    orderVerifier.verify(spySqliteView).stopLoading()
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

    orderVerifier.verify(spySqliteView)
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
    verify(spySqliteView).reportErrorRelatedToService(eq(sqliteService), eq("Error opening Sqlite database"), refEq(throwable))
  }

  fun testSetUpIsDisposed() {
    // Prepare
    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema.EMPTY))

    // Act
    Disposer.dispose(sqliteController)
    sqliteController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(spySqliteView, times(0)).stopLoading()
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
    orderVerifier.verify(spySqliteView, times(0))
      .reportErrorRelatedToService(eq(sqliteService), eq("Error opening Sqlite database"), refEq(throwable))
  }

  fun testUpdateViewResetView() {
    // Prepare
    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFuture(SqliteSchema.EMPTY))

    sqliteController.setUp()
    mockSqliteView.viewListeners.first().openSqliteEvaluatorActionInvoked()

    // Act
    viewFactory.sqliteEvaluatorView.listeners[1].evaluateSqlActionInvoked("")
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(spySqliteView).resetView()
  }

  fun testUpdateViewRefreshView() {
    // Prepare
    val sqliteSchema = object : SqliteSchema {
      override val tables: List<SqliteTable> = listOf(testSqliteTable)
    }

    `when`(sqliteService.readSchema()).thenReturn(Futures.immediateFuture(sqliteSchema))
    `when`(sqliteService.readTable(testSqliteTable)).thenReturn(Futures.immediateFuture(any(SqliteResultSet::class.java)))

    sqliteController.setUp()
    mockSqliteView.viewListeners.first().openSqliteEvaluatorActionInvoked()

    // Act
    mockSqliteView.viewListeners.single().tableNodeActionInvoked(testSqliteTable)
    viewFactory.sqliteEvaluatorView.listeners[1].evaluateSqlActionInvoked("")
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(spySqliteView, times(0)).resetView()
  }
}