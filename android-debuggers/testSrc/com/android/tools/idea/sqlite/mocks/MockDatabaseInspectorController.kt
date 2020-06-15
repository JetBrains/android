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

import com.android.tools.idea.sqlite.controllers.DatabaseInspectorController
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import javax.naming.OperationNotSupportedException
import javax.swing.JComponent

open class MockDatabaseInspectorController(val model: DatabaseInspectorController.Model) : DatabaseInspectorController {

  override val component: JComponent
    get() = throw OperationNotSupportedException()

  override fun setUp() { }

  override fun addSqliteDatabase(sqliteDatabaseFuture: ListenableFuture<SqliteDatabase>): ListenableFuture<Unit> {
    val database = sqliteDatabaseFuture.get()
    model.add(database, SqliteSchema(emptyList()))
    return Futures.immediateFuture(Unit)
  }

  override fun runSqlStatement(database: SqliteDatabase, sqliteStatement: SqliteStatement): ListenableFuture<Unit> {
    return Futures.immediateFuture(Unit)
  }

  override fun closeDatabase(database: SqliteDatabase): ListenableFuture<Unit> {
    model.remove(database)
    database.databaseConnection.close().get()
    return Futures.immediateFuture(Unit)
  }

  override fun dispose() { }
}