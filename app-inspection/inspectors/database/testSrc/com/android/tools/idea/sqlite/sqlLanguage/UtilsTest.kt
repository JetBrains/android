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
import com.intellij.testFramework.LightPlatformTestCase
import java.util.LinkedList
import junit.framework.TestCase

class UtilsTest : LightPlatformTestCase() {
  fun testReplaceParametersNothingIsReplaced() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = 42")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = 42", parsedSqliteStatement.statementText)
    assertEmpty(parsedSqliteStatement.parameters)
  }

  fun testReplaceParametersNamedParameter1() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = :anId")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter(":anId")), parsedSqliteStatement.parameters)
  }

  fun testReplaceParametersNamedParameters1() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where id = :anId and name = :aName"
      )

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ? and name = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(
      listOf(SqliteParameter(":anId"), SqliteParameter(":aName")),
      parsedSqliteStatement.parameters
    )
  }

  fun testReplaceParametersNamedParameter2() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = @anId")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter("@anId")), parsedSqliteStatement.parameters)
  }

  fun testReplaceParametersNamedParameters2() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where id = @anId and name = @aName"
      )

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ? and name = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(
      listOf(SqliteParameter("@anId"), SqliteParameter("@aName")),
      parsedSqliteStatement.parameters
    )
  }

  fun testReplaceParametersNamedParameter3() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = \$anId")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter("\$anId")), parsedSqliteStatement.parameters)
  }

  fun testReplaceParametersNamedParameters3() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where id = \$anId and name = \$aName"
      )

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ? and name = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(
      listOf(SqliteParameter("\$anId"), SqliteParameter("\$aName")),
      parsedSqliteStatement.parameters
    )
  }

  fun testReplaceParametersMixedNamedParameters() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where id = @anId and name = :aName and other = \$other"
      )

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals(
      "select * from Foo where id = ? and name = ? and other = ?",
      parsedSqliteStatement.statementText
    )
    TestCase.assertEquals(
      listOf(SqliteParameter("@anId"), SqliteParameter((":aName")), SqliteParameter("\$other")),
      parsedSqliteStatement.parameters
    )
  }

  fun testReplacePositionalParameter1() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = ?")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter("id")), parsedSqliteStatement.parameters)
  }

  fun testReplacePositionalParameters1() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where id = ? and name = ?"
      )

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ? and name = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(
      listOf(SqliteParameter("id"), SqliteParameter("name")),
      parsedSqliteStatement.parameters
    )
  }

  fun testReplacePositionalParameter2() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = ?1")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter("id")), parsedSqliteStatement.parameters)
  }

  fun testReplacePositionalParameters2() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where id = ?1 and name = ?2"
      )

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ? and name = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(
      listOf(SqliteParameter("id"), SqliteParameter("name")),
      parsedSqliteStatement.parameters
    )
  }

  fun testReplacePositionalParameterInComparison() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id > ?")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id > ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter("id")), parsedSqliteStatement.parameters)
  }

  fun testReplacePositionalParameterInExpressionAndComparison() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = (? >> name)")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = (? >> name)", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter("id")), parsedSqliteStatement.parameters)
  }

  fun testGetSqliteStatementType() {
    assertEquals(SqliteStatementType.SELECT, getSqliteStatementType(project, "SELECT * FROM tab"))
    assertEquals(
      SqliteStatementType.SELECT,
      getSqliteStatementType(project, "/* comment */ SELECT * FROM tab")
    )
    assertEquals(
      SqliteStatementType.SELECT,
      getSqliteStatementType(project, "SELECT /* comment */ * FROM tab")
    )
    assertEquals(
      SqliteStatementType.EXPLAIN,
      getSqliteStatementType(project, "EXPLAIN SELECT * FROM tab")
    )
    assertEquals(
      SqliteStatementType.EXPLAIN,
      getSqliteStatementType(project, "EXPLAIN /* comment */ SELECT * FROM tab")
    )
    assertEquals(
      SqliteStatementType.EXPLAIN,
      getSqliteStatementType(project, "/* comment */ EXPLAIN SELECT * FROM tab")
    )
    assertEquals(
      SqliteStatementType.UPDATE,
      getSqliteStatementType(project, "UPDATE tab SET name = 'name' WHERE id = 1")
    )
    assertEquals(
      SqliteStatementType.UPDATE,
      getSqliteStatementType(
        project,
        "UPDATE tab SET name = 'name' WHERE id IN (SELECT id FROM tab)"
      )
    )
    assertEquals(
      SqliteStatementType.DELETE,
      getSqliteStatementType(project, "DELETE FROM tab WHERE id > 0")
    )
    assertEquals(
      SqliteStatementType.DELETE,
      getSqliteStatementType(
        project,
        "DELETE FROM tab WHERE id IN (SELECT id FROM tab WHERE id > 42)"
      )
    )
    assertEquals(
      SqliteStatementType.INSERT,
      getSqliteStatementType(project, "INSERT INTO tab VALUES (42)")
    )
    assertEquals(
      SqliteStatementType.UNKNOWN,
      getSqliteStatementType(project, "SELECT * FROM t1; EXPLAIN SELECT * FROM t1;")
    )

    assertEquals(
      SqliteStatementType.PRAGMA_QUERY,
      getSqliteStatementType(project, "pragma table_info('sqlite_master')")
    )
    assertEquals(
      SqliteStatementType.PRAGMA_QUERY,
      getSqliteStatementType(project, "PRAGMA cache_size")
    )
    assertEquals(
      SqliteStatementType.PRAGMA_UPDATE,
      getSqliteStatementType(project, "PRAGMA cache_size = 2")
    )
    assertEquals(
      SqliteStatementType.UNKNOWN,
      getSqliteStatementType(project, "PRAGMA cache_size =")
    )

    assertEquals(
      SqliteStatementType.SELECT,
      getSqliteStatementType(project, "WITH one AS (SELECT 1) SELECT * FROM one")
    )
    assertEquals(
      SqliteStatementType.SELECT,
      getSqliteStatementType(
        project,
        "WITH one AS (SELECT 1), two  AS (SELECT 2) SELECT * FROM one, two"
      )
    )
    assertEquals(
      SqliteStatementType.UPDATE,
      getSqliteStatementType(project, "WITH one AS (SELECT 1) UPDATE tab SET name = 1 WHERE id = 1")
    )
    assertEquals(
      SqliteStatementType.INSERT,
      getSqliteStatementType(project, "WITH one AS (SELECT 1) INSERT INTO tab VALUES (1)")
    )
    assertEquals(
      SqliteStatementType.DELETE,
      getSqliteStatementType(
        project,
        "WITH one AS (SELECT 1) DELETE FROM tab WHERE id IN (SELECT * FROM one)"
      )
    )
    assertEquals(
      SqliteStatementType.UNKNOWN,
      getSqliteStatementType(project, "WITH one AS (SELECT 1)")
    )
    assertEquals(
      SqliteStatementType.UNKNOWN,
      getSqliteStatementType(project, "WITH one AS (SELECT 1) EXPLAIN SELECT * FROM one")
    )
  }

  fun testGetWrappableStatement() {
    assertEquals("SELECT * FROM t1", getWrappableStatement(project, "SELECT * FROM t1"))
    assertEquals("SELECT * FROM t1", getWrappableStatement(project, "SELECT * FROM t1;"))
    assertEquals(
      "SELECT * FROM t1; SELECT * FROM t2",
      getWrappableStatement(project, "SELECT * FROM t1; SELECT * FROM t2;")
    )
    assertEquals("SELECT * FROM t1 ", getWrappableStatement(project, "SELECT * FROM t1 -- comment"))
    assertEquals("SELECT * FROM t1 ", getWrappableStatement(project, "SELECT * FROM t1 --comment"))
    assertEquals("SELECT * FROM t1", getWrappableStatement(project, "SELECT * FROM t1--comment"))
    assertEquals(
      "SELECT * FROM t1 /* comment */",
      getWrappableStatement(project, "SELECT * FROM t1 /* comment */")
    )
  }

  fun testHasParsingError() {
    assertTrue(hasParsingError(project, "random string"))
    assertTrue(hasParsingError(project, "SELECT"))
    assertTrue(hasParsingError(project, "SELECT * FROM"))
    assertTrue(hasParsingError(project, "SELECT * FROM tab;;"))
    assertTrue(hasParsingError(project, "SELECT * FROM tab; SELECT * FROM tab"))
    assertTrue(hasParsingError(project, "INSERT INTO t1 VALUES ()"))
    assertTrue(hasParsingError(project, "CREATE TABLE t1"))
    assertTrue(hasParsingError(project, "SELECT * FROM tab WHERE id IN (SELECT * __error__ )"))
    assertFalse(hasParsingError(project, "SELECT * FROM tab"))
    assertFalse(hasParsingError(project, "SELECT * FROM tab;"))
    assertFalse(hasParsingError(project, "INSERT INTO t1 VALUES (42)"))
    assertFalse(hasParsingError(project, "ALTER TABLE t1 ADD COLUMN c2 int"))
    assertFalse(hasParsingError(project, "ALTER TABLE t1 ADD COLUMN c2 int"))
    assertFalse(hasParsingError(project, "ALTER TABLE t1 RENAME TO t2"))
    assertFalse(hasParsingError(project, "UPDATE t1 SET id = 42 WHERE name = 'foo'"))
    assertFalse(hasParsingError(project, "CREATE TABLE t1 (c1)"))
    assertFalse(hasParsingError(project, "DROP TABLE t1"))
    assertFalse(hasParsingError(project, "EXPLAIN SELECT * FROM t1"))
  }

  fun testInlineParameters() {
    assertEquals(
      "SELECT * FROM t1",
      inlineParameterValues(getSqliteStatement("SELECT * FROM t1"), LinkedList(emptyList()))
    )

    assertEquals(
      "SELECT * FROM t1 where id > '42'",
      inlineParameterValues(
        getSqliteStatement("SELECT * FROM t1 where id > ?"),
        LinkedList(listOf("42").toSqliteValues())
      )
    )

    assertEquals(
      "SELECT * FROM t1 where id > null",
      inlineParameterValues(
        getSqliteStatement("SELECT * FROM t1 where id > ?"),
        LinkedList(listOf(null).toSqliteValues())
      )
    )

    assertEquals(
      "SELECT * FROM t1 where id > ?",
      inlineParameterValues(
        getSqliteStatement("SELECT * FROM t1 where id > ?"),
        LinkedList(emptyList())
      )
    )
  }

  private fun getSqliteStatement(sqliteStatement: String) =
    AndroidSqlParserDefinition.parseSqlQuery(project, sqliteStatement)
}
