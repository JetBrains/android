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
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.model.FileSqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.mainView.AddColumns
import com.android.tools.idea.sqlite.ui.mainView.AddTable
import com.android.tools.idea.sqlite.ui.mainView.DatabaseInspectorViewImpl
import com.android.tools.idea.sqlite.ui.mainView.IndexedSqliteColumn
import com.android.tools.idea.sqlite.ui.mainView.IndexedSqliteTable
import com.android.tools.idea.sqlite.ui.mainView.RemoveColumns
import com.android.tools.idea.sqlite.ui.mainView.RemoveTable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.ui.treeStructure.Tree
import org.mockito.Mockito.mock
import java.awt.Dimension
import javax.swing.tree.DefaultMutableTreeNode

class DatabaseInspectorViewImplTest : HeavyPlatformTestCase() {
  private lateinit var view: DatabaseInspectorViewImpl

  override fun setUp() {
    super.setUp()
    view = DatabaseInspectorViewImpl(project, testRootDisposable)
    view.component.size = Dimension(600, 200)
  }

  fun testUpdateDatabaseRemovesTableNode() {
    // Prepare
    val tree = TreeWalker(view.component).descendants().filterIsInstance<Tree>().first()

    val database = FileSqliteDatabase("db", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java))
    val column1 = SqliteColumn("c1", SqliteAffinity.TEXT, false, false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, false, false)
    val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
    val schema = SqliteSchema(listOf(table1))
    view.addDatabaseSchema(database, schema, 0)

    // Act
    view.updateDatabaseSchema(database, listOf(RemoveTable(table1.name)))

