/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.sqlite.databaseConnection.live

import androidx.sqlite.inspection.SqliteInspectorProtocol
import com.android.tools.idea.sqlite.model.RowIdName._ROWID_
import com.google.common.truth.Truth.assertThat
import org.junit.Test

private val column = SqliteInspectorProtocol.Column.newBuilder().setName("column1").setType("TEXT")

class UtilsTest {

  @Test
  fun toSqliteSchema_withoutRowidTrue() {
    val table =
      SqliteInspectorProtocol.Table.newBuilder().addColumns(column).setWithoutRowid(true).build()

    val tables = listOf(table).toSqliteSchema().tables

    assertThat(tables.first().rowIdName).isNull()
  }

  @Test
  fun toSqliteSchema_withoutRowidFalse() {
    val table =
      SqliteInspectorProtocol.Table.newBuilder().addColumns(column).setWithoutRowid(false).build()

    val tables = listOf(table).toSqliteSchema().tables

    assertThat(tables.first().rowIdName).isEqualTo(_ROWID_)
  }

  @Test
  fun toSqliteSchema_withoutRowidUnset() {
    val table = SqliteInspectorProtocol.Table.newBuilder().addColumns(column).build()

    val tables = listOf(table).toSqliteSchema().tables

    assertThat(tables.first().rowIdName).isEqualTo(_ROWID_)
  }
}
