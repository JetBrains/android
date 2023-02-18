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

import junit.framework.TestCase

class UtilsTest : TestCase() {
  fun testGetRowIdIsNullIfIntPrimaryKeyExists() {
    // Prepare
    val columns =
      listOf(
        SqliteColumn("col1", SqliteAffinity.INTEGER, true, true),
        SqliteColumn("col2", SqliteAffinity.INTEGER, true, false),
        SqliteColumn("rowid", SqliteAffinity.INTEGER, false, false)
      )

    // Act
    val rowIdName = getRowIdName(columns)

    // Assert
    assertNull(rowIdName)
  }

  fun testGetRowIdIs_rowid_() {
    // Prepare
    val columns =
      listOf(
        SqliteColumn("col1", SqliteAffinity.INTEGER, true, false),
        SqliteColumn("col2", SqliteAffinity.INTEGER, true, false),
        SqliteColumn("rowid", SqliteAffinity.INTEGER, false, false)
      )

    // Act
    val rowIdName = getRowIdName(columns)

    // Assert
    assertEquals(RowIdName._ROWID_, rowIdName)
  }

  fun testGetRowIdIsRowid() {
    // Prepare
    val columns =
      listOf(
        SqliteColumn("col1", SqliteAffinity.INTEGER, true, false),
        SqliteColumn("col2", SqliteAffinity.INTEGER, true, false),
        SqliteColumn("_rowid_", SqliteAffinity.INTEGER, false, false)
      )

    // Act
    val rowIdName = getRowIdName(columns)

    // Assert
    assertEquals(RowIdName.ROWID, rowIdName)
  }

  fun testGetRowIdIsOid() {
    // Prepare
    val columns =
      listOf(
        SqliteColumn("col1", SqliteAffinity.INTEGER, true, false),
        SqliteColumn("col2", SqliteAffinity.INTEGER, true, false),
        SqliteColumn("_rowid_", SqliteAffinity.INTEGER, false, false),
        SqliteColumn("rowid", SqliteAffinity.INTEGER, false, false)
      )

    // Act
    val rowIdName = getRowIdName(columns)

    // Assert
    assertEquals(RowIdName.OID, rowIdName)
  }

  fun testGetRowIdIsNull() {
    // Prepare
    val columns =
      listOf(
        SqliteColumn("col1", SqliteAffinity.INTEGER, true, false),
        SqliteColumn("col2", SqliteAffinity.INTEGER, true, false),
        SqliteColumn("_rowid_", SqliteAffinity.INTEGER, false, false),
        SqliteColumn("rowid", SqliteAffinity.INTEGER, false, false),
        SqliteColumn("oid", SqliteAffinity.INTEGER, false, false)
      )

    // Act
    val rowIdName = getRowIdName(columns)

    // Assert
    assertNull(rowIdName)
  }
}