    // Assert
    assertTreeContainsNodes(tree, mapOf(Pair(database, emptyList())))
  }

  fun testUpdateDatabaseAddsTableNode() {
    // Prepare
    val tree = TreeWalker(view.component).descendants().filterIsInstance<Tree>().first()

    val database = FileSqliteDatabase("db", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java))
    val column1 = SqliteColumn("c1", SqliteAffinity.TEXT, false, false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, false, false)
    val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
    val schema = SqliteSchema(listOf(table1))
    view.addDatabaseSchema(database, schema, 0)

    val tableToAdd = SqliteTable("t2", listOf(column1, column2), null, false)

    // Act
    view.updateDatabaseSchema(
      database,
      listOf(
        AddTable(IndexedSqliteTable(tableToAdd, 1), listOf(IndexedSqliteColumn(column1, 0), IndexedSqliteColumn(column2, 1)))
      )
    )

    // Assert
    assertTreeContainsNodes(tree, mapOf(Pair(database, listOf(table1, tableToAdd))))
  }

  fun testUpdateDatabaseAddsColumn() {
    // Prepare
    val tree = TreeWalker(view.component).descendants().filterIsInstance<Tree>().first()

    val database = FileSqliteDatabase("db", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java))
    val column1 = SqliteColumn("c1", SqliteAffinity.TEXT, false, false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, false, false)
    val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
    val schema = SqliteSchema(listOf(table1))
    view.addDatabaseSchema(database, schema, 0)

    val column3 = SqliteColumn("c3", SqliteAffinity.TEXT, false, false)
    val table2 = SqliteTable("t1", listOf(column1, column2, column3), null, false)

    // Act
    view.updateDatabaseSchema(database, listOf(AddColumns(table2.name, listOf(IndexedSqliteColumn(column3, 2)), table2)))

    // Assert
    assertTreeContainsNodes(tree, mapOf(Pair(database, listOf(table2))))
  }

  fun testUpdateDatabaseRemovesColumn() {
    // Prepare
    val tree = TreeWalker(view.component).descendants().filterIsInstance<Tree>().first()

    val database = FileSqliteDatabase("db", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java))
    val column1 = SqliteColumn("c1", SqliteAffinity.TEXT, false, false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, false, false)
    val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
    val schema = SqliteSchema(listOf(table1))
    view.addDatabaseSchema(database, schema, 0)

    val table2 = SqliteTable("t1", listOf(column1), null, false)

    // Act
    view.updateDatabaseSchema(database, listOf(RemoveColumns(table1.name, listOf(column2), table2)))

    // Assert
    assertTreeContainsNodes(tree, mapOf(Pair(database, listOf(table2))))
  }

  fun testUpdateDatabaseReplacesOldTableForNewTable() {
    // Prepare
    val tree = TreeWalker(view.component).descendants().filterIsInstance<Tree>().first()

    val database = FileSqliteDatabase("db", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java))
    val column1 = SqliteColumn("c1", SqliteAffinity.TEXT, false, false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, false, false)
    val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
    val schema = SqliteSchema(listOf(table1))
    view.addDatabaseSchema(database, schema, 0)

    val newTable = SqliteTable("t2", listOf(column1, column2), null, false)

    // Act
    view.updateDatabaseSchema(
      database,
      listOf(
        RemoveTable(table1.name),
        AddTable(IndexedSqliteTable(newTable, 0), listOf(IndexedSqliteColumn(column1, 0), IndexedSqliteColumn(column2, 1))))
    )

    // Assert
    assertTreeContainsNodes(tree, mapOf(Pair(database, listOf(newTable))))
  }

  fun testUpdateDatabaseReplacesOldColumnForNewColumn() {
    // Prepare
    val tree = TreeWalker(view.component).descendants().filterIsInstance<Tree>().first()

    val database = FileSqliteDatabase("db", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java))
    val column1 = SqliteColumn("c1", SqliteAffinity.TEXT, false, false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, false, false)
    val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
    val schema = SqliteSchema(listOf(table1))
    view.addDatabaseSchema(database, schema, 0)

    val newColumn = SqliteColumn("c3", SqliteAffinity.TEXT, false, false)
    val table1AfterRemove = SqliteTable("t1", listOf(column1), null, false)
    val finalTable = SqliteTable("t1", listOf(column1, newColumn), null, false)

    // Act
    view.updateDatabaseSchema(
      database,
      listOf(
        RemoveColumns(table1.name, listOf(column2), table1AfterRemove),
        AddColumns(finalTable.name, listOf(IndexedSqliteColumn(newColumn, 1)), finalTable)
      )
    )

    // Assert
    assertTreeContainsNodes(tree, mapOf(Pair(database, listOf(finalTable))))
  }

  fun testUpdateDatabaseAddsTableAccordingToIndex() {
    // Prepare
    val tree = TreeWalker(view.component).descendants().filterIsInstance<Tree>().first()

    val database = FileSqliteDatabase("db", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java))
    val column1 = SqliteColumn("c1", SqliteAffinity.TEXT, false, false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, false, false)
    val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
    val schema = SqliteSchema(listOf(table1))
    view.addDatabaseSchema(database, schema, 0)

    val tableToAdd = SqliteTable("t2", listOf(column1, column2), null, false)

    // Act
    view.updateDatabaseSchema(database, listOf(
      AddTable(IndexedSqliteTable(tableToAdd, 0), listOf(IndexedSqliteColumn(column1, 0), IndexedSqliteColumn(column2, 1)))
    ))

    // Assert
    assertTreeContainsNodes(tree, mapOf(Pair(database, listOf(tableToAdd, table1))))
  }

  fun testUpdateDatabaseAddsColumnAccordingToIndex() {
    // Prepare
    val tree = TreeWalker(view.component).descendants().filterIsInstance<Tree>().first()

    val database = FileSqliteDatabase("db", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java))
    val column1 = SqliteColumn("c1", SqliteAffinity.TEXT, false, false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, false, false)
    val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
    val schema = SqliteSchema(listOf(table1))
    view.addDatabaseSchema(database, schema, 0)

    val column3 = SqliteColumn("c3", SqliteAffinity.TEXT, false, false)
    val table2 = SqliteTable("t1", listOf(column3, column1, column2), null, false)

    // Act
    view.updateDatabaseSchema(database, listOf(AddColumns(table2.name, listOf(IndexedSqliteColumn(column3, 0)), table2)))

    // Assert
    assertTreeContainsNodes(tree, mapOf(Pair(database, listOf(table2))))
  }

  fun testEmptyStateIsShownInitially() {
    // Prepare
    val emptyStateRightPanel = TreeWalker(view.component).descendants().first { it.name == "right-panel-empty-state" }
    val closeDatabaseButton = TreeWalker(view.component).descendants().first { it.name == "close-db-button" }
    val syncSchemaButton = TreeWalker(view.component).descendants().first { it.name == "sync-schema-button" }
    val runSqlButton = TreeWalker(view.component).descendants().first { it.name == "run-sql-button" }
    val tree = TreeWalker(view.component).descendants().first { it.name == "left-panel-tree" } as Tree

    // Assert
    assertTrue(emptyStateRightPanel.isVisible)

    assertFalse(closeDatabaseButton.isEnabled)
    assertFalse(syncSchemaButton.isEnabled)
    assertFalse(runSqlButton.isEnabled)

    // tree.emptyText is shown when the root is null
    assertNull(tree.model.root)
  }

  fun testEmptyStateIsHiddenAfterOpeningADatabase() {
    // Prepare
    val tree = TreeWalker(view.component).descendants().first { it.name == "left-panel-tree" } as Tree
    val database = FileSqliteDatabase("name", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java))

    // Act
    view.addDatabaseSchema(database, SqliteSchema(emptyList()), 0)

    // Assert
    val emptyStateRightPanelAfterAddingDb = TreeWalker(view.component).descendants().firstOrNull { it.name == "right-panel-empty-state" }
    val tabsPanelAfterAddingDb = TreeWalker(view.component).descendants().first { it.name == "right-panel-tabs-panel" }
    val closeDatabaseButtonAfterAddingDb = TreeWalker(view.component).descendants().first { it.name == "close-db-button" }
    val syncSchemaButtonAfterAddingDb = TreeWalker(view.component).descendants().first { it.name == "sync-schema-button" }
    val runSqlButtonAfterAddingDb = TreeWalker(view.component).descendants().first { it.name == "run-sql-button" }
    val treeRootAfterAddingDb = tree.model.root

    assertNull(emptyStateRightPanelAfterAddingDb)
    assertNotNull(tabsPanelAfterAddingDb)
    // tree.emptyText is shown when the root is null
    assertNotNull(treeRootAfterAddingDb)
    assertTrue(closeDatabaseButtonAfterAddingDb.isEnabled)
    assertTrue(syncSchemaButtonAfterAddingDb.isEnabled)
    assertTrue(runSqlButtonAfterAddingDb.isEnabled)
  }

  fun testEmptyStateIsShownAfterOpenDatabasesAreRemoved() {
    // Prepare
    val tree = TreeWalker(view.component).descendants().first { it.name == "left-panel-tree" } as Tree
    val database = FileSqliteDatabase("name", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java))

    // Act
    view.addDatabaseSchema(database, SqliteSchema(emptyList()), 0)
    view.removeDatabaseSchema(database)

    // Assert
    val emptyStateRightPanelAfterRemovingDb = TreeWalker(view.component).descendants().first { it.name == "right-panel-empty-state" }
    val tabsPanelAfterRemovingDb = TreeWalker(view.component).descendants().firstOrNull { it.name == "right-panel-tabs-panel" }
    val closeDatabaseButtonAfterRemovingDb = TreeWalker(view.component).descendants().first { it.name == "close-db-button" }
    val syncSchemaButtonAfterRemovingDb = TreeWalker(view.component).descendants().first { it.name == "sync-schema-button" }
    val runSqlButtonAfterRemovingDb = TreeWalker(view.component).descendants().first { it.name == "run-sql-button" }
    val treeRootAfterRemovingDb = tree.model.root

    assertNotNull(emptyStateRightPanelAfterRemovingDb)
    assertNull(tabsPanelAfterRemovingDb)
    // tree.emptyText is shown when the root is null
    assertNull(treeRootAfterRemovingDb)

    assertFalse(closeDatabaseButtonAfterRemovingDb.isEnabled)
    assertFalse(syncSchemaButtonAfterRemovingDb.isEnabled)
    assertFalse(runSqlButtonAfterRemovingDb.isEnabled)
  }

  private fun assertTreeContainsNodes(tree: Tree, databases: Map<SqliteDatabase, List<SqliteTable>>) {
    val root = tree.model.root
    assertEquals(databases.size, tree.model.getChildCount(root))

    databases.keys.forEachIndexed { databaseIndex, database ->
      val databaseNode = tree.model.getChild(root, databaseIndex) as DefaultMutableTreeNode
      assertEquals(database, databaseNode.userObject)
      assertEquals(databases[database]!!.size, tree.model.getChildCount(databaseNode))

      databases[database]!!.forEachIndexed { tableIndex, table ->
        val tableNode = tree.model.getChild(databaseNode, tableIndex) as DefaultMutableTreeNode
        assertEquals(table, tableNode.userObject)
        assertEquals(table.columns.size, tree.model.getChildCount(tableNode))

        table.columns.forEachIndexed { columnIndex, column ->
          val columnNode = tree.model.getChild(tableNode, columnIndex) as DefaultMutableTreeNode
          assertEquals(column, columnNode.userObject)
        }
      }
    }
  }
}