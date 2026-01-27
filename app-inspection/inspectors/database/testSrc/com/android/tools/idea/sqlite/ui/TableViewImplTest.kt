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

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.getDescendant
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.sqlite.controllers.TableController
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.jdbc.selectAllAndRowIdFromTable
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.ResultSetSqliteColumn
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.SqliteValue
import com.android.tools.idea.sqlite.repository.DatabaseRepositoryImpl
import com.android.tools.idea.sqlite.ui.tableView.OrderBy
import com.android.tools.idea.sqlite.ui.tableView.RowDiffOperation
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.android.tools.idea.sqlite.ui.tableView.TableView.TableViewType.EVALUATOR
import com.android.tools.idea.sqlite.ui.tableView.TableView.TableViewType.TABLE
import com.android.tools.idea.sqlite.ui.tableView.TableViewImpl
import com.android.tools.idea.sqlite.ui.tableView.TableViewImpl.CopyToClipboardAction
import com.android.tools.idea.sqlite.ui.tableView.TableViewImpl.RemoveRowsAction
import com.android.tools.idea.sqlite.ui.tableView.TableViewImpl.SetNullAction
import com.android.tools.idea.sqlite.ui.tableView.ViewColumn
import com.android.tools.idea.sqlite.utils.SqliteTestUtil
import com.android.tools.idea.sqlite.utils.getJdbcDatabaseConnection
import com.android.tools.idea.sqlite.utils.toViewColumn
import com.android.tools.idea.sqlite.utils.toViewColumns
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.runDispatching
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.concurrency.EdtExecutorService
import java.awt.Dimension
import java.awt.Point
import java.awt.datatransfer.DataFlavor.stringFlavor
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JProgressBar
import javax.swing.JTable
import javax.swing.table.TableModel
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

private const val COLUMN_DEFAULT_WIDTH = 75
private const val AUTORESIZE_OFF_COLUMN_PREFERRED_WIDTH = 85

class TableViewImplTest : BasePlatformTestCase() {
  private lateinit var view: TableViewImpl
  private lateinit var fakeUi: FakeUi
  private lateinit var mockActionManager: ActionManager

  private lateinit var sqliteUtil: SqliteTestUtil
  private var realDatabaseConnection: DatabaseConnection? = null

