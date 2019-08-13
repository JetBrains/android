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
import com.android.tools.idea.concurrent.FutureCallbackExecutor
import com.android.tools.idea.sqlite.SqliteService
import com.android.tools.idea.sqlite.mocks.MockSqliteEvaluatorView
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteResultSet
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorViewListener
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.concurrency.EdtExecutorService
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify

class SqliteEvaluatorControllerTest : UsefulTestCase() {

  private lateinit var sqliteEvaluatorView: MockSqliteEvaluatorView
  private lateinit var sqliteService: SqliteService
  private lateinit var edtExecutor: FutureCallbackExecutor
  private lateinit var sqliteEvaluatorController: SqliteEvaluatorController
  private lateinit var sqliteDatabase: SqliteDatabase

  override fun setUp() {
    super.setUp()
    sqliteEvaluatorView = spy(MockSqliteEvaluatorView::class.java)
    sqliteService = mock(SqliteService::class.java)
    edtExecutor = FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())
    sqliteEvaluatorController = SqliteEvaluatorController(testRootDisposable, sqliteEvaluatorView, edtExecutor)
    val virtualFile = mock(VirtualFile::class.java)
    `when`(virtualFile.path).thenReturn("data/data/myapp/databases/file.db")
    sqliteDatabase = SqliteDatabase(virtualFile, sqliteService)
  }

  fun testSetUp() {
    // Act
    sqliteEvaluatorController.setUp()

    // Assert
    verify(sqliteEvaluatorView).addListener(any(SqliteEvaluatorViewListener::class.java))
  }

  fun testEvaluateSqlActionQuerySuccess() {
    // Prepare
    val sqlStatement = "SELECT"
    `when`(sqliteService.executeQuery(sqlStatement)).thenReturn(Futures.immediateFuture(any(SqliteResultSet::class.java)))

    sqliteEvaluatorController.setUp()

    // Act
    sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, sqlStatement)

    // Assert
    verify(sqliteService).executeQuery(sqlStatement)
  }

  fun testEvaluateSqlActionQueryFailure() {
    // Prepare
    val sqlStatement = "SELECT"
    val throwable = Throwable()
    `when`(sqliteService.executeQuery(sqlStatement)).thenReturn(Futures.immediateFailedFuture(throwable))

    sqliteEvaluatorController.setUp()

    // Act
    sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, sqlStatement)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(sqliteService).executeQuery(sqlStatement)
    verify(sqliteEvaluatorView.tableView).reportError(eq("Error executing sqlQueryCommand"), refEq(throwable))
  }

  fun testEvaluateSqlActionCreateSuccess() {
    evaluateSqlActionSuccess("CREATE")
  }

  fun testEvaluateSqlActionCreateFailure() {
    evaluateSqlActionFailure("CREATE")
  }

  fun testEvaluateSqlActionDropSuccess() {
    evaluateSqlActionSuccess("DROP")
  }

  fun testEvaluateSqlActionDropFailure() {
    evaluateSqlActionFailure("DROP")
  }

  fun testEvaluateSqlActionAlterSuccess() {
    evaluateSqlActionSuccess("ALTER")
  }

  fun testEvaluateSqlActionAlterFailure() {
    evaluateSqlActionFailure("ALTER")
  }

  fun testEvaluateSqlActionInsertSuccess() {
    evaluateSqlActionSuccess("INSERT")
  }

  fun testEvaluateSqlActionInsertFailure() {
    evaluateSqlActionFailure("INSERT")
  }

  fun testEvaluateSqlActionUpdateSuccess() {
    evaluateSqlActionSuccess("UPDATE")
  }

  fun testEvaluateSqlActionUpdateFailure() {
    evaluateSqlActionFailure("UPDATE")
  }

  fun testEvaluateSqlActionDeleteSuccess() {
    evaluateSqlActionSuccess("DELETE")
  }

  fun testEvaluateSqlActionDeleteFailure() {
    evaluateSqlActionFailure("DELETE")
  }

  private fun evaluateSqlActionSuccess(action: String) {
    // Prepare
    `when`(sqliteService.executeUpdate(anyString())).thenReturn(Futures.immediateFuture(0))

    sqliteEvaluatorController.setUp()

    // Act
    sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, action)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(sqliteService).executeUpdate(action)
    verify(sqliteEvaluatorView.tableView).resetView()
  }

  private fun evaluateSqlActionFailure(action: String) {
    // Prepare
    val throwable = Throwable()
    `when`(sqliteService.executeUpdate(anyString())).thenReturn(Futures.immediateFailedFuture(throwable))

    sqliteEvaluatorController.setUp()

    // Act
    sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, action)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(sqliteService).executeUpdate(action)
    verify(sqliteEvaluatorView.tableView).reportError(eq("Error executing update"), refEq(throwable))
  }
}