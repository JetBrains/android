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
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorViewsFactory
import com.android.tools.idea.sqlite.mocks.MockParametersBindingDialogView
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.toSqliteValue
import com.android.tools.idea.sqlite.toSqliteValues
import com.android.tools.idea.sqlite.ui.parametersBinding.ParametersBindingDialogView
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestCase
import org.mockito.InOrder
import org.mockito.Mockito
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify

class ParametersBindingControllerTest : PlatformTestCase() {
  private lateinit var controller: ParametersBindingController
  private lateinit var view: MockParametersBindingDialogView
  private lateinit var orderVerifier: InOrder
  private lateinit var ranStatements: MutableList<SqliteStatement>

  override fun setUp() {
    super.setUp()

    val factory = MockDatabaseInspectorViewsFactory()
    view = spy(factory.parametersBindingDialogView)
    orderVerifier = Mockito.inOrder(view)

    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar = :barVal and baz = :bazVal")

    ranStatements = mutableListOf()
    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)
  }

  fun testSetup() {
    // Prepare

    // Act
    controller.setUp()

    // Assert
    orderVerifier.verify(view).addListener(any(ParametersBindingDialogView.Listener::class.java))
    orderVerifier.verify(view).showNamedParameters(setOf(":barVal", ":bazVal"))
  }

  fun testRenamesPositionalTemplates() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar = ? and baz = ?")

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

    // Act
    controller.setUp()

    // Assert
    verify(view).showNamedParameters(setOf("bar", "baz"))
  }

  fun testRenamesPositionalTemplates2() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar = ? and baz = :paramName and p = ?")

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

    // Act
    controller.setUp()

    // Assert
    verify(view).showNamedParameters(setOf("bar", ":paramName", "p"))
  }

  fun testRenamesPositionalTemplates3() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar >> ? and baz >> ?")

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

    // Act
    controller.setUp()

    // Assert
    verify(view).showNamedParameters(setOf("param 1", "param 2"))
  }

  fun testRunStatement() {
    // Prepare

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(mapOf(":barVal" to "1", ":bazVal" to "2").toSqliteValue())

    // Assert
    assertContainsElements(ranStatements, listOf(
      SqliteStatement(
        "select * from Foo where bar = ? and baz = ?",
        listOf("1", "2").toSqliteValues(),
        "select * from Foo where bar = '1' and baz = '2'"
      )
    ))
  }

  fun testSupportsNull() {
    // Prepare

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(mapOf(":barVal" to null, ":bazVal" to "null").toSqliteValue())

    // Assert
    assertContainsElements(ranStatements, listOf(
      SqliteStatement(
        "select * from Foo where bar = ? and baz = ?",
        listOf(null, "null").toSqliteValues(),
        "select * from Foo where bar = null and baz = 'null'"
      )
    ))
  }

  fun testPositionalTemplateInsideString1() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar = '?' and baz = ?")

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(mapOf("baz" to "42").toSqliteValue())

    // Assert
    assertContainsElements(ranStatements, listOf(
      SqliteStatement(
        "select * from Foo where bar = '?' and baz = ?",
        listOf("42").toSqliteValues(),
        "select * from Foo where bar = '?' and baz = '42'"
      )))
  }

  fun testPositionalTemplateInsideString2() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar = '?1' and baz = ?")

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(mapOf("baz" to "42").toSqliteValue())

    // Assert
    assertContainsElements(ranStatements, listOf(
      SqliteStatement(
        "select * from Foo where bar = '?1' and baz = ?",
        listOf("42").toSqliteValues(),
        "select * from Foo where bar = '?1' and baz = '42'"
      )))
  }

  fun testNamedTemplateInsideString1() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar = ':bar' and baz = ?")

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(mapOf("baz" to "42").toSqliteValue())

    // Assert
    assertContainsElements(ranStatements, listOf(
      SqliteStatement(
        "select * from Foo where bar = ':bar' and baz = ?",
        listOf("42").toSqliteValues(),
        "select * from Foo where bar = ':bar' and baz = '42'"
      )))
  }

  fun testNamedTemplateInsideString2() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar = '@bar' and baz = ?")

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(mapOf("baz" to "42").toSqliteValue())

    // Assert
    assertContainsElements(ranStatements, listOf(
      SqliteStatement(
        "select * from Foo where bar = '@bar' and baz = ?",
        listOf("42").toSqliteValues(),
        "select * from Foo where bar = '@bar' and baz = '42'"
      )))
  }

  fun testNamedTemplateInsideString3() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar = '\$bar' and baz = ?")

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(mapOf("baz" to "42").toSqliteValue())

    // Assert
    assertContainsElements(ranStatements, listOf(
      SqliteStatement(
        "select * from Foo where bar = '\$bar' and baz = ?",
        listOf("42").toSqliteValues(),
        "select * from Foo where bar = '\$bar' and baz = '42'"
      )))
  }

  fun testRunStatementWithRepeatedNamedParameter() {
    // Prepare
    val psiFile = AndroidSqlParserDefinition.parseSqlQuery(project, "select * from Foo where bar = :barVal and baz = :barVal")

    controller = ParametersBindingController(view, psiFile) { ranStatements.add(it) }
    Disposer.register(project, controller)

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(mapOf(":barVal" to "1").toSqliteValue())

    // Assert
    assertContainsElements(ranStatements, listOf(
      SqliteStatement(
        "select * from Foo where bar = ? and baz = ?",
        listOf("1", "1").toSqliteValues(),
        "select * from Foo where bar = '1' and baz = '1'"
      )))
  }
}