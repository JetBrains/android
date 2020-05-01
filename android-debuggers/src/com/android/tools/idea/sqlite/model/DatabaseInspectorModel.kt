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
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.intellij.openapi.application.ApplicationManager

/**
 * Class used to store and access currently open [SqliteDatabase]s and their [SqliteSchema]s.
 */
@UiThread
interface DatabaseInspectorModel {
  fun getOpenDatabaseIds(): List<SqliteDatabaseId>
  fun getDatabaseSchema(databaseId: SqliteDatabaseId): SqliteSchema?
  fun getDatabaseConnection(databaseId: SqliteDatabaseId): DatabaseConnection?

  fun add(databaseId: SqliteDatabaseId, databaseConnection: DatabaseConnection, sqliteSchema: SqliteSchema)
  fun remove(databaseId: SqliteDatabaseId): DatabaseConnection?

  fun updateSchema(databaseId: SqliteDatabaseId, newSchema: SqliteSchema)

  fun addListener(modelListener: Listener)
  fun removeListener(modelListener: Listener)

  @UiThread
  interface Listener {
    fun onDatabasesChanged(databaseIds: List<SqliteDatabaseId>)
    fun onSchemaChanged(databaseId: SqliteDatabaseId, oldSchema: SqliteSchema, newSchema: SqliteSchema)
  }
}

@UiThread
class DatabaseInspectorModelImpl : DatabaseInspectorModel {
  private val listeners = mutableListOf<DatabaseInspectorModel.Listener>()

  private val openDatabases = mutableMapOf<SqliteDatabaseId, DatabaseObjects>()

  override fun getOpenDatabaseIds(): List<SqliteDatabaseId> {
    ApplicationManager.getApplication().assertIsDispatchThread()

    return openDatabases.keys.toList()
  }

  override fun getDatabaseSchema(databaseId: SqliteDatabaseId): SqliteSchema? {
    ApplicationManager.getApplication().assertIsDispatchThread()

    return openDatabases[databaseId]?.schema
  }

  override fun getDatabaseConnection(databaseId: SqliteDatabaseId): DatabaseConnection? {
    ApplicationManager.getApplication().assertIsDispatchThread()

    return openDatabases[databaseId]?.connection
  }

  override fun add(databaseId: SqliteDatabaseId, databaseConnection: DatabaseConnection, sqliteSchema: SqliteSchema) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    openDatabases[databaseId] = DatabaseObjects(sqliteSchema, databaseConnection)
    listeners.forEach { it.onDatabasesChanged(openDatabases.keys.toList()) }
  }

  override fun updateSchema(databaseId: SqliteDatabaseId, newSchema: SqliteSchema) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    val databaseObjects = openDatabases[databaseId] ?: return
    val oldSchema = databaseObjects.schema
    databaseObjects.schema = newSchema

    listeners.forEach { it.onSchemaChanged(databaseId, oldSchema, newSchema) }
  }

  override fun remove(databaseId: SqliteDatabaseId): DatabaseConnection? {
    ApplicationManager.getApplication().assertIsDispatchThread()

    val databaseObjects = openDatabases.remove(databaseId)
    listeners.forEach { it.onDatabasesChanged(openDatabases.keys.toList()) }

    return databaseObjects?.connection
  }

  override fun addListener(modelListener: DatabaseInspectorModel.Listener) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    listeners.add(modelListener)
    modelListener.onDatabasesChanged(openDatabases.keys.toList())
  }

  override fun removeListener(modelListener: DatabaseInspectorModel.Listener) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    listeners.remove(modelListener)
  }

  // TODO(b/154733971) move DatabaseConnections to dedicated repository
  private data class DatabaseObjects(var schema: SqliteSchema, val connection: DatabaseConnection)
}