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
package com.android.tools.idea.sqlite.controllers

import com.android.testutils.MockitoKt.any
import com.android.tools.idea.lang.androidSql.parser.AndroidSqlParserDefinition
import com.android.tools.idea.sqlite.mocks.FakeDatabaseInspectorViewsFactory
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.ui.parametersBinding.ParametersBindingDialogView
import com.android.tools.idea.sqlite.utils.toSqliteValues
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito
import org.mockito.Mockito.verify

@RunWith(JUnit4::class)
@RunsInEdt
class ParametersBindingControllerTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule val rule = RuleChain(projectRule, disposableRule, EdtRule())

  private val project
    get() = projectRule.project

  private val disposable
    get() = disposableRule.disposable

  private val view = FakeDatabaseInspectorViewsFactory().parametersBindingDialogView
  private val orderVerifier = Mockito.inOrder(view)
  private val ranStatements = mutableListOf<SqliteStatement>()

  @Test
  fun testSetup() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = :barVal and baz = :bazVal",
      )
    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()

    // Assert
    orderVerifier.verify(view).addListener(any<ParametersBindingDialogView.Listener>())
    orderVerifier
      .verify(view)
      .showNamedParameters(setOf(SqliteParameter(":barVal"), SqliteParameter(":bazVal")))
  }

  @Test
  fun testRenamesPositionalTemplates() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = ? and baz = ?",
      )

    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()

    // Assert
    verify(view).showNamedParameters(setOf(SqliteParameter("bar"), SqliteParameter("baz")))
  }

  @Test
  fun testRenamesPositionalTemplates2() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = ? and baz = :paramName and p = ?",
      )

    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()

    // Assert
    verify(view)
      .showNamedParameters(
        setOf(SqliteParameter("bar"), SqliteParameter(":paramName"), SqliteParameter("p"))
      )
  }

  @Test
  fun testRenamesPositionalTemplates3() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar >> ? and baz >> ?",
      )

    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()

    // Assert
    verify(view).showNamedParameters(setOf(SqliteParameter("param 1"), SqliteParameter("param 2")))
  }

  @Test
  fun testRunStatement() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = :barVal and baz = :bazVal",
      )
    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(
        SqliteParameter(":barVal") to SqliteParameterValue.fromAny("1"),
        SqliteParameter(":bazVal") to SqliteParameterValue.fromAny("2"),
      )
    )

    // Assert
    assertContainsElements(
      ranStatements,
      listOf(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where bar = ? and baz = ?",
          listOf("1", "2").toSqliteValues(),
          "select * from Foo where bar = '1' and baz = '2'",
        )
      ),
    )
  }

  @Test
  fun testSupportsNull() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = :barVal and baz = :bazVal",
      )
    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(
        SqliteParameter(":barVal") to SqliteParameterValue.fromAny(null),
        SqliteParameter(":bazVal") to SqliteParameterValue.fromAny("null"),
      )
    )

    // Assert
    assertContainsElements(
      ranStatements,
      listOf(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where bar = ? and baz = ?",
          listOf(null, "null").toSqliteValues(),
          "select * from Foo where bar = null and baz = 'null'",
        )
      ),
    )
  }

  @Test
  fun testPositionalTemplateInsideString1() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = '?' and baz = ?",
      )

    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("baz") to SqliteParameterValue.fromAny("42"))
    )

    // Assert
    assertContainsElements(
      ranStatements,
      listOf(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where bar = '?' and baz = ?",
          listOf("42").toSqliteValues(),
          "select * from Foo where bar = '?' and baz = '42'",
        )
      ),
    )
  }

  @Test
  fun testPositionalTemplateInsideString2() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = '?1' and baz = ?",
      )

    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("baz") to SqliteParameterValue.fromAny("42"))
    )

    // Assert
    assertContainsElements(
      ranStatements,
      listOf(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where bar = '?1' and baz = ?",
          listOf("42").toSqliteValues(),
          "select * from Foo where bar = '?1' and baz = '42'",
        )
      ),
    )
  }

  @Test
  fun testNamedTemplateInsideString1() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = ':bar' and baz = ?",
      )

    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("baz") to SqliteParameterValue.fromAny("42"))
    )

    // Assert
    assertContainsElements(
      ranStatements,
      listOf(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where bar = ':bar' and baz = ?",
          listOf("42").toSqliteValues(),
          "select * from Foo where bar = ':bar' and baz = '42'",
        )
      ),
    )
  }

  @Test
  fun testNamedTemplateInsideString2() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = '@bar' and baz = ?",
      )

    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("baz") to SqliteParameterValue.fromAny("42"))
    )

    // Assert
    assertContainsElements(
      ranStatements,
      listOf(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where bar = '@bar' and baz = ?",
          listOf("42").toSqliteValues(),
          "select * from Foo where bar = '@bar' and baz = '42'",
        )
      ),
    )
  }

  @Test
  fun testNamedTemplateInsideString3() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = '\$bar' and baz = ?",
      )

    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("baz") to SqliteParameterValue.fromAny("42"))
    )

    // Assert
    assertContainsElements(
      ranStatements,
      listOf(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where bar = '\$bar' and baz = ?",
          listOf("42").toSqliteValues(),
          "select * from Foo where bar = '\$bar' and baz = '42'",
        )
      ),
    )
  }

  @Test
  fun testBindPositionalParameter1ToCollection() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar in (?)")

    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("param 1", true) to SqliteParameterValue.fromAny("1", "2", "3"))
    )

    // Assert
    assertContainsElements(
      ranStatements,
      listOf(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where bar in (?, ?, ?)",
          listOf("1", "2", "3").toSqliteValues(),
          "select * from Foo where bar in ('1', '2', '3')",
        )
      ),
    )
  }

  @Test
  fun testBindPositionalParameter2ToCollection() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar in (\$barVal)")

    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("\$barVal", true) to SqliteParameterValue.fromAny("1", "2", "3"))
    )

    // Assert
    assertContainsElements(
      ranStatements,
      listOf(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where bar in (?, ?, ?)",
          listOf("1", "2", "3").toSqliteValues(),
          "select * from Foo where bar in ('1', '2', '3')",
        )
      ),
    )
  }

  @Test
  fun testBindPositionalParameter3ToCollection() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar in (?1)")

    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("param 1", true) to SqliteParameterValue.fromAny("1", "2", "3"))
    )

    // Assert
    assertContainsElements(
      ranStatements,
      listOf(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where bar in (?, ?, ?)",
          listOf("1", "2", "3").toSqliteValues(),
          "select * from Foo where bar in ('1', '2', '3')",
        )
      ),
    )
  }

  @Test
  fun testBindPositionalParameter4ToCollection() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar in (@barVal)")

    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("@barVal", true) to SqliteParameterValue.fromAny("1", "2", "3"))
    )

    // Assert
    assertContainsElements(
      ranStatements,
      listOf(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where bar in (?, ?, ?)",
          listOf("1", "2", "3").toSqliteValues(),
          "select * from Foo where bar in ('1', '2', '3')",
        )
      ),
    )
  }

  @Test
  fun testBindNamedParameterToCollection() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar in (:barVal)")

    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter(":barVal", true) to SqliteParameterValue.fromAny("1", "2", "3"))
    )

    // Assert
    assertContainsElements(
      ranStatements,
      listOf(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where bar in (?, ?, ?)",
          listOf("1", "2", "3").toSqliteValues(),
          "select * from Foo where bar in ('1', '2', '3')",
        )
      ),
    )
  }

  @Test
  fun testComplexInStatement() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from foo where bar in (select id from baz where bax > ?)",
      )

    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("bax", false) to SqliteParameterValue.fromAny("1"))
    )

    // Assert
    assertContainsElements(
      ranStatements,
      listOf(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from foo where bar in (select id from baz where bax > ?)",
          listOf("1").toSqliteValues(),
          "select * from foo where bar in (select id from baz where bax > '1')",
        )
      ),
    )
  }

  @Test
  fun testRunStatementWithRepeatedNamedParameter() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = :barVal and baz = :barVal",
      )

    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter(":barVal") to SqliteParameterValue.fromAny("1"))
    )

    // Assert
    assertContainsElements(
      ranStatements,
      listOf(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where bar = ? and baz = ?",
          listOf("1", "1").toSqliteValues(),
          "select * from Foo where bar = '1' and baz = '1'",
        )
      ),
    )
  }

  @Test
  fun testParameterStringValueHasRightInlinedFormat1() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar = ?")

    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("bar") to SqliteParameterValue.fromAny("te'st"))
    )

    // Assert
    assertContainsElements(
      ranStatements,
      listOf(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where bar = ?",
          listOf("te'st").toSqliteValues(),
          "select * from Foo where bar = 'te''st'",
        )
      ),
    )
  }

  @Test
  fun testParameterStringValueHasRightInlinedFormat2() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar = ?")

    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("bar") to SqliteParameterValue.fromAny("'test'"))
    )

    // Assert
    assertContainsElements(
      ranStatements,
      listOf(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where bar = ?",
          listOf("'test'").toSqliteValues(),
          "select * from Foo where bar = '''test'''",
        )
      ),
    )
  }

  @Test
  fun testParameterStringValueHasRightInlinedFormat3() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar = ?")

    val controller = parametersBindingController(view, psiFile) { ranStatements.add(it) }

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("bar") to SqliteParameterValue.fromAny("\"test\""))
    )

    // Assert
    assertContainsElements(
      ranStatements,
      listOf(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where bar = ?",
          listOf("\"test\"").toSqliteValues(),
          "select * from Foo where bar = '\"test\"'",
        )
      ),
    )
  }

  private fun parametersBindingController(
    view: ParametersBindingDialogView,
    sqliteStatementPsi: PsiElement,
    runStatement: (SqliteStatement) -> Unit,
  ) =
    ParametersBindingController(view, sqliteStatementPsi, runStatement).also {
      Disposer.register(disposable, it)
    }
}
