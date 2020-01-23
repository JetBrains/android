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
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.model.FileSqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorViewImpl
import com.android.tools.idea.sqlite.ui.tableView.TableViewImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import org.mockito.Mockito.mock
import java.awt.Dimension
import javax.swing.JComboBox

class SqliteEvaluatorViewImplTest: LightPlatformTestCase() {
  private lateinit var view: SqliteEvaluatorViewImpl

  override fun setUp() {
    super.setUp()
    view = SqliteEvaluatorViewImpl(project, TableViewImpl(), object : SchemaProvider {
      override fun getSchema(database: SqliteDatabase): SqliteSchema? { return SqliteSchema(emptyList()) }
    })
    val component = view.component
    component.size = Dimension(600, 200)
  }

  fun testAddAndRemoveDatabases() {
    val treeWalker = TreeWalker(view.component)

    val comboBox = treeWalker.descendantStream().filter(JComboBox::class.java::isInstance).findFirst().get() as JComboBox<*>
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
}