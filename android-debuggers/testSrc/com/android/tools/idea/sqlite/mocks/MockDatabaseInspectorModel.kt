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

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.sqlite.controllers.DatabaseInspectorController
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.intellij.openapi.application.ApplicationManager
import java.util.TreeMap

@UiThread
open class MockDatabaseInspectorModel : DatabaseInspectorController.Model {
  private val listeners = mutableListOf<DatabaseInspectorController.Model.Listener>()

  private val openDatabases: TreeMap<SqliteDatabase, SqliteSchema> = TreeMap(
    Comparator.comparing { database: SqliteDatabase -> database.name }
  )

  override fun getOpenDatabases(): List<SqliteDatabase> {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return openDatabases.keys.toList()
  }

  override fun getDatabaseSchema(database: SqliteDatabase): SqliteSchema? {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return openDatabases[database]
  }

  override fun getSortedIndexOf(database: SqliteDatabase): Int {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return openDatabases.headMap(database).size
  }

  override fun add(database: SqliteDatabase, sqliteSchema: SqliteSchema) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    openDatabases[database] = sqliteSchema
    listeners.forEach { it.onDatabaseAdded(database) }
  }

  override fun remove(database: SqliteDatabase) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    openDatabases.remove(database)
    listeners.forEach { it.onDatabaseRemoved(database) }
  }

  override fun addListener(modelListener: DatabaseInspectorController.Model.Listener) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    listeners.add(modelListener)
  }

  override fun removeListener(modelListener: DatabaseInspectorController.Model.Listener) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    listeners.remove(modelListener)
  }
}