  override fun setUp() {
    super.setUp()

    val mockPopUpMenu = mock(ActionPopupMenu::class.java)
    whenever(mockPopUpMenu.component).thenReturn(mock(JPopupMenu::class.java))
    mockActionManager = mock(ActionManager::class.java)
    whenever(mockActionManager.createActionPopupMenu(any(), any())).thenReturn(mockPopUpMenu)

    IdeComponents(myFixture).replaceApplicationService(ActionManager::class.java, mockActionManager)

    view = TableViewImpl(TABLE)
    val component: JPanel = view.component as JPanel
    component.size = Dimension(600, 200)

    fakeUi = FakeUi(component)

    sqliteUtil =
      SqliteTestUtil(IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
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
    val column = ResultSetSqliteColumn("name", SqliteAffinity.NUMERIC, false, false)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()
    val jbScrollPane = TreeWalker(table).ancestors().filterIsInstance<JBScrollPane>().first()

    // Act
    view.showTableColumns(listOf(column).toViewColumns())
    fakeUi.layout()

    // Assert
    assertThat(table.autoResizeMode).isEqualTo(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS)

    assertThat(table.size.width).isEqualTo(600)
    assertThat(table.columnModel.getColumn(0).width).isEqualTo(60)
    assertThat(table.columnModel.getColumn(1).width).isEqualTo(540)

    assertThat(jbScrollPane.horizontalScrollBar.model.minimum).isEqualTo(0)
    assertThat(jbScrollPane.horizontalScrollBar.model.maximum).isEqualTo(600)
  }

  fun testTableIsScrollableIfTooManyColumns() {
    // Prepare
    val treeWalker = TreeWalker(view.component)

    val column = ResultSetSqliteColumn("name", SqliteAffinity.NUMERIC, false, false)
    val columns = mutableListOf<ResultSetSqliteColumn>()
    repeat(600 / COLUMN_DEFAULT_WIDTH) { columns.add(column) }

    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()
    val jbScrollPane = TreeWalker(table).ancestors().filterIsInstance<JBScrollPane>().first()

    // Act
    view.showTableColumns(columns.toViewColumns())
    fakeUi.layout()

    // Assert
    assertThat(table.autoResizeMode).isEqualTo(JTable.AUTO_RESIZE_OFF)

    assertThat(table.size.width).isGreaterThan(598)
    assertThat(table.columnModel.getColumn(1).width)
      .isEqualTo(AUTORESIZE_OFF_COLUMN_PREFERRED_WIDTH)

    assertThat(jbScrollPane.horizontalScrollBar.model.minimum).isEqualTo(0)
    assertThat(jbScrollPane.horizontalScrollBar.model.maximum).isGreaterThan(598)
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
    assertThat(readOnlyLabel.isVisible).isFalse()
    assertThat(table.model.isCellEditable(0, 1)).isTrue()
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
    assertThat(readOnlyLabel.isVisible).isTrue()
    assertThat(table.model.isCellEditable(0, 0)).isFalse()
  }

  fun testClickOnColumnHeaderSortsTable() {
    // Prepare
    val treeWalker = TreeWalker(view.component)

    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val mockListener = mock(TableView.Listener::class.java)
    view.addListener(mockListener)

    val col = ResultSetSqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rows =
      listOf(SqliteRow(listOf(SqliteColumnValue(col.name, SqliteValue.StringValue("val")))))

    view.startTableLoading()
    view.showTableColumns(cols.toViewColumns())
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
    assertThat(table.columnAtPoint(Point(597, 0))).isEqualTo(1)
    verify(mockListener).toggleOrderByColumnInvoked(col.toViewColumn())
  }

  fun testClickOnFirstColumnHeaderDoesNotSortTable() {
    // Prepare
    val treeWalker = TreeWalker(view.component)

    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val mockListener = mock(TableView.Listener::class.java)
    view.addListener(mockListener)

    val col = ResultSetSqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rows = listOf(SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val")))))

    view.startTableLoading()
    view.showTableColumns(cols.toViewColumns())
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
    assertThat(table.columnAtPoint(Point(0, 0))).isEqualTo(0)
    verify(mockListener, times(0)).toggleOrderByColumnInvoked(col.toViewColumn())
  }

  fun testColumnsAreResizableExceptForFirstColumn() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val col = ResultSetSqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rows = listOf(SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val")))))

    view.startTableLoading()
    view.showTableColumns(cols.toViewColumns())
    view.updateRows(rows.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    // Assert
    assertThat(table.columnModel.getColumn(0).resizable).isFalse()
    assertThat(table.columnModel.getColumn(1).resizable).isTrue()
  }

  fun testColumnsAreNamedCorrectly() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val col = ResultSetSqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rows = listOf(SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val")))))

    view.startTableLoading()
    view.showTableColumns(cols.toViewColumns())
    view.updateRows(rows.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    // Assert
    assertThat(table.model.getColumnName(0)).isEqualTo("")
    assertThat(table.model.getColumnName(1)).isEqualTo("col")
  }

  fun testRowsHaveExpectedValues() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val col = ResultSetSqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rows =
      listOf(
        SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val1")))),
        SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val2")))),
      )

    view.startTableLoading()
    view.showTableColumns(cols.toViewColumns())
    view.updateRows(rows.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    // Assert
    assertThat(getColumnAt(table, 0)).containsExactly("1", "2").inOrder()
    assertThat(getColumnAt(table, 1)).containsExactly("val1", "val2").inOrder()
  }

  fun testSetValueInColumnsOtherThanFirstIsAllowed() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val mockListener = mock(TableView.Listener::class.java)
    view.addListener(mockListener)

    val col = ResultSetSqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val row = SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val1"))))
    val rows = listOf(row)

    view.startTableLoading()
    view.showTableColumns(cols.toViewColumns())
    view.updateRows(rows.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    // Act
    table.model.setValueAt("newValue", 0, 1)

    // Assert
    verify(mockListener)
      .updateCellInvoked(0, col.toViewColumn(), SqliteValue.StringValue("newValue"))
  }

  fun testColumnsAreEditableExceptForFirst() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val col = ResultSetSqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rows = listOf(SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val1")))))

    view.startTableLoading()
    view.showTableColumns(cols.toViewColumns())
    view.updateRows(rows.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()
    view.setEditable(true)

    // Assert
    assertThat(table.model.isCellEditable(0, 0)).isFalse()
    assertThat(table.model.isCellEditable(0, 1)).isTrue()
  }

  fun `testShowRows Add`() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val col = ResultSetSqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rows = listOf(SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val1")))))

    // Act
    view.startTableLoading()
    view.showTableColumns(cols.toViewColumns())
    view.updateRows(rows.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    // Assert
    assertThat(table.model.rowCount).isEqualTo(1)
    assertThat(table.model.getValueAt(0, 1)).isEqualTo("val1")
  }

  fun `testShowRows Add UpdateRemove`() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val col = ResultSetSqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rowsToAdd =
      listOf(
        SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val1")))),
        SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val2")))),
      )

    // Act
    view.startTableLoading()
    view.showTableColumns(cols.toViewColumns())
    view.updateRows(rowsToAdd.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    view.updateRows(
      listOf(
        RowDiffOperation.UpdateCell(
          SqliteColumnValue("col", SqliteValue.StringValue("new val")),
          0,
          0,
        ),
        RowDiffOperation.RemoveLastRows(1),
      )
    )

    // Assert
    assertThat(table.model.rowCount).isEqualTo(1)
    assertThat(table.model.getValueAt(0, 1)).isEqualTo("new val")
  }

  fun `testShowRows Add Update UpdateAdd`() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val col = ResultSetSqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rowsToAdd =
      listOf(
        SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val1")))),
        SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val2")))),
      )

    // Act
    view.startTableLoading()
    view.showTableColumns(cols.toViewColumns())
    view.updateRows(rowsToAdd.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    view.updateRows(
      listOf(
        RowDiffOperation.UpdateCell(
          SqliteColumnValue("col", SqliteValue.StringValue("new val")),
          0,
          0,
        )
      )
    )

    view.updateRows(
      listOf(
        RowDiffOperation.UpdateCell(
          SqliteColumnValue("col", SqliteValue.StringValue("new val1")),
          0,
          0,
        ),
        RowDiffOperation.AddRow(
          SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("new val3"))))
        ),
        RowDiffOperation.AddRow(
          SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("new val4"))))
        ),
      )
    )

    // Assert
    assertThat(table.model.rowCount).isEqualTo(4)
    assertThat(getColumnAt(table, 1))
      .containsExactly("new val1", "val2", "new val3", "new val4")
      .inOrder()
  }

