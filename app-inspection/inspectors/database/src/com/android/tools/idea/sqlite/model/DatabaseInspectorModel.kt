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
package com.android.tools.idea.sqlite.model

import com.android.annotations.concurrency.UiThread
import com.intellij.openapi.application.ApplicationManager

/** Class used to store and access [SqliteDatabase]s and their [SqliteSchema]s. */
@UiThread
interface DatabaseInspectorModel {
  fun getOpenDatabaseIds(): List<SqliteDatabaseId>
  fun getCloseDatabaseIds(): List<SqliteDatabaseId>

  fun getDatabaseSchema(databaseId: SqliteDatabaseId): SqliteSchema?

  fun addDatabaseSchema(databaseId: SqliteDatabaseId, sqliteSchema: SqliteSchema)
  fun removeDatabaseSchema(databaseId: SqliteDatabaseId)

  fun updateSchema(databaseId: SqliteDatabaseId, newSchema: SqliteSchema)

  fun clearDatabases()

  fun addListener(modelListener: Listener)
  fun removeListener(modelListener: Listener)

  @UiThread
  interface Listener {
    fun onDatabasesChanged(
      openDatabaseIds: List<SqliteDatabaseId>,
      closeDatabaseIds: List<SqliteDatabaseId>
    )
    fun onSchemaChanged(
      databaseId: SqliteDatabaseId,
      oldSchema: SqliteSchema,
      newSchema: SqliteSchema
    )
  }
}

@UiThread
class DatabaseInspectorModelImpl : DatabaseInspectorModel {
  private val listeners = mutableListOf<DatabaseInspectorModel.Listener>()

  private val openDatabases = mutableMapOf<SqliteDatabaseId, SqliteSchema>()
  private val closeDatabases = mutableSetOf<SqliteDatabaseId>()

  override fun getOpenDatabaseIds(): List<SqliteDatabaseId> {
    ApplicationManager.getApplication().assertIsDispatchThread()

    return openDatabases.keys.toList()
  }

  override fun getCloseDatabaseIds(): List<SqliteDatabaseId> {
    ApplicationManager.getApplication().assertIsDispatchThread()

    return closeDatabases.toList()
  }

  override fun getDatabaseSchema(databaseId: SqliteDatabaseId): SqliteSchema? {
    ApplicationManager.getApplication().assertIsDispatchThread()

    return openDatabases[databaseId]
  }

  override fun addDatabaseSchema(databaseId: SqliteDatabaseId, sqliteSchema: SqliteSchema) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    closeDatabases.remove(databaseId)
    openDatabases[databaseId] = sqliteSchema
    listeners.forEach {
      it.onDatabasesChanged(openDatabases.keys.toList(), closeDatabases.toList())
    }
  }

  override fun removeDatabaseSchema(databaseId: SqliteDatabaseId) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    openDatabases.remove(databaseId)
    // only add live databases to closed dbs
    if (databaseId is SqliteDatabaseId.LiveSqliteDatabaseId) {
      closeDatabases.add(databaseId)
    }

    listeners.forEach {
      it.onDatabasesChanged(openDatabases.keys.toList(), closeDatabases.toList())
    }
  }

  override fun updateSchema(databaseId: SqliteDatabaseId, newSchema: SqliteSchema) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    val oldSchema = openDatabases[databaseId] ?: return
    openDatabases[databaseId] = newSchema

    listeners.forEach { it.onSchemaChanged(databaseId, oldSchema, newSchema) }
  }

  override fun clearDatabases() {
    ApplicationManager.getApplication().assertIsDispatchThread()

    openDatabases.clear()
    closeDatabases.clear()

    listeners.forEach { it.onDatabasesChanged(emptyList(), emptyList()) }
  }

  override fun addListener(modelListener: DatabaseInspectorModel.Listener) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    listeners.add(modelListener)
    modelListener.onDatabasesChanged(openDatabases.keys.toList(), closeDatabases.toList())
  }

  override fun removeListener(modelListener: DatabaseInspectorModel.Listener) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    listeners.remove(modelListener)
  }
}
