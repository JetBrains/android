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
import com.android.tools.idea.sqlite.mocks.FakeParametersBindingDialogView
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.ui.parametersBinding.ParametersBindingDialogView
import com.android.tools.idea.sqlite.utils.toSqliteValues
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import org.mockito.InOrder
import org.mockito.Mockito
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify

class ParametersBindingControllerTest : LightPlatformTestCase() {
  private lateinit var controller: ParametersBindingController
  private lateinit var view: FakeParametersBindingDialogView
  private lateinit var orderVerifier: InOrder
  private lateinit var ranStatements: MutableList<SqliteStatement>

  override fun setUp() {
    super.setUp()

    val databaseInspectorViewsFactory = FakeDatabaseInspectorViewsFactory()
    view = spy(databaseInspectorViewsFactory.parametersBindingDialogView)
    orderVerifier = Mockito.inOrder(view)

    ranStatements = mutableListOf()
  }

  fun testSetup() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = :barVal and baz = :bazVal"
      )
    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

    // Act
    controller.setUp()

    // Assert
    orderVerifier.verify(view).addListener(any(ParametersBindingDialogView.Listener::class.java))
    orderVerifier
      .verify(view)
      .showNamedParameters(setOf(SqliteParameter(":barVal"), SqliteParameter(":bazVal")))
  }

  fun testRenamesPositionalTemplates() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = ? and baz = ?"
      )

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

    // Act
    controller.setUp()

    // Assert
    verify(view).showNamedParameters(setOf(SqliteParameter("bar"), SqliteParameter("baz")))
  }

  fun testRenamesPositionalTemplates2() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = ? and baz = :paramName and p = ?"
      )

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

    // Act
    controller.setUp()

    // Assert
    verify(view)
      .showNamedParameters(
        setOf(SqliteParameter("bar"), SqliteParameter(":paramName"), SqliteParameter("p"))
      )
  }

  fun testRenamesPositionalTemplates3() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar >> ? and baz >> ?"
      )

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

    // Act
    controller.setUp()

    // Assert
    verify(view).showNamedParameters(setOf(SqliteParameter("param 1"), SqliteParameter("param 2")))
  }

  fun testRunStatement() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = :barVal and baz = :bazVal"
      )
    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(
        SqliteParameter(":barVal") to SqliteParameterValue.fromAny("1"),
        SqliteParameter(":bazVal") to SqliteParameterValue.fromAny("2")
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
          "select * from Foo where bar = '1' and baz = '2'"
        )
      )
    )
  }

  fun testSupportsNull() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = :barVal and baz = :bazVal"
      )
    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(
        SqliteParameter(":barVal") to SqliteParameterValue.fromAny(null),
        SqliteParameter(":bazVal") to SqliteParameterValue.fromAny("null")
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
          "select * from Foo where bar = null and baz = 'null'"
        )
      )
    )
  }

  fun testPositionalTemplateInsideString1() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = '?' and baz = ?"
      )

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

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
          "select * from Foo where bar = '?' and baz = '42'"
        )
      )
    )
  }

  fun testPositionalTemplateInsideString2() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = '?1' and baz = ?"
      )

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

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
          "select * from Foo where bar = '?1' and baz = '42'"
        )
      )
    )
  }

  fun testNamedTemplateInsideString1() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = ':bar' and baz = ?"
      )

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

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
          "select * from Foo where bar = ':bar' and baz = '42'"
        )
      )
    )
  }

  fun testNamedTemplateInsideString2() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = '@bar' and baz = ?"
      )

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

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
          "select * from Foo where bar = '@bar' and baz = '42'"
        )
      )
    )
  }

  fun testNamedTemplateInsideString3() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = '\$bar' and baz = ?"
      )

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

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
          "select * from Foo where bar = '\$bar' and baz = '42'"
        )
      )
    )
  }

  fun testBindPositionalParameter1ToCollection() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar in (?)")

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

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
          "select * from Foo where bar in ('1', '2', '3')"
        )
      )
    )
  }

  fun testBindPositionalParameter2ToCollection() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar in (\$barVal)")

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

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
          "select * from Foo where bar in ('1', '2', '3')"
        )
      )
    )
  }

  fun testBindPositionalParameter3ToCollection() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar in (?1)")

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

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
          "select * from Foo where bar in ('1', '2', '3')"
        )
      )
    )
  }

  fun testBindPositionalParameter4ToCollection() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar in (@barVal)")

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

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
          "select * from Foo where bar in ('1', '2', '3')"
        )
      )
    )
  }

  fun testBindNamedParameterToCollection() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar in (:barVal)")

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

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
          "select * from Foo where bar in ('1', '2', '3')"
        )
      )
    )
  }

  fun testComplexInStatement() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from foo where bar in (select id from baz where bax > ?)"
      )

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

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
          "select * from foo where bar in (select id from baz where bax > '1')"
        )
      )
    )
  }

  fun testRunStatementWithRepeatedNamedParameter() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(
        project,
        "select * from Foo where bar = :barVal and baz = :barVal"
      )

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

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
          "select * from Foo where bar = '1' and baz = '1'"
        )
      )
    )
  }

  fun testParameterStringValueHasRightInlinedFormat1() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar = ?")

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

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
          "select * from Foo where bar = 'te''st'"
        )
      )
    )
  }

  fun testParameterStringValueHasRightInlinedFormat2() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar = ?")

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

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
          "select * from Foo where bar = '''test'''"
        )
      )
    )
  }

  fun testParameterStringValueHasRightInlinedFormat3() {
    // Prepare
    val psiFile =
      AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar = ?")

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

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
          "select * from Foo where bar = '\"test\"'"
        )
      )
    )
  }
}
