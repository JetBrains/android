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

import com.android.tools.idea.sqlite.controllers.SqliteController
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import java.util.TreeMap

class MockDatabaseInspectorModel : SqliteController.Model {
  private val listeners = mutableListOf<SqliteController.Model.Listener>()

  override val openDatabases: TreeMap<SqliteDatabase, SqliteSchema> = TreeMap(
    Comparator.comparing { database: SqliteDatabase -> database.name }
  )

  override fun getSortedIndexOf(database: SqliteDatabase) = openDatabases.headMap(database).size

  override fun add(database: SqliteDatabase, sqliteSchema: SqliteSchema) {
    openDatabases[database] = sqliteSchema
    listeners.forEach { it.onDatabaseAdded(database) }
  }

  override fun remove(database: SqliteDatabase) {
    openDatabases.remove(database)
    listeners.forEach { it.onDatabaseRemoved(database) }
  }

  override fun addListener(modelListener: SqliteController.Model.Listener) { listeners.add(modelListener) }

  override fun removeListener(modelListener: SqliteController.Model.Listener) { listeners.remove(modelListener) }
}