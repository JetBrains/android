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
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase

class UtilsTest : LightPlatformTestCase() {
  fun testReplaceParametersNothingIsReplaced() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = 42")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = 42", parsedSqliteStatement.statementText)
    assertEmpty(parsedSqliteStatement.parameters)
  }

  fun testReplaceParametersNamedParameter1() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = :anId")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter(":anId")), parsedSqliteStatement.parameters)
  }

  fun testReplaceParametersNamedParameters1() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = :anId and name = :aName")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ? and name = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter(":anId"), SqliteParameter(":aName")), parsedSqliteStatement.parameters)
  }

  fun testReplaceParametersNamedParameter2() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = @anId")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter("@anId")), parsedSqliteStatement.parameters)
  }

  fun testReplaceParametersNamedParameters2() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = @anId and name = @aName")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ? and name = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter("@anId"), SqliteParameter("@aName")), parsedSqliteStatement.parameters)
  }

  fun testReplaceParametersNamedParameter3() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = \$anId")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter("\$anId")), parsedSqliteStatement.parameters)
  }

  fun testReplaceParametersNamedParameters3() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = \$anId and name = \$aName")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ? and name = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter("\$anId"), SqliteParameter("\$aName")), parsedSqliteStatement.parameters)
  }

  fun testReplaceParametersMixedNamedParameters() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(
      project,
      "select * from Foo where id = @anId and name = :aName and other = \$other"
    )

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ? and name = ? and other = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(
      listOf(SqliteParameter("@anId"), SqliteParameter((":aName")), SqliteParameter("\$other")),
      parsedSqliteStatement.parameters
    )
  }

  fun testReplacePositionalParameter1() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = ?")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter("id")), parsedSqliteStatement.parameters)
  }

  fun testReplacePositionalParameters1() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = ? and name = ?")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ? and name = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter("id"), SqliteParameter("name")), parsedSqliteStatement.parameters)
  }

  fun testReplacePositionalParameter2() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = ?1")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter("id")), parsedSqliteStatement.parameters)
  }

  fun testReplacePositionalParameters2() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = ?1 and name = ?2")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = ? and name = ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter("id"), SqliteParameter("name")), parsedSqliteStatement.parameters)
  }

  fun testReplacePositionalParameterInComparison() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id > ?")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id > ?", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter("id")), parsedSqliteStatement.parameters)
  }

  fun testReplacePositionalParameterInExpressionAndComparison() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where id = (? >> name)")

    // Act
    val parsedSqliteStatement = replaceNamedParametersWithPositionalParameters(psiFile)

    // Assert
    assertEquals("select * from Foo where id = (? >> name)", parsedSqliteStatement.statementText)
    TestCase.assertEquals(listOf(SqliteParameter("id")), parsedSqliteStatement.parameters)
  }
}