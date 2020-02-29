/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.sqlite.ui

import com.android.testutils.MockitoKt.any
import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.concurrency.AsyncTestUtils
import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.controllers.SqliteEvaluatorController
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.fileType.SqliteTestUtil
import com.android.tools.idea.sqlite.getJdbcDatabaseConnection
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorViewsFactory
import com.android.tools.idea.sqlite.model.FileSqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorViewImpl
import com.android.tools.idea.sqlite.ui.tableView.TableViewImpl
import com.android.tools.idea.testing.IdeComponents
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.concurrency.EdtExecutorService
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.awt.Dimension
import javax.swing.JComboBox
import javax.swing.JTable

class SqliteEvaluatorViewImplTest : LightJavaCodeInsightFixtureTestCase() {
  private lateinit var view: SqliteEvaluatorViewImpl
  private lateinit var mockSchemaProvider: SchemaProvider

  private lateinit var sqliteUtil: SqliteTestUtil
  private var realDatabaseConnection: DatabaseConnection? = null

  override fun setUp() {
    super.setUp()
    mockSchemaProvider = mock(SchemaProvider::class.java)
    `when`(mockSchemaProvider.getSchema(any(SqliteDatabase::class.java))).thenReturn(SqliteSchema(emptyList()))

    view = SqliteEvaluatorViewImpl(project, TableViewImpl(), mockSchemaProvider)
    view.component.size = Dimension(600, 200)

    sqliteUtil = SqliteTestUtil(IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()
  }

  override fun tearDown() {
    try {
      if (realDatabaseConnection != null) {
        pumpEventsAndWaitForFuture(realDatabaseConnection!!.close())
      }
      sqliteUtil.tearDown()
    } finally {
      super.tearDown()
    }
  }

  fun testAddAndRemoveDatabases() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val comboBox = treeWalker.descendants().filterIsInstance<JComboBox<*>>().first()

    // Act/Assert
    assertEquals(-1, comboBox.selectedIndex)

    view.addDatabase(FileSqliteDatabase("db1", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java)), 0)
    assertEquals(0, comboBox.selectedIndex)

    view.addDatabase(FileSqliteDatabase("db2", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java)), 1)
    assertEquals(0, comboBox.selectedIndex)

    view.removeDatabase(0)
    assertEquals(0, comboBox.selectedIndex)

    view.removeDatabase(0)
    assertEquals(-1, comboBox.selectedIndex)
  }

  fun testSelectDatabaseChangesSelectedDatabase() {
    // Prepare
    val database1 = FileSqliteDatabase("db1", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java))
    val database2 = FileSqliteDatabase("db2", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java))

    // Act/Assert
    view.addDatabase(database1, 0)
    view.addDatabase(database2, 0)
    assertEquals(database1, view.getActiveDatabase())

    view.selectDatabase(database2)
    assertEquals(database2, view.getActiveDatabase())
  }

  fun testPsiCacheIsDroppedWhenNewDatabaseIsSelected() {
    // Prepare
    val ideComponents = IdeComponents(myFixture)
    val mockPsiManager = mock(PsiManager::class.java)
    ideComponents.replaceProjectService(PsiManager::class.java, mockPsiManager)

    val comboBox = TreeWalker(view.component).descendants().filterIsInstance<JComboBox<*>>().first()

    val database1 = FileSqliteDatabase("db1", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java))
    val database2 = FileSqliteDatabase("db2", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java))

    // Act/Assert
    view.addDatabase(database1, 0)
    view.addDatabase(database2, 1)
    verify(mockPsiManager).dropPsiCaches()

    view.selectDatabase(database2)
    verify(mockPsiManager, times(2)).dropPsiCaches()

    comboBox.selectedIndex = 0
    verify(mockPsiManager, times(3)).dropPsiCaches()
  }

  fun testSchemaUpdatedDropsCachesAndGetsNewSchema() {
    // Prepare
    val ideComponents = IdeComponents(myFixture)
    val mockPsiManager = mock(PsiManager::class.java)
    ideComponents.replaceProjectService(PsiManager::class.java, mockPsiManager)

    val database = FileSqliteDatabase("db1", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java))

    view.addDatabase(database, 0)

    // Act
    view.schemaChanged(database)

    // Assert
    verify(mockPsiManager, times(2)).dropPsiCaches()
    verify(mockSchemaProvider, times(2)).getSchema(database)
  }

  fun testTableActionsAreNotVisibleInEvaluatorView() {
    // Prepare
    val tableViewTreeWalker = TreeWalker(view.tableView.component)
    val tableActionsPanel = tableViewTreeWalker.descendants().first { it.name == "table-actions-panel" }

    val evaluatorController = SqliteEvaluatorController(
      project,
      view,
      MockDatabaseInspectorViewsFactory(),
      FutureCallbackExecutor(EdtExecutorService.getInstance())
    )

    // Act
    evaluatorController.setUp()

    // Assert
    LightPlatformTestCase.assertFalse(tableActionsPanel.isVisible)
  }

  fun testMultipleStatementAreRun() {
    // Prepare
    val sqliteFile = sqliteUtil.createAdHocSqliteDatabase(
      createStatement = "CREATE TABLE t1 (c1 INT)",
      insertStatement = "INSERT INTO t1 (c1) VALUES (42)"
    )
    realDatabaseConnection = AsyncTestUtils.pumpEventsAndWaitForFuture(
      getJdbcDatabaseConnection(sqliteFile, FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()))
    )

    val database = FileSqliteDatabase("db", realDatabaseConnection!!, sqliteFile)

    val controller = SqliteEvaluatorController(
      project,
      view,
      MockDatabaseInspectorViewsFactory(),
      FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())
    )
    controller.setUp()
    Disposer.register(testRootDisposable, controller)

    controller.addDatabase(database, 0)

    val table = TreeWalker(view.component).descendants().filterIsInstance<JTable>().first()

    // Act
    pumpEventsAndWaitForFuture(controller.evaluateSqlStatement(database, SqliteStatement("SELECT * FROM t1")))

    // Assert
    assertEquals(2, table.model.columnCount)
    assertEquals("c1", table.model.getColumnName(1))
    assertEquals(1, table.model.rowCount)
    assertEquals("42", table.model.getValueAt(0, 1))

    // Act
    pumpEventsAndWaitForFuture(controller.evaluateSqlStatement(database, SqliteStatement("SELECT * FROM t1")))

    // Assert
    assertEquals(2, table.model.columnCount)
    assertEquals("c1", table.model.getColumnName(1))
    assertEquals(1, table.model.rowCount)
    assertEquals("42", table.model.getValueAt(0, 1))
  }
}