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
package com.android.tools.idea.sqlite.ui

import com.android.annotations.concurrency.AnyThread
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.ui.renderers.ResultSetTreeCellRenderer
import com.android.tools.idea.sqlite.ui.renderers.ResultSetTreeHeaderRenderer
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.util.concurrent.CancellationException
import javax.swing.JTable
import javax.swing.table.DefaultTableModel

internal fun JBTable.setupResultSetTable(queryTableModel: DefaultTableModel, columnClass: Class<SqliteColumnValue>) {
  if (this.model != queryTableModel) {
    this.model = queryTableModel
    this.setDefaultRenderer(columnClass, ResultSetTreeCellRenderer())
    // Turn off JTable's auto resize so that JScrollPane will show a horizontal
    // scroll bar.
    this.autoResizeMode = JTable.AUTO_RESIZE_OFF
    this.emptyText.text = "Table is empty"
  }
}

internal fun JBTable.setResultSetTableColumns() {
  val headerRenderer = ResultSetTreeHeaderRenderer(this.tableHeader.defaultRenderer)
  val width = Math.max(JBUI.scale(50), (this.parent.width - JBUI.scale(10)) / this.columnModel.columnCount)
  for (index in 0 until this.columnModel.columnCount) {
    val column = this.columnModel.getColumn(index)
    column.preferredWidth = width
    column.headerRenderer = headerRenderer
  }
}

@AnyThread
internal fun reportError(message: String, t: Throwable) {
  if (t is CancellationException) {
    return
  }

  var errorMessage = message
  t.message?.let {
    errorMessage += ": " + t.message
  }

  val notification = Notification("Sqlite Viewer",
                                  "Sqlite Viewer",
                                  errorMessage,
                                  NotificationType.WARNING)

  Notifications.Bus.notify(notification)
}