  fun testEditTableUsingPrimaryKey() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        createStatement = "CREATE TABLE t1 (pk INT PRIMARY KEY, c1 INT)",
        insertStatement = "INSERT INTO t1 (pk, c1) VALUES (42, 1)",
      )
    realDatabaseConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )
    val databaseRepository = DatabaseRepositoryImpl(project, EdtExecutorService.getInstance())
    val databaseId = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(customSqliteFile))
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection!!)
    }

    val schema = pumpEventsAndWaitForFuture(realDatabaseConnection!!.readSchema())
    val sqliteTable = schema.tables.first()

    val controller =
      TableController(
        project,
        10,
        view,
        databaseId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, selectAllAndRowIdFromTable(sqliteTable)),
        {},
        {},
        EdtExecutorService.getInstance(),
        EdtExecutorService.getInstance(),
      )
    Disposer.register(testRootDisposable, controller)
    pumpEventsAndWaitForFuture(controller.setUp())

    val swingTable = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()
    val tableModel = swingTable.model

    // Act
    tableModel.setValueAt(0, 0, 2)

    // Assert
    val resultSet =
      pumpEventsAndWaitForFuture(
        realDatabaseConnection!!.query(
          SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1")
        )
      )
    val rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 10)).rows
    assertThat(rows).hasSize(1)
    assertThat(rows.first().values[0].value).isEqualTo(SqliteValue.fromAny(42))
    assertThat(rows.first().values[1].value).isEqualTo(SqliteValue.fromAny(0))
  }

  fun testEditTableUsingRowId() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        createStatement = "CREATE TABLE t1 (c1 INT, c2 INT)",
        insertStatement = "INSERT INTO t1 (c1, c2) VALUES (42, 1)",
      )
    realDatabaseConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )
    val databaseRepository = DatabaseRepositoryImpl(project, EdtExecutorService.getInstance())
    val databaseId = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(customSqliteFile))
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection!!)
    }

    val schema = pumpEventsAndWaitForFuture(realDatabaseConnection!!.readSchema())
    val sqliteTable = schema.tables.first()

    val controller =
      TableController(
        project,
        10,
        view,
        databaseId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, selectAllAndRowIdFromTable(sqliteTable)),
        {},
        {},
        EdtExecutorService.getInstance(),
        EdtExecutorService.getInstance(),
      )
    Disposer.register(testRootDisposable, controller)
    pumpEventsAndWaitForFuture(controller.setUp())

    val swingTable = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()
    val tableModel = swingTable.model

    // Act
    tableModel.setValueAt(0, 0, 2)

    // Assert
    val resultSet =
      pumpEventsAndWaitForFuture(
        realDatabaseConnection!!.query(
          SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1")
        )
      )
    val rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 10)).rows
    assertThat(rows).hasSize(1)
    assertThat(rows.first().values[0].value).isEqualTo(SqliteValue.fromAny(42))
    assertThat(rows.first().values[1].value).isEqualTo(SqliteValue.fromAny(0))
  }

  fun testEditTableInsertString() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        createStatement = "CREATE TABLE t1 (pk INT PRIMARY KEY, c1 INT)",
        insertStatement = "INSERT INTO t1 (pk, c1) VALUES (42, 1)",
      )
    realDatabaseConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )
    val databaseRepository = DatabaseRepositoryImpl(project, EdtExecutorService.getInstance())
    val databaseId = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(customSqliteFile))
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection!!)
    }

    val schema = pumpEventsAndWaitForFuture(realDatabaseConnection!!.readSchema())
    val sqliteTable = schema.tables.first()

    val controller =
      TableController(
        project,
        10,
        view,
        databaseId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, selectAllAndRowIdFromTable(sqliteTable)),
        {},
        {},
        EdtExecutorService.getInstance(),
        EdtExecutorService.getInstance(),
      )
    Disposer.register(testRootDisposable, controller)
    pumpEventsAndWaitForFuture(controller.setUp())

    val swingTable = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()
    val tableModel = swingTable.model

    // Act
    tableModel.setValueAt("foo", 0, 2)

    // Assert
    val resultSet =
      pumpEventsAndWaitForFuture(
        realDatabaseConnection!!.query(
          SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1")
        )
      )
    val rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 10)).rows
    assertThat(rows).hasSize(1)
    assertThat(rows.first().values[0].value).isEqualTo(SqliteValue.fromAny(42))
    assertThat(rows.first().values[1].value).isEqualTo(SqliteValue.fromAny("foo"))
  }

  fun testEditTableInsertNull() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        createStatement = "CREATE TABLE t1 (pk INT PRIMARY KEY, c1 INT)",
        insertStatement = "INSERT INTO t1 (pk, c1) VALUES (42, 1)",
      )
    realDatabaseConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )
    val databaseRepository = DatabaseRepositoryImpl(project, EdtExecutorService.getInstance())
    val databaseId = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(customSqliteFile))
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection!!)
    }

    val schema = pumpEventsAndWaitForFuture(realDatabaseConnection!!.readSchema())
    val sqliteTable = schema.tables.first()

    val controller =
      TableController(
        project,
        10,
        view,
        databaseId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, selectAllAndRowIdFromTable(sqliteTable)),
        {},
        {},
        EdtExecutorService.getInstance(),
        EdtExecutorService.getInstance(),
      )
    Disposer.register(testRootDisposable, controller)
    pumpEventsAndWaitForFuture(controller.setUp())

    val swingTable = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()
    val tableModel = swingTable.model

    // Act
    tableModel.setValueAt(null, 0, 2)

    // Assert
    val resultSet =
      pumpEventsAndWaitForFuture(
        realDatabaseConnection!!.query(
          SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1")
        )
      )
    val rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 10)).rows
    assertThat(rows).hasSize(1)
    assertThat(rows.first().values[0].value).isEqualTo(SqliteValue.fromAny(42))
    assertThat(rows.first().values[1].value).isEqualTo(SqliteValue.fromAny(null))
  }

  fun testRightClickSelectsCell() {
    // Prepare
    val table = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()

    val col1 = ResultSetSqliteColumn("col1", SqliteAffinity.INTEGER, false, false)
    val col2 = ResultSetSqliteColumn("col2", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col1, col2)
    val rows =
      listOf(
        SqliteRow(
          listOf(
            SqliteColumnValue("col1", SqliteValue.StringValue("val1")),
            SqliteColumnValue("col2", SqliteValue.StringValue("val2")),
          )
        ),
        SqliteRow(
          listOf(
            SqliteColumnValue("col1", SqliteValue.StringValue("val3")),
            SqliteColumnValue("col2", SqliteValue.StringValue("val4")),
          )
        ),
      )

    view.startTableLoading()
    view.showTableColumns(cols.toViewColumns())
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
    assertThat(table.selectedRows.size).isEqualTo(1)
    assertThat(table.selectedColumns.size).isEqualTo(1)
    assertThat(table.selectedRows[0]).isEqualTo(1)
    assertThat(table.selectedColumns[0]).isEqualTo(1)
  }

  fun testRightClickOnCellOpensMenu_table() {
    // Prepare
    view = TableViewImpl(TABLE)
    val table = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()

    val col1 = ResultSetSqliteColumn("col1", SqliteAffinity.INTEGER, false, false)
    val col2 = ResultSetSqliteColumn("col2", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col1, col2)
    val rows =
      listOf(
        SqliteRow(
          listOf(
            SqliteColumnValue("col1", SqliteValue.StringValue("val1")),
            SqliteColumnValue("col2", SqliteValue.StringValue("val2")),
          )
        ),
        SqliteRow(
          listOf(
            SqliteColumnValue("col1", SqliteValue.StringValue("val3")),
            SqliteColumnValue("col2", SqliteValue.StringValue("val4")),
          )
        ),
      )

    view.startTableLoading()
    view.showTableColumns(cols.toViewColumns())
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
    val captor: ArgumentCaptor<ActionGroup> = ArgumentCaptor.forClass(ActionGroup::class.java)
    verify(mockActionManager).createActionPopupMenu(any(), captor.capture())
    val actions = (captor.value as DefaultActionGroup).getChildren(mockActionManager)

    assertThat(actions.map { it.javaClass.simpleName })
      .containsExactly("CopyToClipboardAction", "RemoveRowsAction", "SetNullAction")
      .inOrder()
  }

  fun testRightClickOnCellOpensMenu_evaluator() {
    // Prepare
    view = TableViewImpl(EVALUATOR)
    val table = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()

    val col1 = ResultSetSqliteColumn("col1", SqliteAffinity.INTEGER, false, false)
    val col2 = ResultSetSqliteColumn("col2", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col1, col2)
    val rows =
      listOf(
        SqliteRow(
          listOf(
            SqliteColumnValue("col1", SqliteValue.StringValue("val1")),
            SqliteColumnValue("col2", SqliteValue.StringValue("val2")),
          )
        ),
        SqliteRow(
          listOf(
            SqliteColumnValue("col1", SqliteValue.StringValue("val3")),
            SqliteColumnValue("col2", SqliteValue.StringValue("val4")),
          )
        ),
      )

    view.startTableLoading()
    view.showTableColumns(cols.toViewColumns())
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
    val captor: ArgumentCaptor<ActionGroup> = ArgumentCaptor.forClass(ActionGroup::class.java)
    verify(mockActionManager).createActionPopupMenu(any(), captor.capture())
    val actions = (captor.value as DefaultActionGroup).getChildren(mockActionManager)

    assertThat(actions.map { it.javaClass.simpleName })
      .containsExactly("CopyToClipboardAction", "SetNullAction")
      .inOrder()
  }

  fun testRightClickOutsideOfTableRows() {
    // Prepare
    val table = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()

    val col1 = ResultSetSqliteColumn("col1", SqliteAffinity.INTEGER, false, false)
    val col2 = ResultSetSqliteColumn("col2", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col1, col2)
    val rows =
      listOf(
        SqliteRow(
          listOf(
            SqliteColumnValue("col1", SqliteValue.StringValue("val1")),
            SqliteColumnValue("col2", SqliteValue.StringValue("val2")),
          )
        ),
        SqliteRow(
          listOf(
            SqliteColumnValue("col1", SqliteValue.StringValue("val3")),
            SqliteColumnValue("col2", SqliteValue.StringValue("val4")),
          )
        ),
      )

    view.startTableLoading()
    view.showTableColumns(cols.toViewColumns())
    view.updateRows(rows.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    table.size = Dimension(600, 200)
    table.preferredSize = table.size
    fakeUi = FakeUi(table)

    val rect = table.getCellRect(1, 1, false)

    // Act
    // click outside of last row
    fakeUi.mouse.rightClick(rect.x + rect.width / 2, (rect.y + rect.height / 2) * 2)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // we should reach this point without exceptions being thrown after clicking
  }

  fun testTableModelIsNotRecreatedIfColumnsAreNotDifferent() {
    // Prepare
    val column = ResultSetSqliteColumn("name", SqliteAffinity.NUMERIC, false, false)
    val table = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()

    view.startTableLoading()
    view.showTableColumns(listOf(column).toViewColumns())
    view.stopTableLoading()

    val tableModel1 = table.model

    // Act
    view.startTableLoading()
    view.showTableColumns(listOf(column).toViewColumns())
    view.stopTableLoading()

    // Assert
    assertThat(table.model).isEqualTo(tableModel1)
  }

  fun testTableModelIsRecreatedIfColumnsAreDifferent() {
    // Prepare
    val column1 = ResultSetSqliteColumn("name1", SqliteAffinity.NUMERIC, false, false)
    val column2 = ResultSetSqliteColumn("name2", SqliteAffinity.NUMERIC, false, false)
    val table = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()

    view.startTableLoading()
    view.showTableColumns(listOf(column1).toViewColumns())
    view.stopTableLoading()

    val tableModel1 = table.model

    // Act
    view.startTableLoading()
    view.showTableColumns(listOf(column2).toViewColumns())
    view.stopTableLoading()

    // Assert
    assertThat(table.model).isNotEqualTo(tableModel1)
  }

  fun testProgressBarIsHiddenByDefault() {
    val progressBar =
      TreeWalker(view.component).descendants().filterIsInstance<JProgressBar>().first()
    assertThat(progressBar.isVisible).isFalse()
  }

  fun testProgressBarIsVisibleWhenLoading() {
    // Act
    view.startTableLoading()

    // Assert
    val table = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()
    val progressBar =
      TreeWalker(view.component).descendants().filterIsInstance<JProgressBar>().first()

    assertThat(table.emptyText.text).isEqualTo("Waiting for data...")
    assertThat(table.isVisible).isTrue()
    assertThat(table.isEnabled).isFalse()
    assertThat(progressBar.isVisible).isTrue()

    view.stopTableLoading()
  }

  fun testProgressBarIsHiddenWhenLoadingIsFinished() {
    // Act
    view.startTableLoading()
    view.stopTableLoading()

    // Assert
    val table = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()
    val progressBar =
      TreeWalker(view.component).descendants().filterIsInstance<JProgressBar>().first()

    assertThat(table.emptyText.text).isEqualTo("Table is empty")
    assertThat(table.isVisible).isTrue()
    assertThat(table.isEnabled).isTrue()
    assertThat(progressBar.isVisible).isFalse()
  }

  fun testDisposeWhileLoadingDoesntThrow() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        createStatement = "CREATE TABLE t1 (pk INT PRIMARY KEY, c1 INT)",
        insertStatement = "INSERT INTO t1 (pk, c1) VALUES (42, 1)",
      )
    realDatabaseConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )
    val databaseRepository = DatabaseRepositoryImpl(project, EdtExecutorService.getInstance())
    val databaseId = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(customSqliteFile))
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId, realDatabaseConnection!!)
    }

    val controller =
      TableController(
        project,
        10,
        view,
        databaseId,
        { SqliteTable("tab", emptyList(), null, false) },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"),
        {},
        {},
        EdtExecutorService.getInstance(),
        EdtExecutorService.getInstance(),
      )
    Disposer.register(testRootDisposable, controller)
    pumpEventsAndWaitForFuture(controller.setUp())

    view.startTableLoading()

    // Act/Assert
    Disposer.dispose(controller)
  }

  fun testButtonsAreDisabledBeforeAndWhileLoading() {
    // Prepare
    val pageSizeComboBox =
      TreeWalker(view.component).descendants().first { it.name == "page-size-combo-box" }
    val refreshButton =
      TreeWalker(view.component).descendants().first { it.name == "refresh-button" }
    val liveUpdatesCheckBox =
      TreeWalker(view.component).descendants().first { it.name == "live-updates-checkbox" }

    // Assert
    assertThat(pageSizeComboBox.isEnabled).isFalse()
    assertThat(refreshButton.isEnabled).isFalse()
    assertThat(liveUpdatesCheckBox.isEnabled).isFalse()

    // Act
    view.startTableLoading()

    // Assert
    assertThat(pageSizeComboBox.isEnabled).isFalse()
    assertThat(refreshButton.isEnabled).isFalse()
    assertThat(liveUpdatesCheckBox.isEnabled).isFalse()

    // Act
    view.stopTableLoading()

    // Assert
    assertThat(pageSizeComboBox.isEnabled).isTrue()
    assertThat(refreshButton.isEnabled).isTrue()
    assertThat(liveUpdatesCheckBox.isEnabled).isTrue()
  }

  fun testDoesntSetValueIfSameValue() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val mockListener = mock(TableView.Listener::class.java)
    view.addListener(mockListener)

    val col = ResultSetSqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val row = SqliteRow(listOf(SqliteColumnValue("col", SqliteValue.StringValue("val1"))))
    val rows = listOf(row)

    view.startTableLoading()
    view.showTableColumns(cols.toViewColumns())
    view.updateRows(rows.map { RowDiffOperation.AddRow(it) })
    view.stopTableLoading()

    // Act
    table.model.setValueAt("val1", 0, 1)

    // Assert
    verify(mockListener, times(0))
      .updateCellInvoked(0, col.toViewColumn(), SqliteValue.StringValue("val1"))
  }

  fun testRevertLastTableCellEdit() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    view.showTableColumns(listOf(ViewColumn("c1", false, false)))
    view.updateRows(
      listOf(
        RowDiffOperation.AddRow(
          SqliteRow(listOf(SqliteColumnValue("c1", SqliteValue.fromAny("value"))))
        )
      )
    )
    view.setEditable(true)

    // Act
    table.model.setValueAt("new value", 0, 1)

    // Assert
    assertThat(table.model.getValueAt(0, 1)).isEqualTo("new value")

    // Act
    view.revertLastTableCellEdit()

    // Assert
    assertThat(table.model.getValueAt(0, 1)).isEqualTo("value")
  }

  fun testNoColumnsAreShownAfterResetView() {
    val table = TreeWalker(view.component).descendants().filterIsInstance<JBTable>().first()
    val tableModel = table.model

    assertThat(tableModel.columnCount).isEqualTo(0)

    view.resetView()

    assertThat(tableModel.columnCount).isEqualTo(0)
  }

  fun testEditNullCellToEmptyStringDoesNothing() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    view.showTableColumns(listOf(ViewColumn("c1", false, false)))
    view.updateRows(
      listOf(
        RowDiffOperation.AddRow(
          SqliteRow(listOf(SqliteColumnValue("c1", SqliteValue.fromAny("value"))))
        )
      )
    )
    view.setEditable(true)

    // Act
    table.model.setValueAt(null, 0, 1)

    // Assert
    assertThat(table.model.getValueAt(0, 1)).isNull()

    // Act
    table.model.setValueAt("", 0, 1)

    // Assert
    assertThat(table.model.getValueAt(0, 1)).isNull()
  }

  fun testDisabledLiveUpdates() {
    // Prepare
    val liveUpdatesCheckBox =
      TreeWalker(view.component).descendants().first { it.name == "live-updates-checkbox" }

    // Assert
    assertThat(liveUpdatesCheckBox.isEnabled).isFalse()

    // Act
    view.setLiveUpdatesButtonState(false)
    view.startTableLoading()

    // Assert
    assertThat(liveUpdatesCheckBox.isEnabled).isFalse()

    // Act
    view.stopTableLoading()

    // Assert
    assertThat(liveUpdatesCheckBox.isEnabled).isFalse()
  }

  fun testNoSortingAfterResetView() {
    view.setColumnSortIndicator(OrderBy.Asc("col"))

    assertThat(view.orderBy).isEqualTo(OrderBy.Asc("col"))

    view.resetView()

    assertThat(view.orderBy).isEqualTo(OrderBy.NotOrdered)
  }

  private fun getColumnAt(table: JTable, colIndex: Int): List<String?> {
    val values = mutableListOf<String?>()
    for (i in 0 until table.model.rowCount) {
      values.add(table.model.getValueAt(i, colIndex) as? String)
    }

    return values
  }

  fun testRemoveRowAction() {
    view.prepare()
    val table = view.component.getDescendant<JTable>()
    table.selectionModel.setSelectionInterval(0, 1)
    val mockListener = mock<TableView.Listener>()
    view.addListener(mockListener)

    RemoveRowsAction(table).actionPerformed(TestActionEvent.createTestEvent())

    verify(mockListener).removeRowsInvoked(listOf(0, 1))
  }

  fun testRemoveRowAction_update_oneRow() {
    view.prepare()
    val table = view.component.getDescendant<JTable>()
    table.selectionModel.setSelectionInterval(0, 0)
    val mockListener = mock<TableView.Listener>()
    view.addListener(mockListener)
    view.setEditable(true)
    val event = TestActionEvent.createTestEvent()

    RemoveRowsAction(table).update(event)

    assertEquals("Remove Row", event.presentation.text)
    assertTrue(event.presentation.isVisible)
    assertTrue(event.presentation.isEnabled)
  }

  fun testRemoveRowAction_update_multipleRows() {
    view.prepare()
    val table = view.component.getDescendant<JTable>()
    table.selectionModel.setSelectionInterval(0, 1)
    val mockListener = mock<TableView.Listener>()
    view.addListener(mockListener)
    view.setEditable(true)
    val event = TestActionEvent.createTestEvent()

    RemoveRowsAction(table).update(event)

    assertThat(event.presentation.text).isEqualTo("Remove 2 Rows")
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()
  }

  fun testRemoveRowAction_update_readOnly() {
    view.prepare()
    val table = view.component.getDescendant<JTable>()
    table.selectionModel.setSelectionInterval(0, 0)
    val mockListener = mock<TableView.Listener>()
    view.addListener(mockListener)
    view.setEditable(false)
    val event = TestActionEvent.createTestEvent()

    RemoveRowsAction(table).update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  fun testCopyToClipboardAction_singleRow() {
    val copyPasteManager = CopyPasteManager.getInstance()
    view.prepare(
      TableData(
        listOf("col1", "col2"),
        listOf(listOf("val-1-1", "val-1-2"), listOf("val-2-1", "val-2-2")),
      )
    )
    val table = view.component.getDescendant<JTable>()
    table.selectionModel.setSelectionInterval(1, 1)
    table.addColumnSelectionInterval(1, 1)

    CopyToClipboardAction(table).actionPerformed(TestActionEvent.createTestEvent())

    assertThat(copyPasteManager.getContents<String>(stringFlavor)).isEqualTo("val-2-1")
  }

  fun testCopyToClipboardAction_multipleRows() {
    val copyPasteManager = CopyPasteManager.getInstance()
    view.prepare(
      TableData(
        listOf("col1", "col2"),
        listOf(
          listOf("val-1-1", "val-1-2"),
          listOf("val-2-1", "val-2-2"),
          listOf("val-3-1", "val-3-2"),
          ),
      )
    )
    val table = view.component.getDescendant<JTable>()
    table.selectionModel.addSelectionInterval(0, 0)
    table.selectionModel.addSelectionInterval(2, 2)
    table.addColumnSelectionInterval(2, 2)

    CopyToClipboardAction(table).actionPerformed(TestActionEvent.createTestEvent())

    assertThat(copyPasteManager.getContents<String>(stringFlavor)).isEqualTo("val-1-2,val-3-2")
  }

  fun testSetNullAction_singleRow() {
    view.prepare(
      TableData(
        listOf("col1", "col2"),
        listOf(
          listOf("val-1-1", "val-1-2"),
          listOf("val-2-1", "val-2-2"),
        ),
      )
    )
    val table = view.component.getDescendant<JTable>()
    table.selectionModel.addSelectionInterval(0, 0)
    table.addColumnSelectionInterval(1, 1)
    assertThat(table.model.getColumnValues(1)).containsExactly("val-1-1", "val-2-1")

    SetNullAction(table).actionPerformed(TestActionEvent.createTestEvent())

    assertThat(table.model.getColumnValues(1)).containsExactly(null, "val-2-1")
  }

  fun testSetNullAction_multipleRows() {
    view.prepare(
      TableData(
        listOf("col1", "col2"),
        listOf(
          listOf("val-1-1", "val-1-2"),
          listOf("val-2-1", "val-2-2"),
          listOf("val-3-1", "val-3-2"),
        ),
      )
    )
    val table = view.component.getDescendant<JTable>()
    table.selectionModel.addSelectionInterval(0, 0)
    table.selectionModel.addSelectionInterval(2, 2)
    table.addColumnSelectionInterval(2, 2)
    assertThat(table.model.getColumnValues(2)).containsExactly("val-1-2", "val-2-2", "val-3-2")

    SetNullAction(table).actionPerformed(TestActionEvent.createTestEvent())

    assertThat(table.model.getColumnValues(2)).containsExactly(null, "val-2-2", null)
  }
}

private fun TableModel.getColumnValues(column: Int): List<Any?> {
  return buildList {
    repeat(rowCount) {
      add(getValueAt(it, column))
    }
  }
}

private data class TableData(val columnNames: List<String>, val values: List<List<String>>)

private fun TableViewImpl.prepare(
  data: TableData =
    TableData(
      listOf("col1", "col2"),
      listOf(listOf("val-1-1", "val-1-2"), listOf("val-2-1", "val-2-2")),
    )
) {
  assert(data.values.all { it.size == data.columnNames.size })

  val columns =
    data.columnNames.map { ResultSetSqliteColumn(it, SqliteAffinity.TEXT, true, false) }
  showTableColumns(columns.toViewColumns())
  val rows =
    data.values.map {
      SqliteRow(
        it.zip(data.columnNames) { value, columnName ->
          SqliteColumnValue(columnName, SqliteValue.StringValue(value))
        }
      )
    }
  updateRows(rows.map { RowDiffOperation.AddRow(it) })
}
