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

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.sqlite.ui.tableView.TableViewImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import java.util.ArrayList
import javax.swing.JComponent

/**
 * A dialog that can be used to run sql queries and updates.
 */
@UiThread
class SqliteEvaluatorDialog(
  project: Project?,
  canBeParent: Boolean
) : DialogWrapper(project, canBeParent), SqliteEvaluatorView {

  private val evaluatorPanel = SqliteEvaluatorPanel()
  override val component: JComponent = evaluatorPanel.root

  override val tableView = TableViewImpl()

  private val listeners = ArrayList<SqliteEvaluatorViewListener>()

  init {
    evaluatorPanel.root.add(tableView.component, BorderLayout.CENTER)

    isModal = false
    title = "SQL evaluator"
    setOKButtonText("Evaluate")
    setCancelButtonText("Close")

    init()
  }

  override fun addListener(listener: SqliteEvaluatorViewListener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: SqliteEvaluatorViewListener) {
    listeners.remove(listener)
  }

  override fun requestFocus() {
    toFront()
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return evaluatorPanel.textField
  }

  override fun doOKAction() {
    listeners.forEach { it.evaluateSqlActionInvoked(evaluatorPanel.textField.text) }
  }

  override fun doCancelAction() {
    listeners.forEach { it.sessionClosed() }
    super.doCancelAction()
  }

  override fun createCenterPanel(): JComponent = evaluatorPanel.root

  override fun dispose() {
    super.dispose()
    evaluatorPanel.root.removeAll()
  }
}