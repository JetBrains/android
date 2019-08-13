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
package com.android.tools.idea.sqlite.mocks

import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorView
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorViewListener
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.intellij.openapi.project.Project
import org.mockito.Mockito.mock
import java.util.ArrayList
import javax.swing.JComponent

open class MockSqliteEvaluatorView : SqliteEvaluatorView {
  override val project: Project = mock(Project::class.java)
  override val component: JComponent = mock(JComponent::class.java)
  override val tableView: TableView = mock(TableView::class.java)

  val listeners = ArrayList<SqliteEvaluatorViewListener>()

  override fun addListener(listener: SqliteEvaluatorViewListener) { listeners.add(listener) }

  override fun removeListener(listener: SqliteEvaluatorViewListener) { listeners.remove(listener) }

  override fun showSqliteStatement(sqliteStatement: String) {  }

  override fun addDatabase(database: SqliteDatabase, index: Int) { }

  override fun removeDatabase(index: Int) { }

  override fun selectDatabase(database: SqliteDatabase) { }
}