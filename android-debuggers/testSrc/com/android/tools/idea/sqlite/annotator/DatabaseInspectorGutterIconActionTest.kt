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
import com.android.tools.idea.DatabaseInspectorGutterIconAction
import com.android.tools.idea.sqlite.SqliteService
import com.android.tools.idea.sqlite.mocks.MockPopupChooserBuilder
import com.android.tools.idea.sqlite.mocks.MockSqliteEditorViewFactory
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqliteExplorer.SqliteExplorerProjectService
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.caret
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
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

class DatabaseInspectorGutterIconActionTest : LightJavaCodeInsightFixtureTestCase() {
  private lateinit var ideComponents: IdeComponents
  private lateinit var mockSqliteExplorerProjectService: SqliteExplorerProjectService
  private lateinit var sqliteDatabase1: SqliteDatabase
  private lateinit var sqliteDatabase2: SqliteDatabase
  private lateinit var anActionEvent: AnActionEvent
  private lateinit var mouseEvent: MouseEvent
  private lateinit var anAction: DatabaseInspectorGutterIconAction
  private lateinit var viewFactory: MockSqliteEditorViewFactory

  override fun setUp() {
    super.setUp()
    sqliteDatabase1 = SqliteDatabase("db1", mock(SqliteService::class.java))
    sqliteDatabase2 = SqliteDatabase("d2", mock(SqliteService::class.java))

    ideComponents = IdeComponents(myFixture)
    mockSqliteExplorerProjectService = ideComponents.mockProjectService(SqliteExplorerProjectService::class.java)

    mouseEvent = mock(MouseEvent::class.java)
    `when`(mouseEvent.component).thenReturn(mock(Component::class.java))
    `when`(mouseEvent.point).thenReturn(Point(0, 0))

    viewFactory = MockSqliteEditorViewFactory()
  }

  fun testDoNothingWhenDatabaseIsNotOpen() {
    // Prepare
    `when`(mockSqliteExplorerProjectService.hasOpenDatabase()).thenReturn(false)

    buildAction("select * from Foo")

    // Act
    anAction.actionPerformed(anActionEvent)

    // Assert
    verify(mockSqliteExplorerProjectService, times(0)).runSqliteStatement(eq(sqliteDatabase1), any(SqliteStatement::class.java))
  }

  fun testRunSqliteStatementWhenDatabaseIsOpen() {
    // Prepare
    `when`(mockSqliteExplorerProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockSqliteExplorerProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo")

    // Act
    anAction.actionPerformed(anActionEvent)

    // Assert
    verify(mockSqliteExplorerProjectService).runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo"))
  }

  fun testMultipleDatabaseShowsPopUp() {
    // Prepare
    val databases = sortedSetOf(Comparator.comparing { database: SqliteDatabase -> database.name }, sqliteDatabase1, sqliteDatabase2)
    val mockJBPopupFactory = ideComponents.mockApplicationService(JBPopupFactory::class.java)
    val spyPopupChooserBuilder = spy(MockPopupChooserBuilder::class.java)
    `when`(mockJBPopupFactory.createPopupChooserBuilder(databases.toList())).thenReturn(spyPopupChooserBuilder)

    `when`(mockSqliteExplorerProjectService.getOpenDatabases()).thenReturn(databases)
    `when`(mockSqliteExplorerProjectService.hasOpenDatabase()).thenReturn(true)

    buildAction("select * from Foo")

    // Act
    anAction.actionPerformed(anActionEvent)
    spyPopupChooserBuilder.callback?.consume(sqliteDatabase1)

    // Assert
    assertNotNull(spyPopupChooserBuilder.callback)

    verify(mockJBPopupFactory).createPopupChooserBuilder(databases.toList())
    verify(spyPopupChooserBuilder).createPopup()
    verify(spyPopupChooserBuilder.mockPopUp).show(any(RelativePoint::class.java))
    verify(mockSqliteExplorerProjectService).runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo"))
  }

  fun testSqlStatementContainsNamedParameters1() {
    // Prepare
    `when`(mockSqliteExplorerProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockSqliteExplorerProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo where id = :anId")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(mapOf(":anId" to "1"))

    // Assert
    verify(mockSqliteExplorerProjectService)
      .runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo where id = ?", listOf("1")))
  }

  fun testSqlStatementContainsMultipleNamedParameters() {
    // Prepare
    `when`(mockSqliteExplorerProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockSqliteExplorerProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo where id = :anId and name = :aName")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(mapOf(":anId" to "1", ":aName" to "name"))

    // Assert
    verify(mockSqliteExplorerProjectService)
      .runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo where id = ? and name = ?", listOf("1", "name")))
  }

  fun testSqlStatementContainsNamedParameters2() {
    // Prepare
    `when`(mockSqliteExplorerProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockSqliteExplorerProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo where id = ?1")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(mapOf("?1" to "1"))

    // Assert
    verify(mockSqliteExplorerProjectService)
      .runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo where id = ?", listOf("1")))
  }

  fun testSqlStatementContainsNamedParameters3() {
    // Prepare
    `when`(mockSqliteExplorerProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockSqliteExplorerProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo where id = ?")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(mapOf("?" to "1"))

    // Assert
    verify(mockSqliteExplorerProjectService)
      .runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo where id = ?", listOf("1")))
  }

  fun testSqlStatementContainsNamedParameters4() {
    // Prepare
    `when`(mockSqliteExplorerProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockSqliteExplorerProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo where id = @anId")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(mapOf("@anId" to "1"))

    // Assert
    verify(mockSqliteExplorerProjectService)
      .runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo where id = ?", listOf("1")))
  }

  fun testSqlStatementContainsNamedParameters5() {
    // Prepare
    `when`(mockSqliteExplorerProjectService.hasOpenDatabase()).thenReturn(true)
    `when`(mockSqliteExplorerProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))

    buildAction("select * from Foo where id = \$anId")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(mapOf("\$anId" to "1"))

    // Assert
    verify(mockSqliteExplorerProjectService)
      .runSqliteStatement(sqliteDatabase1, SqliteStatement("select * from Foo where id = ?", listOf("1")))
  }

  private fun buildAction(sqlStatement: String) {
    myFixture.configureByText(
      JavaFileType.INSTANCE,
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

    anAction = DatabaseInspectorGutterIconAction(hostElement.project, hostElement, viewFactory)
    anActionEvent = TestActionEvent.createFromAnAction(anAction, mouseEvent, "", DataContext.EMPTY_CONTEXT)
  }
}