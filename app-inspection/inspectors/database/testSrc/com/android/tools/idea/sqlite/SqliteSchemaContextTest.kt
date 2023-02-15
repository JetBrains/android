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
package com.android.tools.idea.sqlite

import com.android.tools.idea.lang.androidSql.AndroidSqlFileType
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlColumn
import com.android.tools.idea.lang.androidSql.resolution.CollectUniqueNamesProcessor
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.sqlLanguage.AndroidSqlFakePsiElement
import com.android.tools.idea.sqlite.sqlLanguage.SqliteSchemaColumn
import com.android.tools.idea.sqlite.sqlLanguage.SqliteSchemaContext
import com.android.tools.idea.sqlite.sqlLanguage.SqliteSchemaSqlType
import com.android.tools.idea.sqlite.sqlLanguage.convertToSqlTable
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.navigation.CtrlMouseHandler
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

class SqliteSchemaContextTest : JavaCodeInsightFixtureTestCase() {

  /**
   * Skips tests on Windows.
   *
   * Our current Bazel setup leaks a Disposer instance when running any editor tests on Windows,
   * which is why most targets are tagged with no_test_windows. Disabling this whole target could be
   * dangerous, in the past we had Windows-specific bugs like leaking file handles that were caught
   * by tests, so for now we disable this editor-centric test.
   */
  override fun shouldRunTest() = !SystemInfo.isWindows && super.shouldRunTest()

  override fun setUp() {
    if (shouldRunTest()) {
      super.setUp()
    }
  }

  fun testConvertSqliteTableToAndroidSqlTable() {
    val columns: List<SqliteColumn> =
      listOf(
        SqliteColumn("col1", SqliteAffinity.TEXT, true, false),
        SqliteColumn("col2", SqliteAffinity.INTEGER, true, false)
      )
    val table = SqliteTable("table", columns, null, false)

    val sqlFile = myFixture.configureByText(AndroidSqlFileType.INSTANCE, "")
    val androidSqliteSchema = table.convertToSqlTable(sqlFile)

    val columnsProcessor = CollectUniqueNamesProcessor<AndroidSqlColumn>()

    androidSqliteSchema.processColumns(columnsProcessor, HashSet())

    val androidSqlColumns = columnsProcessor.result.toList()

    assertThat(androidSqlColumns)
      .containsExactly(
        SqliteSchemaColumn(columns[0].name, sqlFile, SqliteSchemaSqlType(columns[0].affinity.name)),
        SqliteSchemaColumn(columns[1].name, sqlFile, SqliteSchemaSqlType(columns[1].affinity.name))
      )
  }

  fun testGetContextFromFile() {
    val schema = SqliteSchema(listOf(SqliteTable("User", emptyList(), null, false)))

    val sqlFile =
      myFixture.configureByText(AndroidSqlFileType.INSTANCE, "SELECT * FROM Us<caret>er")
        .virtualFile
    sqlFile.putUserData(SqliteSchemaContext.SQLITE_SCHEMA_KEY, schema)
    myFixture.configureFromExistingVirtualFile(sqlFile)

    assertThat(myFixture.elementAtCaret).isInstanceOf(AndroidSqlFakePsiElement::class.java)
    assertThat((myFixture.elementAtCaret as AndroidSqlFakePsiElement).name).isEqualTo("User")
  }

  fun testGetContextFromFileDuringCompletion() {
    val schema =
      SqliteSchema(
        listOf(
          SqliteTable(
            "User",
            listOf(SqliteColumn("name", SqliteAffinity.TEXT, true, false)),
            null,
            false
          )
        )
      )
    val sqlFile =
      myFixture.configureByText(AndroidSqlFileType.INSTANCE, "SELECT <caret> FROM User").virtualFile
    sqlFile.putUserData(SqliteSchemaContext.SQLITE_SCHEMA_KEY, schema)

    val lookupElements = myFixture.completeBasic()

    assertThat(lookupElements).hasLength(1)
    assertThat(lookupElements[0].lookupString).isEqualTo("name")
  }

  fun testProvideCorrectDescription() {
    val schema =
      SqliteSchema(
        listOf(
          SqliteTable(
            "User",
            listOf(SqliteColumn("name", SqliteAffinity.TEXT, true, false)),
            null,
            false
          )
        )
      )
    val sqlFile =
      myFixture.configureByText(AndroidSqlFileType.INSTANCE, "SELECT n<caret>ame FROM User")
        .virtualFile
    sqlFile.putUserData(SqliteSchemaContext.SQLITE_SCHEMA_KEY, schema)

    val ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)

    assertThat(ref).isNotNull()
    assertThat(CtrlMouseHandler.getInfo(ref!!.resolve(), ref.element))
      .isEqualTo("${SqliteAffinity.TEXT.name} \"name\"")
  }
}
