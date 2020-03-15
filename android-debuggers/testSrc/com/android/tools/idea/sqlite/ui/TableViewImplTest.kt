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
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.controllers.TableController
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.jdbc.selectAllAndRowIdFromTable
import com.android.tools.idea.sqlite.fileType.SqliteTestUtil
import com.android.tools.idea.sqlite.getJdbcDatabaseConnection
import com.android.tools.idea.sqlite.model.FileSqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.SqliteValue
import com.android.tools.idea.sqlite.ui.tableView.RowDiffOperation
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.android.tools.idea.sqlite.ui.tableView.TableViewImpl
import com.android.tools.idea.testing.IdeComponents
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.concurrency.EdtExecutorService
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.awt.Dimension
import java.awt.Point
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTable

private const val COLUMN_DEFAULT_WIDTH = 75

class TableViewImplTest : LightJavaCodeInsightFixtureTestCase() {
  private lateinit var view: TableViewImpl
  private lateinit var fakeUi: FakeUi
  private lateinit var mockActionManager: ActionManager

  private lateinit var sqliteUtil: SqliteTestUtil
  private var realDatabaseConnection: DatabaseConnection? = null

  private val database = FileSqliteDatabase("db", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java))

  override fun setUp() {
    super.setUp()

    val mockPopUpMenu = mock(ActionPopupMenu::class.java)
    `when`(mockPopUpMenu.component).thenReturn(mock(JPopupMenu::class.java))
    mockActionManager = mock(ActionManager::class.java)
    `when`(mockActionManager.createActionPopupMenu(any(String::class.java), any(ActionGroup::class.java))).thenReturn(mockPopUpMenu)

    IdeComponents(myFixture).replaceApplicationService(ActionManager::class.java, mockActionManager)

    view = TableViewImpl()
    val component: JPanel = view.component as JPanel
    component.size = Dimension(600, 200)

    fakeUi = FakeUi(component)

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

  fun testColumnsFitParentIfSpaceIsAvailable() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val column = SqliteColumn("name", SqliteAffinity.NUMERIC, false, false)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()
    val jbScrollPane = TreeWalker(table).ancestors().filterIsInstance<JBScrollPane>().first()

    // Act
    view.showTableColumns(listOf(column))
    fakeUi.layout()

    // Assert
    assertEquals(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS, table.autoResizeMode)

    assertEquals(598, table.size.width)
    assertEquals(60, table.columnModel.getColumn(0).width)
    assertEquals(538, table.columnModel.getColumn(1).width)

    assertEquals(0, jbScrollPane.horizontalScrollBar.model.minimum)
    assertEquals(598, jbScrollPane.horizontalScrollBar.model.maximum)
  }

  fun testTableIsScrollableIfTooManyColumns() {
    // Prepare
    val treeWalker = TreeWalker(view.component)

    val column = SqliteColumn("name", SqliteAffinity.NUMERIC, false, false)
    val columns = mutableListOf<SqliteColumn>()
    repeat(600 / COLUMN_DEFAULT_WIDTH) {
      columns.add(column)
    }

    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()
    val jbScrollPane = TreeWalker(table).ancestors().filterIsInstance<JBScrollPane>().first()

    // Act
    view.showTableColumns(columns)
    fakeUi.layout()

    // Assert
    assertEquals(JTable.AUTO_RESIZE_OFF, table.autoResizeMode)

    assertTrue(table.size.width > 598)
    assertEquals(COLUMN_DEFAULT_WIDTH, table.columnModel.getColumn(1).width)

    assertEquals(0, jbScrollPane.horizontalScrollBar.model.minimum)
    assertTrue(jbScrollPane.horizontalScrollBar.model.maximum > 598)
  }

  fun testSetEditableHidesReadOnlyLabelAndEnablesCellEditing() {
    // Prepare
    val treeWalker = TreeWalker(view.component)

    val readOnlyLabel = treeWalker.descendants().first { it.name == "read-only-label" }
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    view.showTableColumns(emptyList())

    // Act
    view.setEditable(true)

    // Assert
    assertFalse(readOnlyLabel.isVisible)
    assertTrue(table.model.isCellEditable(0, 1))
  }

  fun testSetNotEditableShowsReadOnlyLabelAndDisableCellEditing() {
    // Prepare
    val treeWalker = TreeWalker(view.component)

    val readOnlyLabel = treeWalker.descendants().first { it.name == "read-only-label" }
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    view.showTableColumns(emptyList())

    // Act
    view.setEditable(false)


    // Assert
    assertTrue(readOnlyLabel.isVisible)
    assertFalse(table.model.isCellEditable(0, 0))
  }

  fun testClickOnColumnHeaderSortsTable() {
    // Prepare
    val treeWalker = TreeWalker(view.component)

    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val mockListener = mock(TableView.Listener::class.java)
    view.addListener(mockListener)

    val col = SqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rows = listOf(SqliteRow(listOf(SqliteColumnValue(col.name, SqliteValue.StringValue("val")))))

    view.startTableLoading()
    view.showTableColumns(cols)
    view.updateRows(rows.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    table.size = Dimension(600, 200)
    table.tableHeader.size = Dimension(600, 100)

    table.preferredSize = table.size
    TreeWalker(table).descendants().forEach { it.doLayout() }

    fakeUi = FakeUi(table.tableHeader)

    // Act
    fakeUi.mouse.click(597, 0)

    // Assert
    assertEquals(1, table.columnAtPoint(Point(597, 0)))
    verify(mockListener).toggleOrderByColumnInvoked(col)
  }

  fun testClickOnFirstColumnHeaderDoesNotSortTable() {
    // Prepare
    val treeWalker = TreeWalker(view.component)

    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val mockListener = mock(TableView.Listener::class.java)
    view.addListener(mockListener)

    val col = SqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rows = listOf(SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val")))))

    view.startTableLoading()
    view.showTableColumns(cols)
    view.updateRows(rows.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    table.size = Dimension(600, 200)
    table.tableHeader.size = Dimension(600, 100)

    table.preferredSize = table.size
    TreeWalker(table).descendants().forEach { it.doLayout() }

    fakeUi = FakeUi(table.tableHeader)

    // Act
    fakeUi.mouse.click(0, 0)

    // Assert
    assertEquals(0, table.columnAtPoint(Point(0, 0)))
    verify(mockListener, times(0)).toggleOrderByColumnInvoked(col)
  }

  fun testColumnsAreResizableExceptForFirstColumn() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val col = SqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rows = listOf(SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val")))))

    view.startTableLoading()
    view.showTableColumns(cols)
    view.updateRows(rows.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    // Assert
    assertFalse(table.columnModel.getColumn(0).resizable)
    assertTrue(table.columnModel.getColumn(1).resizable)
  }

  fun testColumnsAreNamedCorrectly() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val col = SqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rows = listOf(SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val")))))

    view.startTableLoading()
    view.showTableColumns(cols)
    view.updateRows(rows.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    // Assert
    assertEquals("", table.model.getColumnName(0))
    assertEquals("col", table.model.getColumnName(1))
  }

  fun testRowsHaveExpectedValues() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val col = SqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rows = listOf(
      SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val1")))),
      SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val2"))))
    )

    view.startTableLoading()
    view.showTableColumns(cols)
    view.updateRows(rows.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    // Assert
    assertEquals(listOf("1", "2"), getColumnAt(table, 0))
    assertEquals(listOf("val1", "val2"), getColumnAt(table, 1))
  }

  fun testSetValueInColumnsOtherThanFirstIsAllowed() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val mockListener = mock(TableView.Listener::class.java)
    view.addListener(mockListener)

    val col = SqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val row = SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val1"))))
    val rows = listOf(row)

    view.startTableLoading()
    view.showTableColumns(cols)
    view.updateRows(rows.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    // Act
    table.model.setValueAt("newValue", 0, 1)

    // Assert
    verify(mockListener).updateCellInvoked(0, col, SqliteValue.StringValue("newValue"))
  }

  fun testColumnsAreEditableExceptForFirst() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val col = SqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rows = listOf(SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val1")))))

    view.startTableLoading()
    view.showTableColumns(cols)
    view.updateRows(rows.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()
    view.setEditable(true)

    // Assert
    assertFalse(table.model.isCellEditable(0, 0))
    assertTrue(table.model.isCellEditable(0, 1))
  }

  fun `testShowRows Add`() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val col = SqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rows = listOf(SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val1")))))

    // Act
    view.startTableLoading()
    view.showTableColumns(cols)
    view.updateRows(rows.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    // Assert
    assertEquals(1, table.model.rowCount)
    assertEquals("val1", table.model.getValueAt(0, 1))
  }

  fun `testShowRows Add UpdateRemove`() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val col = SqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rowsToAdd = listOf(
      SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val1")))),
      SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val2"))))
    )

    // Act
    view.startTableLoading()
    view.showTableColumns(cols)
    view.updateRows(rowsToAdd.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    view.updateRows(
      listOf(
        RowDiffOperation.UpdateCell(SqliteColumnValue("col", SqliteValue.StringValue("new val")), 0, 0),
        RowDiffOperation.RemoveLastRows(1))
    )

    // Assert
    assertEquals(1, table.model.rowCount)
    assertEquals("new val", table.model.getValueAt(0, 1))
  }

  fun `testShowRows Add Update UpdateAdd`() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val col = SqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rowsToAdd = listOf(
      SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val1")))),
      SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val2"))))
    )

    // Act
    view.startTableLoading()
    view.showTableColumns(cols)
    view.updateRows(rowsToAdd.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    view.updateRows(listOf(RowDiffOperation.UpdateCell(SqliteColumnValue("col", SqliteValue.StringValue("new val")), 0, 0)))

    view.updateRows(
      listOf(
        RowDiffOperation.UpdateCell(SqliteColumnValue("col", SqliteValue.StringValue("new val1")), 0, 0),
        RowDiffOperation.AddRow(SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("new val3"))))),
        RowDiffOperation.AddRow(SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("new val4")))))
    ))

    // Assert
    assertEquals(4, table.model.rowCount)
    assertEquals(listOf("new val1", "val2", "new val3", "new val4"), getColumnAt(table, 1))
  }

  fun testEditTableUsingPrimaryKey() {
    // Prepare
    val customSqliteFile = sqliteUtil.createAdHocSqliteDatabase(
      createStatement = "CREATE TABLE t1 (pk INT PRIMARY KEY, c1 INT)",
      insertStatement = "INSERT INTO t1 (pk, c1) VALUES (42, 1)"
    )
    realDatabaseConnection = pumpEventsAndWaitForFuture(
      getJdbcDatabaseConnection(customSqliteFile, FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()))
    )

    val schema = pumpEventsAndWaitForFuture(realDatabaseConnection!!.readSchema())
    val sqliteTable = schema.tables.first()

    val controller = TableController(
      project,
      10,
      view,
      { sqliteTable },
      realDatabaseConnection!!,
      SqliteStatement(selectAllAndRowIdFromTable(sqliteTable)),
      EdtExecutorService.getInstance(),
      EdtExecutorService.getInstance()
    )
    Disposer.register(testRootDisposable, controller)
    pumpEventsAndWaitForFuture(controller.setUp())

    val swingTable = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()
    val tableModel = swingTable.model

    // Act
    tableModel.setValueAt(0, 0, 2)

    // Assert
    val resultSet = pumpEventsAndWaitForFuture(realDatabaseConnection!!.execute(SqliteStatement("SELECT * FROM t1")))
    val rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 10))
    assertSize(1, rows)
    assertEquals(SqliteValue.fromAny(42), rows.first().values[0].value)
    assertEquals(SqliteValue.fromAny(0), rows.first().values[1].value)
  }

  fun testEditTableUsingRowId() {
    // Prepare
    val customSqliteFile = sqliteUtil.createAdHocSqliteDatabase(
      createStatement = "CREATE TABLE t1 (c1 INT, c2 INT)",
      insertStatement = "INSERT INTO t1 (c1, c2) VALUES (42, 1)"
    )
    realDatabaseConnection = pumpEventsAndWaitForFuture(
      getJdbcDatabaseConnection(customSqliteFile, FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()))
    )

    val schema = pumpEventsAndWaitForFuture(realDatabaseConnection!!.readSchema())
    val sqliteTable = schema.tables.first()

    val controller = TableController(
      project,
      10,
      view,
      { sqliteTable },
      realDatabaseConnection!!,
      SqliteStatement(selectAllAndRowIdFromTable(sqliteTable)),
      EdtExecutorService.getInstance(),
      EdtExecutorService.getInstance()
    )
    Disposer.register(testRootDisposable, controller)
    pumpEventsAndWaitForFuture(controller.setUp())

    val swingTable = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()
    val tableModel = swingTable.model

    // Act
    tableModel.setValueAt(0, 0, 2)

    // Assert
    val resultSet = pumpEventsAndWaitForFuture(realDatabaseConnection!!.execute(SqliteStatement("SELECT * FROM t1")))
    val rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 10))
    assertSize(1, rows)
    assertEquals(SqliteValue.fromAny(42), rows.first().values[0].value)
    assertEquals(SqliteValue.fromAny(0), rows.first().values[1].value)
  }

  fun testEditTableInsertString() {
    // Prepare
    val customSqliteFile = sqliteUtil.createAdHocSqliteDatabase(
      createStatement = "CREATE TABLE t1 (pk INT PRIMARY KEY, c1 INT)",
      insertStatement = "INSERT INTO t1 (pk, c1) VALUES (42, 1)"
    )
    realDatabaseConnection = pumpEventsAndWaitForFuture(
      getJdbcDatabaseConnection(customSqliteFile, FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()))
    )

    val schema = pumpEventsAndWaitForFuture(realDatabaseConnection!!.readSchema())
    val sqliteTable = schema.tables.first()

    val controller = TableController(
      project,
      10,
      view,
      { sqliteTable },
      realDatabaseConnection!!,
      SqliteStatement(selectAllAndRowIdFromTable(sqliteTable)),
      EdtExecutorService.getInstance(),
      EdtExecutorService.getInstance()
    )
    Disposer.register(testRootDisposable, controller)
    pumpEventsAndWaitForFuture(controller.setUp())

    val swingTable = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()
    val tableModel = swingTable.model

    // Act
    tableModel.setValueAt("foo", 0, 2)

    // Assert
    val resultSet = pumpEventsAndWaitForFuture(realDatabaseConnection!!.execute(SqliteStatement("SELECT * FROM t1")))
    val rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 10))
    assertSize(1, rows)
    assertEquals(SqliteValue.fromAny(42), rows.first().values[0].value)
    assertEquals(SqliteValue.fromAny("foo"), rows.first().values[1].value)
  }

  fun testEditTableInsertNull() {
    // Prepare
    val customSqliteFile = sqliteUtil.createAdHocSqliteDatabase(
      createStatement = "CREATE TABLE t1 (pk INT PRIMARY KEY, c1 INT)",
      insertStatement = "INSERT INTO t1 (pk, c1) VALUES (42, 1)"
    )
    realDatabaseConnection = pumpEventsAndWaitForFuture(
      getJdbcDatabaseConnection(customSqliteFile, FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()))
    )

    val schema = pumpEventsAndWaitForFuture(realDatabaseConnection!!.readSchema())
    val sqliteTable = schema.tables.first()

    val controller = TableController(
      project,
      10,
      view,
      { sqliteTable },
      realDatabaseConnection!!,
      SqliteStatement(selectAllAndRowIdFromTable(sqliteTable)),
      EdtExecutorService.getInstance(),
      EdtExecutorService.getInstance()
    )
    Disposer.register(testRootDisposable, controller)
    pumpEventsAndWaitForFuture(controller.setUp())

    val swingTable = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()
    val tableModel = swingTable.model

    // Act
    tableModel.setValueAt(null, 0, 2)

    // Assert
    val resultSet = pumpEventsAndWaitForFuture(realDatabaseConnection!!.execute(SqliteStatement("SELECT * FROM t1")))
    val rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 10))
    assertSize(1, rows)
    assertEquals(SqliteValue.fromAny(42), rows.first().values[0].value)
    assertEquals(SqliteValue.fromAny(null), rows.first().values[1].value)
  }

  fun testRightClickSelectsCell() {
    // Prepare
    val table = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()

    val col1 = SqliteColumn("col1", SqliteAffinity.INTEGER, false, false)
    val col2 = SqliteColumn("col2", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col1, col2)
    val rows = listOf(
      SqliteRow(listOf(
        SqliteColumnValue("col1", SqliteValue.StringValue("val1")),
        SqliteColumnValue("col2", SqliteValue.StringValue("val2"))
      )),
      SqliteRow(listOf(
        SqliteColumnValue("col1", SqliteValue.StringValue("val3")),
        SqliteColumnValue("col2", SqliteValue.StringValue("val4"))
      ))
    )

    view.startTableLoading()
    view.showTableColumns(cols)
    view.updateRows(rows.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    table.size = Dimension(600, 200)
    table.preferredSize = table.size
    fakeUi = FakeUi(table)

    val rect = table.getCellRect(1, 1, false)

    // Act
    fakeUi.mouse.rightClick(rect.x + rect.width / 2, rect.y + rect.height / 2)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertEquals(1, table.selectedRows.size)
    assertEquals(1, table.selectedColumns.size)
    assertEquals(1, table.selectedRows[0])
    assertEquals(1, table.selectedColumns[0])
  }

  fun testRightClickOnCellOpensMenu() {
    // Prepare
    val table = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()

    val col1 = SqliteColumn("col1", SqliteAffinity.INTEGER, false, false)
    val col2 = SqliteColumn("col2", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col1, col2)
    val rows = listOf(
      SqliteRow(listOf(
        SqliteColumnValue("col1", SqliteValue.StringValue("val1")),
        SqliteColumnValue("col2", SqliteValue.StringValue("val2"))
      )),
      SqliteRow(listOf(
        SqliteColumnValue("col1", SqliteValue.StringValue("val3")),
        SqliteColumnValue("col2", SqliteValue.StringValue("val4"))
      ))
    )

    view.startTableLoading()
    view.showTableColumns(cols)
    view.updateRows(rows.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    table.size = Dimension(600, 200)
    table.preferredSize = table.size
    fakeUi = FakeUi(table)

    val rect = table.getCellRect(1, 1, false)

    // Act
    fakeUi.mouse.rightClick(rect.x + rect.width / 2, rect.y + rect.height / 2)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockActionManager).createActionPopupMenu(any(String::class.java), any(ActionGroup::class.java))
  }

  fun testTableModelIsNotRecreatedIfColumnsAreNotDifferent() {
    // Prepare
    val column = SqliteColumn("name", SqliteAffinity.NUMERIC, false, false)
    val table = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()

    view.startTableLoading()
    view.showTableColumns(listOf(column))
    view.stopTableLoading()

    val tableModel1 = table.model

    // Act
    view.startTableLoading()
    view.showTableColumns(listOf(column))
    view.stopTableLoading()

    // Assert
    assertEquals(tableModel1, table.model)
  }

  fun testTableModelIsRecreatedIfColumnsAreDifferent() {
    // Prepare
    val column1 = SqliteColumn("name1", SqliteAffinity.NUMERIC, false, false)
    val column2 = SqliteColumn("name2", SqliteAffinity.NUMERIC, false, false)
    val table = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()

    view.startTableLoading()
    view.showTableColumns(listOf(column1))
    view.stopTableLoading()

    val tableModel1 = table.model

    // Act
    view.startTableLoading()
    view.showTableColumns(listOf(column2))
    view.stopTableLoading()

    // Assert
    assertTrue(tableModel1 != table.model)
  }

  fun testClickOnUrlCallsListener() {
    // Prepare
    val mockListener = mock(TableView.Listener::class.java)
    view.addListener(mockListener)
    view.startTableLoading()

    val loadingPanel = TreeWalker(view.component).descendants().first { it.name == "loading-panel" } as JEditorPane
    loadingPanel.text = "<a href=\"\">Test url</a>"

    loadingPanel.size = Dimension(10, 10)
    loadingPanel.preferredSize = loadingPanel.size
    val fakeUi = FakeUi(loadingPanel)

    // Act
    fakeUi.mouse.click(5, 5)

    // Assert
    verify(mockListener).cancelRunningStatementInvoked()

    view.stopTableLoading()
  }

  fun testLoadingPanelIsVisibleWhenLoading() {
    // Act
    view.startTableLoading()

    // Assert
    val table = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().firstOrNull()
    val loadingPanel = TreeWalker(view.component).descendants().first { it.name == "loading-panel" }

    assertTrue(loadingPanel.isVisible)
    assertNull(table)

    view.stopTableLoading()
  }

  fun testLoadingPanelIsRemovedWhenLoadingIsFinished() {
    // Act
    view.startTableLoading()
    view.stopTableLoading()

    // Assert
    val table = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()
    val loadingPanel = TreeWalker(view.component).descendants().firstOrNull { it.name == "loading-panel" }

    assertTrue(table.isVisible)
    assertNull(loadingPanel)
  }

  fun testDisposeWhileLoadingDoesntThrow() {
    // Prepare
    val customSqliteFile = sqliteUtil.createAdHocSqliteDatabase(
      createStatement = "CREATE TABLE t1 (pk INT PRIMARY KEY, c1 INT)",
      insertStatement = "INSERT INTO t1 (pk, c1) VALUES (42, 1)"
    )
    realDatabaseConnection = pumpEventsAndWaitForFuture(
      getJdbcDatabaseConnection(customSqliteFile, FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()))
    )

    val controller = TableController(
      project,
      10,
      view,
      { SqliteTable("tab", emptyList(), null, false) },
      realDatabaseConnection!!,
      SqliteStatement("SELECT * FROM t1"),
      EdtExecutorService.getInstance(),
      EdtExecutorService.getInstance()
    )
    Disposer.register(testRootDisposable, controller)
    pumpEventsAndWaitForFuture(controller.setUp())

    view.startTableLoading()

    // Act/Assert
    Disposer.dispose(controller)
  }

  fun testButtonsAreDisabledWhileLoading() {
    // Prepare
    val pageSizeComboBox = TreeWalker(view.component).descendants().filter { it.name == "page-size-combo-box" }.first()
    val refreshButton = TreeWalker(view.component).descendants().filter { it.name == "refresh-button" }.first()

    // Act
    view.startTableLoading()

    // Assert
    assertFalse(pageSizeComboBox.isEnabled)
    assertFalse(refreshButton.isEnabled)

    // Act
    view.stopTableLoading()

    // Assert
    assertTrue(pageSizeComboBox.isEnabled)
    assertTrue(refreshButton.isEnabled)
  }

  private fun getColumnAt(table: JTable, colIndex: Int): List<String?> {
    val values = mutableListOf<String?>()
    for (i in 0 until table.model.rowCount) {
      values.add(table.model.getValueAt(i, colIndex) as? String)
    }

    return values
  }
}