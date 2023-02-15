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

import com.intellij.mock.MockVirtualFile
import java.sql.JDBCType
import junit.framework.TestCase

class ModelTest : TestCase() {
  fun testSqliteAffinityFromString() {
    assertEquals(SqliteAffinity.INTEGER, SqliteAffinity.fromTypename("int"))
    assertEquals(SqliteAffinity.INTEGER, SqliteAffinity.fromTypename("integer"))
    assertEquals(SqliteAffinity.INTEGER, SqliteAffinity.fromTypename("tinyint"))
    assertEquals(SqliteAffinity.INTEGER, SqliteAffinity.fromTypename("samllint"))
    assertEquals(SqliteAffinity.INTEGER, SqliteAffinity.fromTypename("mediumint"))
    assertEquals(SqliteAffinity.INTEGER, SqliteAffinity.fromTypename("bigint"))
    assertEquals(SqliteAffinity.INTEGER, SqliteAffinity.fromTypename("unsigned big int"))
    assertEquals(SqliteAffinity.INTEGER, SqliteAffinity.fromTypename("int2"))
    assertEquals(SqliteAffinity.INTEGER, SqliteAffinity.fromTypename("int8"))
    assertEquals(SqliteAffinity.INTEGER, SqliteAffinity.fromTypename("charint"))
    assertEquals(SqliteAffinity.INTEGER, SqliteAffinity.fromTypename("somethingintsomething"))

    assertEquals(SqliteAffinity.TEXT, SqliteAffinity.fromTypename("char"))
    assertEquals(SqliteAffinity.TEXT, SqliteAffinity.fromTypename("character"))
    assertEquals(SqliteAffinity.TEXT, SqliteAffinity.fromTypename("varchar"))
    assertEquals(SqliteAffinity.TEXT, SqliteAffinity.fromTypename("nchar"))
    assertEquals(SqliteAffinity.TEXT, SqliteAffinity.fromTypename("native character"))
    assertEquals(SqliteAffinity.TEXT, SqliteAffinity.fromTypename("nvarchar"))
    assertEquals(SqliteAffinity.TEXT, SqliteAffinity.fromTypename("clob"))
    assertEquals(SqliteAffinity.TEXT, SqliteAffinity.fromTypename("text"))

    assertEquals(SqliteAffinity.BLOB, SqliteAffinity.fromTypename("blob"))
    assertEquals(SqliteAffinity.BLOB, SqliteAffinity.fromTypename(""))

    assertEquals(SqliteAffinity.REAL, SqliteAffinity.fromTypename("real"))
    assertEquals(SqliteAffinity.REAL, SqliteAffinity.fromTypename("floa"))
    assertEquals(SqliteAffinity.REAL, SqliteAffinity.fromTypename("float"))
    assertEquals(SqliteAffinity.REAL, SqliteAffinity.fromTypename("doub"))
    assertEquals(SqliteAffinity.REAL, SqliteAffinity.fromTypename("double"))
    assertEquals(SqliteAffinity.REAL, SqliteAffinity.fromTypename("double precision"))

    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromTypename("numeric"))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromTypename("decimal"))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromTypename("boolean"))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromTypename("date"))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromTypename("datetime"))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromTypename("whatever"))
  }

  fun testSqliteAffinityFromJDBCType() {
    assertEquals(SqliteAffinity.INTEGER, SqliteAffinity.fromJDBCType(JDBCType.TINYINT))
    assertEquals(SqliteAffinity.INTEGER, SqliteAffinity.fromJDBCType(JDBCType.SMALLINT))
    assertEquals(SqliteAffinity.INTEGER, SqliteAffinity.fromJDBCType(JDBCType.INTEGER))
    assertEquals(SqliteAffinity.INTEGER, SqliteAffinity.fromJDBCType(JDBCType.BIGINT))

    assertEquals(SqliteAffinity.TEXT, SqliteAffinity.fromJDBCType(JDBCType.CHAR))
    assertEquals(SqliteAffinity.TEXT, SqliteAffinity.fromJDBCType(JDBCType.VARCHAR))
    assertEquals(SqliteAffinity.TEXT, SqliteAffinity.fromJDBCType(JDBCType.LONGVARCHAR))
    assertEquals(SqliteAffinity.TEXT, SqliteAffinity.fromJDBCType(JDBCType.CLOB))
    assertEquals(SqliteAffinity.TEXT, SqliteAffinity.fromJDBCType(JDBCType.NCLOB))
    assertEquals(SqliteAffinity.TEXT, SqliteAffinity.fromJDBCType(JDBCType.NVARCHAR))
    assertEquals(SqliteAffinity.TEXT, SqliteAffinity.fromJDBCType(JDBCType.LONGNVARCHAR))
    assertEquals(SqliteAffinity.TEXT, SqliteAffinity.fromJDBCType(JDBCType.NCHAR))

    assertEquals(SqliteAffinity.BLOB, SqliteAffinity.fromJDBCType(JDBCType.BLOB))

    assertEquals(SqliteAffinity.REAL, SqliteAffinity.fromJDBCType(JDBCType.FLOAT))
    assertEquals(SqliteAffinity.REAL, SqliteAffinity.fromJDBCType(JDBCType.REAL))
    assertEquals(SqliteAffinity.REAL, SqliteAffinity.fromJDBCType(JDBCType.DOUBLE))

    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.BIT))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.NUMERIC))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.DECIMAL))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.DATE))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.TIME))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.TIMESTAMP))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.BINARY))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.LONGVARBINARY))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.NULL))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.OTHER))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.JAVA_OBJECT))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.DISTINCT))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.STRUCT))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.ARRAY))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.REF))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.DATALINK))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.BOOLEAN))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.ROWID))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.SQLXML))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.REF_CURSOR))
    assertEquals(SqliteAffinity.NUMERIC, SqliteAffinity.fromJDBCType(JDBCType.TIME_WITH_TIMEZONE))
    assertEquals(
      SqliteAffinity.NUMERIC,
      SqliteAffinity.fromJDBCType(JDBCType.TIMESTAMP_WITH_TIMEZONE)
    )
  }

  fun testFileDatabaseNameAndPath() {
    val databaseId =
      SqliteDatabaseId.fromFileDatabase(
        DatabaseFileData(MockVirtualFile("someDir/data/data/com.example.package/databases/db-file"))
      )

    assertEquals("db-file", databaseId.name)
    assertEquals("/data/data/com.example.package/databases/db-file", databaseId.path)
  }

  fun testLiveDatabasePathIsConverted() {
    val databaseId =
      SqliteDatabaseId.fromLiveDatabase("/data/user/0/com.example.package/databases/db-file", 0)

    assertEquals("db-file", databaseId.name)
    assertEquals("/data/data/com.example.package/databases/db-file", databaseId.path)

    val databaseIdSdCard =
      SqliteDatabaseId.fromLiveDatabase(
        "/storage/emulated/0/com.example.package/databases/db-file",
        0
      )

    assertEquals("db-file", databaseIdSdCard.name)
    assertEquals("/sdcard/com.example.package/databases/db-file", databaseIdSdCard.path)
  }
}
