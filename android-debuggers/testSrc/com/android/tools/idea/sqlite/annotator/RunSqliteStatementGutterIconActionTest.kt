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
package com.android.tools.idea.sqlite.annotator

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.tools.idea.sqlite.DatabaseInspectorProjectService
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorViewsFactory
import com.android.tools.idea.sqlite.mocks.MockPopupChooserBuilder
import com.android.tools.idea.sqlite.model.LiveSqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.caret
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.ui.awt.RelativePoint
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseEvent

class RunSqliteStatementGutterIconActionTest : LightJavaCodeInsightFixtureTestCase() {
  private lateinit var ideComponents: IdeComponents
  private lateinit var mockDatabaseInspectorProjectService: DatabaseInspectorProjectService
  private lateinit var sqliteDatabase1: SqliteDatabase
  private lateinit var sqliteDatabase2: SqliteDatabase
  private lateinit var anActionEvent: AnActionEvent
  private lateinit var mouseEvent: MouseEvent
  private lateinit var anAction: RunSqliteStatementGutterIconAction
  private lateinit var viewFactory: MockDatabaseInspectorViewsFactory

  override fun setUp() {
    super.setUp()

    sqliteDatabase1 = LiveSqliteDatabase("db1", mock(DatabaseConnection::class.java))
    sqliteDatabase2 = LiveSqliteDatabase("db2", mock(DatabaseConnection::class.java))

    ideComponents = IdeComponents(myFixture)
    mockDatabaseInspectorProjectService = ideComponents.mockProjectService(DatabaseInspectorProjectService::class.java)

    mouseEvent = mock(MouseEvent::class.java)
    `when`(mouseEvent.component).thenReturn(mock(Component::class.java))
    `when`(mouseEvent.point).thenReturn(Point(0, 0))

    viewFactory = MockDatabaseInspectorViewsFactory()
  }

  fun testDoNothingWhenDatabaseIsNotOpen() {
    // Prepare
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(false)

    buildAction("select * from Foo")

    // Act
    anAction.actionPerformed(anActionEvent)

    // Assert
    verify(mockDatabaseInspectorProjectService, times(0)).runSqliteStatement(eq(sqliteDatabase1), any(SqliteStatement::class.java))
  }

  fun testRunSqliteStatementWhenDatabaseIsOpen() {
    // Prepare
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockDatabaseInspectorProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo")

    // Act
    anAction.actionPerformed(anActionEvent)

    // Assert
    verify(mockDatabaseInspectorProjectService).runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo"))
  }

  fun testMultipleDatabaseShowsPopUp() {
    // Prepare
    val databases = sortedSetOf(Comparator.comparing { database: SqliteDatabase -> database.name }, sqliteDatabase1, sqliteDatabase2)
    val mockJBPopupFactory = ideComponents.mockApplicationService(JBPopupFactory::class.java)
    val spyPopupChooserBuilder = spy(MockPopupChooserBuilder::class.java)
    `when`(mockJBPopupFactory.createPopupChooserBuilder(databases.toList())).thenReturn(spyPopupChooserBuilder)

    `when`(mockDatabaseInspectorProjectService.getOpenDatabases()).thenReturn(databases)
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)

    buildAction("select * from Foo")

    // Act
    anAction.actionPerformed(anActionEvent)
    spyPopupChooserBuilder.callback?.consume(sqliteDatabase1)

    // Assert
    assertNotNull(spyPopupChooserBuilder.callback)

    verify(mockJBPopupFactory).createPopupChooserBuilder(databases.toList())
    verify(spyPopupChooserBuilder).createPopup()
    verify(spyPopupChooserBuilder.mockPopUp).show(any(RelativePoint::class.java))
    verify(mockDatabaseInspectorProjectService).runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo"))
  }

  fun testSqlStatementWithNoPositionalParameters() {
    // Prepare
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockDatabaseInspectorProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo where id = 42")

    // Act
    anAction.actionPerformed(anActionEvent)

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo where id = 42", emptyList()))
  }

  fun testSqlStatementContainsPositionalParameters() {
    // Prepare
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockDatabaseInspectorProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo where id = ?")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(mapOf("id" to "1"))

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo where id = ?", listOf("1")))
  }

  fun testSqlStatementContainsMultiplePositionalParameters() {
    // Prepare
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockDatabaseInspectorProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo where id = ? and name = ?")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(mapOf("id" to "1", "name" to "name"))

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo where id = ? and name = ?", listOf("1", "name")))
  }

  fun testSqlStatementContainsMultiplePositionalNumberedParameters() {
    // Prepare
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockDatabaseInspectorProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo where id = ?1 and name = ?2")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(mapOf("id" to "1", "name" to "name"))

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo where id = ? and name = ?", listOf("1", "name")))
  }

  fun testSqlStatementContainsPositionalParametersInComparison() {
    // Prepare
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockDatabaseInspectorProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo where id > ?")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(mapOf("id" to "1"))

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo where id > ?", listOf("1")))
  }

  fun testSqlStatementContainsPositionalParametersInExpressionAndComparison() {
    // Prepare
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockDatabaseInspectorProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo where id = (? >> name)")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(mapOf("id" to "1"))

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo where id = (? >> name)", listOf("1")))
  }

  fun testSqlStatementContainsNamedParameters1() {
    // Prepare
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockDatabaseInspectorProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo where id = :anId")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(mapOf(":anId" to "1"))

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo where id = ?", listOf("1")))
  }

  fun testSqlStatementContainsMultipleNamedParameters() {
    // Prepare
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockDatabaseInspectorProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo where id = :anId and name = :aName")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(mapOf(":anId" to "1", ":aName" to "name"))

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo where id = ? and name = ?", listOf("1", "name")))
  }

  fun testSqlStatementContainsNamedParameters2() {
    // Prepare
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockDatabaseInspectorProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo where id = ?1")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(mapOf("id" to "1"))

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo where id = ?", listOf("1")))
  }

  fun testSqlStatementContainsNamedParameters3() {
    // Prepare
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockDatabaseInspectorProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo where id = ?")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(mapOf("id" to "1"))

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo where id = ?", listOf("1")))
  }

  fun testSqlStatementContainsNamedParameters4() {
    // Prepare
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockDatabaseInspectorProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo where id = @anId")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(mapOf("@anId" to "1"))

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo where id = ?", listOf("1")))
  }

  fun testSqlStatementContainsNamedParameters5() {
    // Prepare
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockDatabaseInspectorProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo where id = \$anId")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(mapOf("\$anId" to "1"))

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo where id = ?", listOf("1")))
  }

  private fun buildAction(sqlStatement: String) {
    myFixture.configureByText(
      StdFileTypes.JAVA,
      // language=java
      """
        package com.example;
        class Foo {
          void bar() {
            // language=RoomSql
            String query = $caret"$sqlStatement";
          }
        }
        """.trimIndent()
    )

    val hostElement = myFixture.file.findElementAt(myFixture.caretOffset)!!.parent

    anAction = RunSqliteStatementGutterIconAction(hostElement.project, hostElement, viewFactory)
    anActionEvent = TestActionEvent.createFromAnAction(anAction, mouseEvent, "", DataContext.EMPTY_CONTEXT)
  }
}