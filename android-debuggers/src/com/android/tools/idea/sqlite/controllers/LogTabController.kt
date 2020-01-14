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
package com.android.tools.idea.sqlite.controllers

import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnectionListener
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.ui.logtab.LogTabView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater

class LogTabController(private val view: LogTabView) {
  init {
    ApplicationManager.getApplication().messageBus.connect().subscribe(DatabaseConnection.TOPIC, object : DatabaseConnectionListener {
      override fun onSqliteStatementExecutionSuccess(sqliteStatement: SqliteStatement) {
        invokeLater { view.log("Execution successful: ${sqliteStatement.sqliteStatementText}") }
      }

      override fun onSqliteStatementExecutionFailed(sqliteStatement: SqliteStatement) {
        invokeLater { view.logError("Execution failed: ${sqliteStatement.sqliteStatementText}") }
      }
    })
  }
}