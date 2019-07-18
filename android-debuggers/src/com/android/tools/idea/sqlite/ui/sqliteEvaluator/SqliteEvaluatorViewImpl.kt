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
package com.android.tools.idea.sqlite.ui.sqliteEvaluator

import com.android.tools.idea.lang.androidSql.AndroidSqlLanguage
import com.android.tools.idea.sqlite.ui.tableView.TableViewImpl
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import java.awt.BorderLayout
import java.util.ArrayList
import javax.swing.JComponent

/**
 * @see SqliteEvaluatorView
 */
class SqliteEvaluatorViewImpl(override val project: Project) : SqliteEvaluatorView {
  private val evaluatorPanel = SqliteEvaluatorPanel()
  override val component: JComponent = evaluatorPanel.root
  override val tableView = TableViewImpl()

  private val editorTextField = EditorTextField("", project, AndroidSqlLanguage.INSTANCE.associatedFileType)

  private val listeners = ArrayList<SqliteEvaluatorViewListener>()

  init {
    evaluatorPanel.controlsContainer.add(editorTextField, BorderLayout.CENTER)
    evaluatorPanel.root.add(tableView.component, BorderLayout.CENTER)
    evaluatorPanel.evaluateButton.addActionListener {
      listeners.forEach {
        it.evaluateSqlActionInvoked(editorTextField.text)
      }
    }
  }

  override fun addListener(listener: SqliteEvaluatorViewListener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: SqliteEvaluatorViewListener) {
    listeners.remove(listener)
  }

  override fun showSqliteStatement(sqliteStatement: String) {
    editorTextField.text = sqliteStatement
  }
}