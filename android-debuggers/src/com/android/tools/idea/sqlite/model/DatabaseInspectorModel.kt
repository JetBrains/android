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

/**
 * Class used to store and access currently open [SqliteDatabase]s and their [SqliteSchema]s.
 */
@UiThread
interface DatabaseInspectorModel {
  fun getOpenDatabases(): List<SqliteDatabase>
  fun getDatabaseSchema(database: SqliteDatabase): SqliteSchema?

  fun add(database: SqliteDatabase, sqliteSchema: SqliteSchema)
  fun remove(database: SqliteDatabase)

  fun updateSchema(database: SqliteDatabase, newSchema: SqliteSchema)

  fun addListener(modelListener: Listener)
  fun removeListener(modelListener: Listener)

  @UiThread
  interface Listener {
    fun onDatabasesChanged(databases: List<SqliteDatabase>)
    fun onSchemaChanged(database: SqliteDatabase, oldSchema: SqliteSchema, newSchema: SqliteSchema)
  }
}

@UiThread
class DatabaseInspectorModelImpl : DatabaseInspectorModel {

  private val listeners = mutableListOf<DatabaseInspectorModel.Listener>()
  private val openDatabases = mutableMapOf<SqliteDatabase, SqliteSchema>()

  override fun getOpenDatabases(): List<SqliteDatabase> {
    ApplicationManager.getApplication().assertIsDispatchThread()

    return openDatabases.keys.toList()
  }

  override fun getDatabaseSchema(database: SqliteDatabase): SqliteSchema? {
    ApplicationManager.getApplication().assertIsDispatchThread()

    return openDatabases[database]
  }

  override fun add(database: SqliteDatabase, sqliteSchema: SqliteSchema) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    openDatabases[database] = sqliteSchema
    val newDatabaseList = openDatabases.keys.toList()

    listeners.forEach { it.onDatabasesChanged(newDatabaseList) }
  }

  override fun remove(database: SqliteDatabase) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    openDatabases.remove(database)
    val newDatabaseList = openDatabases.keys.toList()

    listeners.forEach { it.onDatabasesChanged(newDatabaseList) }
  }

  override fun updateSchema(database: SqliteDatabase, newSchema: SqliteSchema) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    val oldSchema = openDatabases[database] ?: return
    openDatabases[database] = newSchema
    listeners.forEach { it.onSchemaChanged(database, oldSchema, newSchema) }
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
}