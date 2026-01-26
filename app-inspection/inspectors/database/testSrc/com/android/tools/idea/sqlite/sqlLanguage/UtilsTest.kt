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
package com.android.tools.idea.sqlite.sqlLanguage

import com.android.tools.idea.lang.androidSql.parser.AndroidSqlParserDefinition
import com.android.tools.idea.sqlite.controllers.SqliteParameter
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.utils.toSqliteValues
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import java.util.LinkedList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class UtilsTest {
  private val projectRule = ProjectRule()
  @get:Rule val rule = RuleChain(projectRule, EdtRule())

  private val project
    get() = projectRule.project

  @Test
  fun testReplaceParametersNothingIsReplaced() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = 42")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertThat(parsedSqliteStatement.statementText).isEqualTo("select * from Foo where id = 42")
    assertThat(parsedSqliteStatement.parameters).isEmpty()
  }

  @Test
  fun testReplaceParametersNamedParameter1() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = :anId")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertThat(parsedSqliteStatement.statementText).isEqualTo("select * from Foo where id = ?")
    assertThat(parsedSqliteStatement.parameters).containsExactly(SqliteParameter(":anId"))
  }

  @Test
  fun testReplaceParametersNamedParameters1() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where id = :anId and name = :aName",
      )

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertThat(parsedSqliteStatement.statementText)
      .isEqualTo("select * from Foo where id = ? and name = ?")
    assertThat(parsedSqliteStatement.parameters)
      .containsExactly(SqliteParameter(":anId"), SqliteParameter(":aName"))
      .inOrder()
  }

  @Test
  fun testReplaceParametersNamedParameter2() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = @anId")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertThat(parsedSqliteStatement.statementText).isEqualTo("select * from Foo where id = ?")
    assertThat(parsedSqliteStatement.parameters).containsExactly(SqliteParameter("@anId"))
  }

  @Test
  fun testReplaceParametersNamedParameters2() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where id = @anId and name = @aName",
      )

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertThat(parsedSqliteStatement.statementText)
      .isEqualTo("select * from Foo where id = ? and name = ?")
    assertThat(parsedSqliteStatement.parameters)
      .containsExactly(SqliteParameter("@anId"), SqliteParameter("@aName"))
      .inOrder()
  }

  @Test
  fun testReplaceParametersNamedParameter3() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = \$anId")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertThat(parsedSqliteStatement.statementText).isEqualTo("select * from Foo where id = ?")
    assertThat(parsedSqliteStatement.parameters).containsExactly(SqliteParameter("\$anId"))
  }

  @Test
  fun testReplaceParametersNamedParameters3() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where id = \$anId and name = \$aName",
      )

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertThat(parsedSqliteStatement.statementText)
      .isEqualTo("select * from Foo where id = ? and name = ?")
    assertThat(parsedSqliteStatement.parameters)
      .containsExactly(SqliteParameter("\$anId"), SqliteParameter("\$aName"))
      .inOrder()
  }

  @Test
  fun testReplaceParametersMixedNamedParameters() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where id = @anId and name = :aName and other = \$other",
      )

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertThat(parsedSqliteStatement.statementText)
      .isEqualTo("select * from Foo where id = ? and name = ? and other = ?")
    assertThat(parsedSqliteStatement.parameters)
      .containsExactly(
        SqliteParameter("@anId"),
        SqliteParameter((":aName")),
        SqliteParameter("\$other"),
      )
      .inOrder()
  }

  @Test
  fun testReplacePositionalParameter1() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = ?")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertThat(parsedSqliteStatement.statementText).isEqualTo("select * from Foo where id = ?")
    assertThat(parsedSqliteStatement.parameters).containsExactly(SqliteParameter("id"))
  }

  @Test
  fun testReplacePositionalParameters1() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where id = ? and name = ?",
      )

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertThat(parsedSqliteStatement.statementText)
      .isEqualTo("select * from Foo where id = ? and name = ?")
    assertThat(parsedSqliteStatement.parameters)
      .containsExactly(SqliteParameter("id"), SqliteParameter("name"))
      .inOrder()
  }

  @Test
  fun testReplacePositionalParameter2() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = ?1")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertThat(parsedSqliteStatement.statementText).isEqualTo("select * from Foo where id = ?")
    assertThat(parsedSqliteStatement.parameters).containsExactly(SqliteParameter("id"))
  }

  @Test
  fun testReplacePositionalParameters2() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where id = ?1 and name = ?2",
      )

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertThat(parsedSqliteStatement.statementText)
      .isEqualTo("select * from Foo where id = ? and name = ?")
    assertThat(parsedSqliteStatement.parameters)
      .containsExactly(SqliteParameter("id"), SqliteParameter("name"))
      .inOrder()
  }

  @Test
  fun testReplacePositionalParameterInComparison() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id > ?")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertThat(parsedSqliteStatement.statementText).isEqualTo("select * from Foo where id > ?")
    assertThat(parsedSqliteStatement.parameters).containsExactly(SqliteParameter("id"))
  }

  @Test
  fun testReplacePositionalParameterInExpressionAndComparison() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = (? >> name)")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertThat(parsedSqliteStatement.statementText)
      .isEqualTo("select * from Foo where id = (? >> name)")
    assertThat(parsedSqliteStatement.parameters).containsExactly(SqliteParameter("id"))
  }

  @Test
  fun testGetSqliteStatementType() {
    assertThat(getSqliteStatementType(project, "SELECT * FROM tab"))
      .isEqualTo(SqliteStatementType.SELECT)
    assertThat(getSqliteStatementType(project, "/* comment */ SELECT * FROM tab"))
      .isEqualTo(SqliteStatementType.SELECT)
    assertThat(getSqliteStatementType(project, "SELECT /* comment */ * FROM tab"))
      .isEqualTo(SqliteStatementType.SELECT)
    assertThat(getSqliteStatementType(project, "EXPLAIN SELECT * FROM tab"))
      .isEqualTo(SqliteStatementType.EXPLAIN)
    assertThat(getSqliteStatementType(project, "EXPLAIN /* comment */ SELECT * FROM tab"))
      .isEqualTo(SqliteStatementType.EXPLAIN)
    assertThat(getSqliteStatementType(project, "/* comment */ EXPLAIN SELECT * FROM tab"))
      .isEqualTo(SqliteStatementType.EXPLAIN)
    assertThat(getSqliteStatementType(project, "UPDATE tab SET name = 'name' WHERE id = 1"))
      .isEqualTo(SqliteStatementType.UPDATE)
    assertThat(
        getSqliteStatementType(
          project,
          "UPDATE tab SET name = 'name' WHERE id IN (SELECT id FROM tab)",
        )
      )
      .isEqualTo(SqliteStatementType.UPDATE)
    assertThat(getSqliteStatementType(project, "DELETE FROM tab WHERE id > 0"))
      .isEqualTo(SqliteStatementType.DELETE)
    assertThat(
        getSqliteStatementType(
          project,
          "DELETE FROM tab WHERE id IN (SELECT id FROM tab WHERE id > 42)",
        )
      )
      .isEqualTo(SqliteStatementType.DELETE)
    assertThat(getSqliteStatementType(project, "INSERT INTO tab VALUES (42)"))
      .isEqualTo(SqliteStatementType.INSERT)
    assertThat(getSqliteStatementType(project, "SELECT * FROM t1; EXPLAIN SELECT * FROM t1;"))
      .isEqualTo(SqliteStatementType.UNKNOWN)

    assertThat(getSqliteStatementType(project, "pragma table_info('sqlite_master')"))
      .isEqualTo(SqliteStatementType.PRAGMA_QUERY)
    assertThat(getSqliteStatementType(project, "PRAGMA cache_size"))
      .isEqualTo(SqliteStatementType.PRAGMA_QUERY)
    assertThat(getSqliteStatementType(project, "PRAGMA cache_size = 2"))
      .isEqualTo(SqliteStatementType.PRAGMA_UPDATE)
    assertThat(getSqliteStatementType(project, "PRAGMA cache_size ="))
      .isEqualTo(SqliteStatementType.UNKNOWN)

    assertThat(getSqliteStatementType(project, "WITH one AS (SELECT 1) SELECT * FROM one"))
      .isEqualTo(SqliteStatementType.SELECT)
    assertThat(
        getSqliteStatementType(
          project,
          "WITH one AS (SELECT 1), two  AS (SELECT 2) SELECT * FROM one, two",
        )
      )
      .isEqualTo(SqliteStatementType.SELECT)
    assertThat(
        getSqliteStatementType(
          project,
          "WITH one AS (SELECT 1) UPDATE tab SET name = 1 WHERE id = 1",
        )
      )
      .isEqualTo(SqliteStatementType.UPDATE)
    assertThat(getSqliteStatementType(project, "WITH one AS (SELECT 1) INSERT INTO tab VALUES (1)"))
      .isEqualTo(SqliteStatementType.INSERT)
    assertThat(
        getSqliteStatementType(
          project,
          "WITH one AS (SELECT 1) DELETE FROM tab WHERE id IN (SELECT * FROM one)",
        )
      )
      .isEqualTo(SqliteStatementType.DELETE)
    assertThat(getSqliteStatementType(project, "WITH one AS (SELECT 1)"))
      .isEqualTo(SqliteStatementType.UNKNOWN)
    assertThat(getSqliteStatementType(project, "WITH one AS (SELECT 1) EXPLAIN SELECT * FROM one"))
      .isEqualTo(SqliteStatementType.UNKNOWN)
  }

  @Test
  fun testGetWrappableStatement() {
    assertThat(getWrappableStatement(project, "SELECT * FROM t1")).isEqualTo("SELECT * FROM t1")
    assertThat(getWrappableStatement(project, "SELECT * FROM t1;")).isEqualTo("SELECT * FROM t1")
    assertThat(getWrappableStatement(project, "SELECT * FROM t1; SELECT * FROM t2;"))
      .isEqualTo("SELECT * FROM t1; SELECT * FROM t2")
    assertThat(getWrappableStatement(project, "SELECT * FROM t1 -- comment"))
      .isEqualTo("SELECT * FROM t1 ")
    assertThat(getWrappableStatement(project, "SELECT * FROM t1 --comment"))
      .isEqualTo("SELECT * FROM t1 ")
    assertThat(getWrappableStatement(project, "SELECT * FROM t1--comment"))
      .isEqualTo("SELECT * FROM t1")
    assertThat(getWrappableStatement(project, "SELECT * FROM t1 /* comment */"))
      .isEqualTo("SELECT * FROM t1 /* comment */")
  }

  @Test
  fun testHasParsingError() {
    assertThat(hasParsingError(project, "random string")).isTrue()
    assertThat(hasParsingError(project, "SELECT")).isTrue()
    assertThat(hasParsingError(project, "SELECT * FROM")).isTrue()
    assertThat(hasParsingError(project, "SELECT * FROM tab;;")).isTrue()
    assertThat(hasParsingError(project, "SELECT * FROM tab; SELECT * FROM tab")).isTrue()
    assertThat(hasParsingError(project, "INSERT INTO t1 VALUES ()")).isTrue()
    assertThat(hasParsingError(project, "CREATE TABLE t1")).isTrue()
    assertThat(hasParsingError(project, "SELECT * FROM tab WHERE id IN (SELECT * __error__ )"))
      .isTrue()
    assertThat(hasParsingError(project, "SELECT * FROM tab")).isFalse()
    assertThat(hasParsingError(project, "SELECT * FROM tab;")).isFalse()
    assertThat(hasParsingError(project, "INSERT INTO t1 VALUES (42)")).isFalse()
    assertThat(hasParsingError(project, "ALTER TABLE t1 ADD COLUMN c2 int")).isFalse()
    assertThat(hasParsingError(project, "ALTER TABLE t1 ADD COLUMN c2 int")).isFalse()
    assertThat(hasParsingError(project, "ALTER TABLE t1 RENAME TO t2")).isFalse()
    assertThat(hasParsingError(project, "UPDATE t1 SET id = 42 WHERE name = 'foo'")).isFalse()
    assertThat(hasParsingError(project, "CREATE TABLE t1 (c1)")).isFalse()
    assertThat(hasParsingError(project, "DROP TABLE t1")).isFalse()
    assertThat(hasParsingError(project, "EXPLAIN SELECT * FROM t1")).isFalse()
  }

  @Test
  fun testInlineParameters() {
    assertThat(
        inlineParameterValues(getSqliteStatement("SELECT * FROM t1"), LinkedList(emptyList()))
      )
      .isEqualTo("SELECT * FROM t1")

    assertThat(
        inlineParameterValues(
          getSqliteStatement("SELECT * FROM t1 where id > ?"),
          LinkedList(listOf("42").toSqliteValues()),
        )
      )
      .isEqualTo("SELECT * FROM t1 where id > '42'")

    assertThat(
        inlineParameterValues(
          getSqliteStatement("SELECT * FROM t1 where id > ?"),
          LinkedList(listOf(null).toSqliteValues()),
        )
      )
      .isEqualTo("SELECT * FROM t1 where id > null")

    assertThat(
        inlineParameterValues(
          getSqliteStatement("SELECT * FROM t1 where id > ?"),
          LinkedList(emptyList()),
        )
      )
      .isEqualTo("SELECT * FROM t1 where id > ?")
  }

  private fun getSqliteStatement(sqliteStatement: String) =
    AndroidSqlParserDefinition.parseSqlQuery(project, sqliteStatement)
}
