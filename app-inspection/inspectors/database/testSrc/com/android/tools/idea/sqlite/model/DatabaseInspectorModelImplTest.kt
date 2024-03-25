/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.idea.sqlite.model.DatabaseInspectorModelImplTest.Listener.Event.DatabasesChangedEvent
import com.android.tools.idea.sqlite.model.DatabaseInspectorModelImplTest.Listener.Event.SchemaChangedEvent
import com.android.tools.idea.sqlite.model.SqliteDatabaseId.Companion.fromFileDatabase
import com.android.tools.idea.sqlite.model.SqliteDatabaseId.Companion.fromLiveDatabase
import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockVirtualFile
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for [DatabaseInspectorModelImpl] */
@RunWith(JUnit4::class)
@RunsInEdt
class DatabaseInspectorModelImplTest {

  @get:Rule val rule = RuleChain(ApplicationRule(), EdtRule())

  private val model = DatabaseInspectorModelImpl()
  private val listener = Listener()
  private val liveDb = fromLiveDatabase("/data/live.db", 1) as SqliteDatabaseId.LiveSqliteDatabaseId
  private val fileDb = fromFileDatabase(DatabaseFileData(MockVirtualFile("/data/file.db")))
  private val schema = SqliteSchema(emptyList())

  @Test
  fun addDatabaseSchema_callsOnDatabasesChanged() {
    model.addListener(listener)

    model.addDatabaseSchema(liveDb, schema)
    model.addDatabaseSchema(fileDb, schema)

    assertThat(listener.events)
      .containsExactly(
        DatabasesChangedEvent(),
        DatabasesChangedEvent(listOf(liveDb)),
        DatabasesChangedEvent(listOf(liveDb, fileDb)),
      )
      .inOrder()
  }

  @Test
  fun addDatabaseSchema_isForcedChanges() {
    model.addListener(listener)

    val forcedDb = liveDb.copy(isForced = true)
    val unforcedDb = liveDb.copy(isForced = false)
    assertThat(forcedDb).isNotEqualTo(unforcedDb)

    model.addDatabaseSchema(forcedDb, schema)
    model.addDatabaseSchema(unforcedDb, schema)
    model.addDatabaseSchema(forcedDb, schema)

    assertThat(listener.events)
      .containsExactly(
        DatabasesChangedEvent(),
        DatabasesChangedEvent(listOf(forcedDb)),
        DatabasesChangedEvent(listOf(unforcedDb)),
        DatabasesChangedEvent(listOf(forcedDb)),
      )
      .inOrder()
  }

  private class Listener : DatabaseInspectorModel.Listener {
    val events = mutableListOf<Event>()

    override fun onDatabasesChanged(
      openDatabaseIds: List<SqliteDatabaseId>,
      closeDatabaseIds: List<SqliteDatabaseId>,
    ) {
      events.add(DatabasesChangedEvent(openDatabaseIds, closeDatabaseIds))
    }

    override fun onSchemaChanged(
      databaseId: SqliteDatabaseId,
      oldSchema: SqliteSchema,
      newSchema: SqliteSchema,
    ) {
      events.add(SchemaChangedEvent(databaseId, oldSchema, newSchema))
    }

    sealed class Event {
      data class DatabasesChangedEvent(
        val openDatabaseIds: List<SqliteDatabaseId> = emptyList(),
        val closeDatabaseIds: List<SqliteDatabaseId> = emptyList(),
      ) : Event()

      data class SchemaChangedEvent(
        val databaseId: SqliteDatabaseId,
        val oldSchema: SqliteSchema,
        val newSchema: SqliteSchema,
      ) : Event()
    }
  }
}
