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

import com.android.tools.adtui.swing.findDescendant
import com.android.tools.adtui.swing.getDescendant
import com.android.tools.idea.sqlite.controllers.TabId
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.mainView.AddColumns
import com.android.tools.idea.sqlite.ui.mainView.AddTable
import com.android.tools.idea.sqlite.ui.mainView.DatabaseDiffOperation
import com.android.tools.idea.sqlite.ui.mainView.DatabaseInspectorViewImpl
import com.android.tools.idea.sqlite.ui.mainView.IndexedSqliteColumn
import com.android.tools.idea.sqlite.ui.mainView.IndexedSqliteTable
import com.android.tools.idea.sqlite.ui.mainView.RemoveColumns
import com.android.tools.idea.sqlite.ui.mainView.RemoveTable
import com.android.tools.idea.sqlite.ui.mainView.ViewDatabase
import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockVirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.ui.treeStructure.Tree
import icons.StudioIcons
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class DatabaseInspectorViewImplTest : HeavyPlatformTestCase() {
  private lateinit var view: DatabaseInspectorViewImpl
  private lateinit var databaseId: SqliteDatabaseId

  override fun setUp() {
    super.setUp()
    view = DatabaseInspectorViewImpl(project, testRootDisposable)
    view.component.size = Dimension(600, 200)
    databaseId = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("name")))
  }

  fun testUpdateDatabaseRemovesTableNode() {
    // Prepare
    val tree = view.component.getDescendant<Tree>()

    val column1 = SqliteColumn("c1", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
    val schema = SqliteSchema(listOf(table1))
    view.updateDatabases(
      listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId, true), schema, 0))
    )

    // Act
    view.updateDatabaseSchema(ViewDatabase(databaseId, true), listOf(RemoveTable(table1.name)))

    // Assert
    assertTreeContainsNodes(tree, mapOf(Pair(ViewDatabase(databaseId, true), emptyList())))
  }

  fun testUpdateDatabaseAddsTableNode() {
    // Prepare
    val tree = view.component.getDescendant<Tree>()

    val column1 = SqliteColumn("c1", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
    val schema = SqliteSchema(listOf(table1))
    view.updateDatabases(
      listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId, true), schema, 0))
    )

    val tableToAdd = SqliteTable("t2", listOf(column1, column2), null, false)

    // Act
    view.updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(
        AddTable(
          IndexedSqliteTable(tableToAdd, 1),
          listOf(IndexedSqliteColumn(column1, 0), IndexedSqliteColumn(column2, 1)),
        )
      ),
    )

    // Assert
    assertTreeContainsNodes(
      tree,
      mapOf(Pair(ViewDatabase(databaseId, true), listOf(table1, tableToAdd))),
    )
  }

  fun testUpdateDatabaseAddsColumn() {
    // Prepare
    val tree = view.component.getDescendant<Tree>()

    val column1 = SqliteColumn("c1", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
    val schema = SqliteSchema(listOf(table1))
    view.updateDatabases(
      listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId, true), schema, 0))
    )

    val column3 = SqliteColumn("c3", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val table2 = SqliteTable("t1", listOf(column1, column2, column3), null, false)

    // Act
    view.updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(AddColumns(table2.name, listOf(IndexedSqliteColumn(column3, 2)), table2)),
    )

    // Assert
    assertTreeContainsNodes(tree, mapOf(Pair(ViewDatabase(databaseId, true), listOf(table2))))
  }

  fun testUpdateDatabaseRemovesColumn() {
    // Prepare
    val tree = view.component.getDescendant<Tree>()

    val column1 = SqliteColumn("c1", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
    val schema = SqliteSchema(listOf(table1))
    view.updateDatabases(
      listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId, true), schema, 0))
    )

    val table2 = SqliteTable("t1", listOf(column1), null, false)

    // Act
    view.updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(RemoveColumns(table1.name, listOf(column2), table2)),
    )

    // Assert
    assertTreeContainsNodes(tree, mapOf(Pair(ViewDatabase(databaseId, true), listOf(table2))))
  }

  fun testUpdateDatabaseReplacesOldTableForNewTable() {
    // Prepare
    val tree = view.component.getDescendant<Tree>()

    val column1 = SqliteColumn("c1", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
    val schema = SqliteSchema(listOf(table1))
    view.updateDatabases(
      listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId, true), schema, 0))
    )

    val newTable = SqliteTable("t2", listOf(column1, column2), null, false)

    // Act
    view.updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(
        RemoveTable(table1.name),
        AddTable(
          IndexedSqliteTable(newTable, 0),
          listOf(IndexedSqliteColumn(column1, 0), IndexedSqliteColumn(column2, 1)),
        ),
      ),
    )

    // Assert
    assertTreeContainsNodes(tree, mapOf(Pair(ViewDatabase(databaseId, true), listOf(newTable))))
  }

  fun testUpdateDatabaseReplacesOldColumnForNewColumn() {
    // Prepare
    val tree = view.component.getDescendant<Tree>()

    val column1 = SqliteColumn("c1", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
    val schema = SqliteSchema(listOf(table1))
    view.updateDatabases(
      listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId, true), schema, 0))
    )

    val newColumn =
      SqliteColumn("c3", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val table1AfterRemove = SqliteTable("t1", listOf(column1), null, false)
    val finalTable = SqliteTable("t1", listOf(column1, newColumn), null, false)

    // Act
    view.updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(
        RemoveColumns(table1.name, listOf(column2), table1AfterRemove),
        AddColumns(finalTable.name, listOf(IndexedSqliteColumn(newColumn, 1)), finalTable),
      ),
    )

    // Assert
    assertTreeContainsNodes(tree, mapOf(Pair(ViewDatabase(databaseId, true), listOf(finalTable))))
  }

  fun testUpdateDatabaseAddsTableAccordingToIndex() {
    // Prepare
    val tree = view.component.getDescendant<Tree>()

    val column1 = SqliteColumn("c1", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
    val schema = SqliteSchema(listOf(table1))
    view.updateDatabases(
      listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId, true), schema, 0))
    )

    val tableToAdd = SqliteTable("t2", listOf(column1, column2), null, false)

    // Act
    view.updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(
        AddTable(
          IndexedSqliteTable(tableToAdd, 0),
          listOf(IndexedSqliteColumn(column1, 0), IndexedSqliteColumn(column2, 1)),
        )
      ),
    )

    // Assert
    assertTreeContainsNodes(
      tree,
      mapOf(Pair(ViewDatabase(databaseId, true), listOf(tableToAdd, table1))),
    )
  }

  fun testUpdateDatabaseAddsColumnAccordingToIndex() {
    // Prepare
    val tree = view.component.getDescendant<Tree>()

    val column1 = SqliteColumn("c1", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val column2 = SqliteColumn("c2", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
    val schema = SqliteSchema(listOf(table1))
    view.updateDatabases(
      listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId, true), schema, 0))
    )

    val column3 = SqliteColumn("c3", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
    val table2 = SqliteTable("t1", listOf(column3, column1, column2), null, false)

    // Act
    view.updateDatabaseSchema(
      ViewDatabase(databaseId, true),
      listOf(AddColumns(table2.name, listOf(IndexedSqliteColumn(column3, 0)), table2)),
    )

    // Assert
    assertTreeContainsNodes(tree, mapOf(Pair(ViewDatabase(databaseId, true), listOf(table2))))
  }

  fun testEmptyStateIsShownInitially() {
    // Prepare
    val emptyStateRightPanel =
      view.component.getDescendant<JComponent> { it.name == "right-panel-empty-state" }
    val syncSchemaButton =
      view.component.getDescendant<JComponent> { it.name == "refresh-schema-button" }
    val runSqlButton = view.component.getDescendant<JComponent> { it.name == "run-sql-button" }
    val tree = view.component.getDescendant<Tree> { it.name == "left-panel-tree" }

    // Assert
    assertThat(emptyStateRightPanel.isVisible).isTrue()
    assertThat(syncSchemaButton.isEnabled).isFalse()
    assertThat(runSqlButton.isEnabled).isFalse()

    // tree.emptyText is shown when the root is null
    assertThat(tree.model.root).isNull()
  }

  fun testTreeEmptyStateIsHiddenAfterOpeningADatabase() {
    // Prepare
    val tree = view.component.getDescendant<Tree> { it.name == "left-panel-tree" }

    // Act
    view.updateDatabases(
      listOf(
        DatabaseDiffOperation.AddDatabase(
          ViewDatabase(databaseId, true),
          SqliteSchema(emptyList()),
          0,
        )
      )
    )

    // Assert
    val tabsPanelAfterAddingDb =
      view.component.findDescendant<JComponent> { it.name == "right-panel-tabs-panel" }
    val syncSchemaButtonAfterAddingDb =
      view.component.getDescendant<JComponent> { it.name == "refresh-schema-button" }
    val runSqlButtonAfterAddingDb =
      view.component.getDescendant<JComponent> { it.name == "run-sql-button" }
    val treeRootAfterAddingDb = tree.model.root

    assertThat(tabsPanelAfterAddingDb).isNull()
    // tree.emptyText is shown when the root is null
    assertThat(treeRootAfterAddingDb).isNotNull()
    assertThat(syncSchemaButtonAfterAddingDb.isEnabled).isTrue()
    assertThat(runSqlButtonAfterAddingDb.isEnabled).isTrue()
  }

  fun testRightPanelEmptyStateIsHiddenAfterOpeningATab() {
    // Act
    view.updateDatabases(
      listOf(
        DatabaseDiffOperation.AddDatabase(
          ViewDatabase(databaseId, true),
          SqliteSchema(emptyList()),
          0,
        )
      )
    )
    view.openTab(TabId.AdHocQueryTab(1), "new tab", StudioIcons.DatabaseInspector.TABLE, JPanel())

    // Assert
    val emptyStateRightPanelAfterAddingTab =
      view.component.findDescendant<JComponent> { it.name == "right-panel-empty-state" }
    val tabsPanelAfterAddingTab =
      view.component.findDescendant<JComponent> { it.name == "right-panel-tabs-panel" }

    assertThat(emptyStateRightPanelAfterAddingTab).isNull()
    assertThat(tabsPanelAfterAddingTab).isNotNull()
  }

  fun testRightPanelEmptyStateIsShownAfterAllTabsAreClosed() {
    // Prepare
    val tabId = TabId.AdHocQueryTab(1)

    // Act
    view.updateDatabases(
      listOf(
        DatabaseDiffOperation.AddDatabase(
          ViewDatabase(databaseId, true),
          SqliteSchema(emptyList()),
          0,
        )
      )
    )
    view.openTab(tabId, "new tab", StudioIcons.DatabaseInspector.TABLE, JPanel())

    // Assert
    val emptyStateRightPanelAfterAddingTab =
      view.component.findDescendant<JComponent> { it.name == "right-panel-empty-state" }
    val tabsPanelAfterAddingTab =
      view.component.findDescendant<JComponent> { it.name == "right-panel-tabs-panel" }

    assertThat(emptyStateRightPanelAfterAddingTab).isNull()
    assertThat(tabsPanelAfterAddingTab).isNotNull()

    // Act
    view.closeTab(tabId)

    // Assert
    val emptyStateRightPanelAfterRemovingTab =
      view.component.findDescendant<JComponent> { it.name == "right-panel-empty-state" }
    val tabsPanelAfterRemovingTab =
      view.component.findDescendant<JComponent> { it.name == "right-panel-tabs-panel" }

    assertThat(emptyStateRightPanelAfterRemovingTab).isNotNull()
    assertThat(tabsPanelAfterRemovingTab).isNull()
  }

  fun testEmptyStateIsShownAfterOpenDatabasesAreRemoved() {
    // Prepare
    val tree = view.component.getDescendant<Tree> { it.name == "left-panel-tree" }

    // Act
    view.updateDatabases(
      listOf(
        DatabaseDiffOperation.AddDatabase(
          ViewDatabase(databaseId, true),
          SqliteSchema(emptyList()),
          0,
        )
      )
    )
    view.updateDatabases(
      listOf(DatabaseDiffOperation.RemoveDatabase(ViewDatabase(databaseId, true)))
    )

    // Assert
    val emptyStateRightPanelAfterRemovingDb =
      view.component.findDescendant<JComponent> { it.name == "right-panel-empty-state" }
    val tabsPanelAfterRemovingDb =
      view.component.findDescendant<JComponent> { it.name == "right-panel-tabs-panel" }
    val syncSchemaButtonAfterRemovingDb =
      view.component.getDescendant<JComponent> { it.name == "refresh-schema-button" }
    val runSqlButtonAfterRemovingDb =
      view.component.getDescendant<JComponent> { it.name == "run-sql-button" }
    val treeRootAfterRemovingDb = tree.model.root

    assertThat(emptyStateRightPanelAfterRemovingDb).isNotNull()
    assertThat(tabsPanelAfterRemovingDb).isNull()
    // tree.emptyText is shown when the root is null
    assertThat(treeRootAfterRemovingDb).isNull()

    assertThat(syncSchemaButtonAfterRemovingDb.isEnabled).isFalse()
    assertThat(runSqlButtonAfterRemovingDb.isEnabled).isFalse()
  }

  fun testTabsAreNotHiddenIfANewDatabaseIsAdded() {
    // Prepare
    val databaseId1 = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("db1")))
    val databaseId2 = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("db2")))
    view.updateDatabases(
      listOf(
        DatabaseDiffOperation.AddDatabase(
          ViewDatabase(databaseId1, true),
          SqliteSchema(emptyList()),
          0,
        )
      )
    )

    // Act
    view.openTab(TabId.AdHocQueryTab(1), "tab", StudioIcons.DatabaseInspector.TABLE, JPanel())
    view.updateDatabases(
      listOf(
        DatabaseDiffOperation.AddDatabase(
          ViewDatabase(databaseId2, true),
          SqliteSchema(emptyList()),
          0,
        )
      )
    )

    // Assert
    val emptyStateRightPanel =
      view.component.findDescendant<JComponent> { it.name == "right-panel-empty-state" }
    val tabsPanel = view.component.getDescendant<JComponent> { it.name == "right-panel-tabs-panel" }

    assertThat(emptyStateRightPanel).isNull()
    assertThat(tabsPanel.isVisible).isTrue()
  }

  fun testUpdateKeepConnectionOpenButton() {
    // Prepare
    val button =
      view.component.getDescendant<JToggleButton> { it.name == "keep-connections-open-button" }

    // Assert
    assertThat(button.icon).isEqualTo(StudioIcons.DatabaseInspector.KEEP_DATABASES_OPEN)

    // Act
    view.updateKeepConnectionOpenButton(true)

    // Assert
    assertThat(button.icon).isEqualTo(StudioIcons.DatabaseInspector.KEEP_DATABASES_OPEN)

    // Act
    view.updateKeepConnectionOpenButton(false)

    // Assert
    assertThat(button.icon).isEqualTo(StudioIcons.DatabaseInspector.ALLOW_DATABASES_TO_CLOSE)
  }

  fun testKeepConnectionOpenIsDisabledWithOfflineDatabases() {
    // Prepare
    val fileDatabaseId1 =
      SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("file1")))
    val fileDatabaseId2 =
      SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("file2")))
    val keepConnectionsOpenButton =
      view.component.getDescendant<JToggleButton> { it.name == "keep-connections-open-button" }

    // Act
    view.updateDatabases(
      listOf(
        DatabaseDiffOperation.AddDatabase(
          ViewDatabase(fileDatabaseId1, true),
          SqliteSchema(emptyList()),
          0,
        )
      )
    )
    view.updateDatabases(
      listOf(
        DatabaseDiffOperation.AddDatabase(
          ViewDatabase(fileDatabaseId2, true),
          SqliteSchema(emptyList()),
          0,
        )
      )
    )

    // Assert
    assertThat(keepConnectionsOpenButton.isEnabled).isFalse()
  }

  fun testKeepConnectionOpenIsEnabledWithLiveDatabases() {
    // Prepare
    val liveDatabaseId = SqliteDatabaseId.fromLiveDatabase("", 0)
    val keepConnectionsOpenButton =
      view.component.getDescendant<JToggleButton> { it.name == "keep-connections-open-button" }

    // Act
    view.updateDatabases(
      listOf(
        DatabaseDiffOperation.AddDatabase(
          ViewDatabase(liveDatabaseId, true),
          SqliteSchema(emptyList()),
          0,
        )
      )
    )

    // Assert
    assertThat(keepConnectionsOpenButton.isEnabled).isTrue()
  }

  fun testKeepConnectionOpenIsEnableIfAtLeastOneOnlineDatabase() {
    // Prepare
    val fileDatabaseId1 =
      SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("file1")))
    val fileDatabaseId2 =
      SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("file2")))
    val liveDatabaseId = SqliteDatabaseId.fromLiveDatabase("", 0)
    val keepConnectionsOpenButton =
      view.component.getDescendant<JToggleButton> { it.name == "keep-connections-open-button" }

    // Act
    view.updateDatabases(
      listOf(
        DatabaseDiffOperation.AddDatabase(
          ViewDatabase(fileDatabaseId1, true),
          SqliteSchema(emptyList()),
          0,
        )
      )
    )
    view.updateDatabases(
      listOf(
        DatabaseDiffOperation.AddDatabase(
          ViewDatabase(liveDatabaseId, true),
          SqliteSchema(emptyList()),
          0,
        )
      )
    )
    view.updateDatabases(
      listOf(
        DatabaseDiffOperation.AddDatabase(
          ViewDatabase(fileDatabaseId2, true),
          SqliteSchema(emptyList()),
          0,
        )
      )
    )

    // Assert
    assertThat(keepConnectionsOpenButton.isEnabled).isTrue()

    // Act
    view.updateDatabases(
      listOf(DatabaseDiffOperation.RemoveDatabase(ViewDatabase(liveDatabaseId, true)))
    )

    // Assert
    assertThat(keepConnectionsOpenButton.isEnabled).isFalse()
  }

  fun testTreeRootNodeIsExpandedWhenEmptyNodeIsAdded() {
    // Prepare
    val fileDatabaseId1 =
      SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("file1")))

    val tree = view.component.findDescendant<JComponent> { it.name == "left-panel-tree" } as Tree
    val diffOperations =
      listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(fileDatabaseId1, true), null, 0))

    // Act
    view.updateDatabases(diffOperations)

    // Assert
    val root = tree.model.root
    assertThat(tree.isExpanded(TreePath((root)))).isTrue()
  }

  private fun assertTreeContainsNodes(tree: Tree, databases: Map<ViewDatabase, List<SqliteTable>>) {
    val root = tree.model.root
    assertThat(tree.model.getChildCount(root)).isEqualTo(databases.size)

    databases.keys.forEachIndexed { databaseIndex, database ->
      val databaseNode = tree.model.getChild(root, databaseIndex) as DefaultMutableTreeNode
      assertThat(databaseNode.userObject).isEqualTo(database)
      assertThat(tree.model.getChildCount(databaseNode)).isEqualTo(databases[database]!!.size)

      databases[database]!!.forEachIndexed { tableIndex, table ->
        val tableNode = tree.model.getChild(databaseNode, tableIndex) as DefaultMutableTreeNode
        assertThat(tableNode.userObject).isEqualTo(table)
        assertThat(tree.model.getChildCount(tableNode)).isEqualTo(table.columns.size)

        table.columns.forEachIndexed { columnIndex, column ->
          val columnNode = tree.model.getChild(tableNode, columnIndex) as DefaultMutableTreeNode
          assertThat(columnNode.userObject).isEqualTo(column)
        }
      }
    }
  }
}
