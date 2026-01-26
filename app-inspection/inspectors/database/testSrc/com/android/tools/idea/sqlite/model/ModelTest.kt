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

import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockVirtualFile
import java.sql.JDBCType
import junit.framework.TestCase

class ModelTest : TestCase() {
  fun testSqliteAffinityFromString() {
    assertThat(SqliteAffinity.fromTypename("int")).isEqualTo(SqliteAffinity.INTEGER)
    assertThat(SqliteAffinity.fromTypename("integer")).isEqualTo(SqliteAffinity.INTEGER)
    assertThat(SqliteAffinity.fromTypename("tinyint")).isEqualTo(SqliteAffinity.INTEGER)
    assertThat(SqliteAffinity.fromTypename("samllint")).isEqualTo(SqliteAffinity.INTEGER)
    assertThat(SqliteAffinity.fromTypename("mediumint")).isEqualTo(SqliteAffinity.INTEGER)
    assertThat(SqliteAffinity.fromTypename("bigint")).isEqualTo(SqliteAffinity.INTEGER)
    assertThat(SqliteAffinity.fromTypename("unsigned big int")).isEqualTo(SqliteAffinity.INTEGER)
    assertThat(SqliteAffinity.fromTypename("int2")).isEqualTo(SqliteAffinity.INTEGER)
    assertThat(SqliteAffinity.fromTypename("int8")).isEqualTo(SqliteAffinity.INTEGER)
    assertThat(SqliteAffinity.fromTypename("charint")).isEqualTo(SqliteAffinity.INTEGER)
    assertThat(SqliteAffinity.fromTypename("somethingintsomething"))
      .isEqualTo(SqliteAffinity.INTEGER)

    assertThat(SqliteAffinity.fromTypename("char")).isEqualTo(SqliteAffinity.TEXT)
    assertThat(SqliteAffinity.fromTypename("character")).isEqualTo(SqliteAffinity.TEXT)
    assertThat(SqliteAffinity.fromTypename("varchar")).isEqualTo(SqliteAffinity.TEXT)
    assertThat(SqliteAffinity.fromTypename("nchar")).isEqualTo(SqliteAffinity.TEXT)
    assertThat(SqliteAffinity.fromTypename("native character")).isEqualTo(SqliteAffinity.TEXT)
    assertThat(SqliteAffinity.fromTypename("nvarchar")).isEqualTo(SqliteAffinity.TEXT)
    assertThat(SqliteAffinity.fromTypename("clob")).isEqualTo(SqliteAffinity.TEXT)
    assertThat(SqliteAffinity.fromTypename("text")).isEqualTo(SqliteAffinity.TEXT)

    assertThat(SqliteAffinity.fromTypename("blob")).isEqualTo(SqliteAffinity.BLOB)
    assertThat(SqliteAffinity.fromTypename("")).isEqualTo(SqliteAffinity.BLOB)

    assertThat(SqliteAffinity.fromTypename("real")).isEqualTo(SqliteAffinity.REAL)
    assertThat(SqliteAffinity.fromTypename("floa")).isEqualTo(SqliteAffinity.REAL)
    assertThat(SqliteAffinity.fromTypename("float")).isEqualTo(SqliteAffinity.REAL)
    assertThat(SqliteAffinity.fromTypename("doub")).isEqualTo(SqliteAffinity.REAL)
    assertThat(SqliteAffinity.fromTypename("double")).isEqualTo(SqliteAffinity.REAL)
    assertThat(SqliteAffinity.fromTypename("double precision")).isEqualTo(SqliteAffinity.REAL)

    assertThat(SqliteAffinity.fromTypename("numeric")).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromTypename("decimal")).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromTypename("boolean")).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromTypename("date")).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromTypename("datetime")).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromTypename("whatever")).isEqualTo(SqliteAffinity.NUMERIC)
  }

  fun testSqliteAffinityFromJDBCType() {
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.TINYINT)).isEqualTo(SqliteAffinity.INTEGER)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.SMALLINT)).isEqualTo(SqliteAffinity.INTEGER)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.INTEGER)).isEqualTo(SqliteAffinity.INTEGER)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.BIGINT)).isEqualTo(SqliteAffinity.INTEGER)

    assertThat(SqliteAffinity.fromJDBCType(JDBCType.CHAR)).isEqualTo(SqliteAffinity.TEXT)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.VARCHAR)).isEqualTo(SqliteAffinity.TEXT)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.LONGVARCHAR)).isEqualTo(SqliteAffinity.TEXT)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.CLOB)).isEqualTo(SqliteAffinity.TEXT)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.NCLOB)).isEqualTo(SqliteAffinity.TEXT)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.NVARCHAR)).isEqualTo(SqliteAffinity.TEXT)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.LONGNVARCHAR)).isEqualTo(SqliteAffinity.TEXT)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.NCHAR)).isEqualTo(SqliteAffinity.TEXT)

    assertThat(SqliteAffinity.fromJDBCType(JDBCType.BLOB)).isEqualTo(SqliteAffinity.BLOB)

    assertThat(SqliteAffinity.fromJDBCType(JDBCType.FLOAT)).isEqualTo(SqliteAffinity.REAL)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.REAL)).isEqualTo(SqliteAffinity.REAL)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.DOUBLE)).isEqualTo(SqliteAffinity.REAL)

    assertThat(SqliteAffinity.fromJDBCType(JDBCType.BIT)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.NUMERIC)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.DECIMAL)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.DATE)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.TIME)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.TIMESTAMP)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.BINARY)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.LONGVARBINARY))
      .isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.NULL)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.OTHER)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.JAVA_OBJECT)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.DISTINCT)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.STRUCT)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.ARRAY)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.REF)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.DATALINK)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.BOOLEAN)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.ROWID)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.SQLXML)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.REF_CURSOR)).isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.TIME_WITH_TIMEZONE))
      .isEqualTo(SqliteAffinity.NUMERIC)
    assertThat(SqliteAffinity.fromJDBCType(JDBCType.TIMESTAMP_WITH_TIMEZONE))
      .isEqualTo(SqliteAffinity.NUMERIC)
  }

  fun testFileDatabaseNameAndPath() {
    val databaseId =
      SqliteDatabaseId.fromFileDatabase(
        DatabaseFileData(MockVirtualFile("someDir/data/data/com.example.package/databases/db-file"))
      )

    assertThat(databaseId.name).isEqualTo("db-file")
    assertThat(databaseId.path).isEqualTo("/data/data/com.example.package/databases/db-file")
  }

  fun testLiveDatabasePathIsConverted() {
    val databaseId =
      SqliteDatabaseId.fromLiveDatabase("/data/user/0/com.example.package/databases/db-file", 0)

    assertThat(databaseId.name).isEqualTo("db-file")
    assertThat(databaseId.path).isEqualTo("/data/data/com.example.package/databases/db-file")

    val databaseIdSdCard =
      SqliteDatabaseId.fromLiveDatabase(
        "/storage/emulated/0/com.example.package/databases/db-file",
        0,
      )

    assertThat(databaseIdSdCard.name).isEqualTo("db-file")
    assertThat(databaseIdSdCard.path).isEqualTo("/sdcard/com.example.package/databases/db-file")
  }
}
