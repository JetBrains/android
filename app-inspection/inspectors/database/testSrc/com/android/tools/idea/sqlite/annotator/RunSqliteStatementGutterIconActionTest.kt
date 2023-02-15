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
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.DatabaseInspectorProjectService
import com.android.tools.idea.sqlite.controllers.SqliteParameter
import com.android.tools.idea.sqlite.controllers.SqliteParameterValue
import com.android.tools.idea.sqlite.mocks.FakeComponentPopupBuilder
import com.android.tools.idea.sqlite.mocks.FakeDatabaseInspectorViewsFactory
import com.android.tools.idea.sqlite.mocks.FakePopupChooserBuilder
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.utils.toSqliteValues
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.caret
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.registerServiceInstance
import com.intellij.ui.awt.RelativePoint
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JComponent
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class RunSqliteStatementGutterIconActionTest : LightJavaCodeInsightFixtureTestCase() {
  private lateinit var ideComponents: IdeComponents
  private lateinit var mockDatabaseInspectorProjectService: DatabaseInspectorProjectService
  private lateinit var sqliteDatabaseId1: SqliteDatabaseId
  private lateinit var sqliteDatabaseId2: SqliteDatabaseId
  private lateinit var anActionEvent: AnActionEvent
  private lateinit var mouseEvent: MouseEvent
  private lateinit var anAction: RunSqliteStatementGutterIconAction
  private lateinit var viewFactory: FakeDatabaseInspectorViewsFactory
  private lateinit var mockAppInspectionIdeServices: AppInspectionIdeServices

  override fun setUp() {
    super.setUp()

    sqliteDatabaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)
    sqliteDatabaseId2 = SqliteDatabaseId.fromLiveDatabase("db2", 2)

    ideComponents = IdeComponents(myFixture)
    mockDatabaseInspectorProjectService =
      ideComponents.mockProjectService(DatabaseInspectorProjectService::class.java)

    mockAppInspectionIdeServices = mock(AppInspectionIdeServices::class.java)
    whenever(mockDatabaseInspectorProjectService.getIdeServices())
      .thenReturn(mockAppInspectionIdeServices)

    mouseEvent = mock(MouseEvent::class.java)
    whenever(mouseEvent.component).thenReturn(mock(Component::class.java))
    whenever(mouseEvent.point).thenReturn(Point(0, 0))

    viewFactory = FakeDatabaseInspectorViewsFactory()
  }

  fun testDoNothingWhenDatabaseIsNotOpen() {
    // Prepare
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(false)

    buildActionFromJavaFile("select * from Foo")

    // Act
    anAction.actionPerformed(anActionEvent)

    // Assert
    verify(mockDatabaseInspectorProjectService, times(0))
      .runSqliteStatement(eq(sqliteDatabaseId1), any(SqliteStatement::class.java))
  }

  fun testRunSqliteStatementWhenDatabaseIsOpen() {
    // Prepare
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    whenever(mockDatabaseInspectorProjectService.getOpenDatabases())
      .thenReturn(listOf(sqliteDatabaseId1))

    buildActionFromJavaFile("select * from Foo")

    // Act
    anAction.actionPerformed(anActionEvent)

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(
        sqliteDatabaseId1,
        SqliteStatement(SqliteStatementType.SELECT, "select * from Foo")
      )
  }

  fun testRunSqliteStatementWhenDatabaseIsOpenKotlin() {
    // Prepare
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    whenever(mockDatabaseInspectorProjectService.getOpenDatabases())
      .thenReturn(listOf(sqliteDatabaseId1))

    buildActionFromKotlinFile("select * from Foo")

    // Act
    anAction.actionPerformed(anActionEvent)

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(
        sqliteDatabaseId1,
        SqliteStatement(SqliteStatementType.SELECT, "select * from Foo")
      )
  }

  fun testMultipleDatabaseShowsPopUp() {
    // Prepare
    val databases =
      listOf(sqliteDatabaseId1, sqliteDatabaseId2).sortedBy { database -> database.name }
    val mockJBPopupFactory = ideComponents.mockApplicationService(JBPopupFactory::class.java)
    val spyPopupChooserBuilder = spy(FakePopupChooserBuilder::class.java)
    whenever(mockJBPopupFactory.createPopupChooserBuilder(databases.toList()))
      .thenReturn(spyPopupChooserBuilder)
    whenever(mockJBPopupFactory.createComponentPopupBuilder(any(JComponent::class.java), eq(null)))
      .thenReturn(FakeComponentPopupBuilder())

    whenever(mockDatabaseInspectorProjectService.getOpenDatabases()).thenReturn(databases)
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)

    buildActionFromJavaFile("select * from Foo")

    // Act
    anAction.actionPerformed(anActionEvent)
    spyPopupChooserBuilder.callback?.consume(sqliteDatabaseId1)

    // Assert
    assertNotNull(spyPopupChooserBuilder.callback)

    verify(mockJBPopupFactory).createPopupChooserBuilder(databases.toList())
    verify(spyPopupChooserBuilder).createPopup()
    verify(spyPopupChooserBuilder.mockPopUp).show(any(RelativePoint::class.java))
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(
        sqliteDatabaseId1,
        SqliteStatement(SqliteStatementType.SELECT, "select * from Foo")
      )
  }

  fun testSqlStatementWithNoPositionalParameters() {
    // Prepare
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    whenever(mockDatabaseInspectorProjectService.getOpenDatabases())
      .thenReturn(listOf(sqliteDatabaseId1))

    buildActionFromJavaFile("select * from Foo where id = 42")

    // Act
    anAction.actionPerformed(anActionEvent)

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(
        sqliteDatabaseId1,
        SqliteStatement(SqliteStatementType.SELECT, "select * from Foo where id = 42")
      )
  }

  fun testSqlStatementContainsPositionalParameters() {
    // Prepare
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    whenever(mockDatabaseInspectorProjectService.getOpenDatabases())
      .thenReturn(listOf(sqliteDatabaseId1))

    buildActionFromJavaFile("select * from Foo where id = ?")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("id") to SqliteParameterValue.fromAny("1"))
    )

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(
        sqliteDatabaseId1,
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where id = ?",
          listOf("1").toSqliteValues(),
          "select * from Foo where id = '1'"
        )
      )
  }

  fun testSqlStatementContainsMultiplePositionalParameters() {
    // Prepare
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    whenever(mockDatabaseInspectorProjectService.getOpenDatabases())
      .thenReturn(listOf(sqliteDatabaseId1))

    buildActionFromJavaFile("select * from Foo where id = ? and name = ?")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(
        SqliteParameter("id") to SqliteParameterValue.fromAny("1"),
        SqliteParameter("name") to SqliteParameterValue.fromAny("name")
      )
    )

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(
        sqliteDatabaseId1,
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where id = ? and name = ?",
          listOf("1", "name").toSqliteValues(),
          "select * from Foo where id = '1' and name = 'name'"
        )
      )
  }

  fun testSqlStatementContainsMultiplePositionalNumberedParameters() {
    // Prepare
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    whenever(mockDatabaseInspectorProjectService.getOpenDatabases())
      .thenReturn(listOf(sqliteDatabaseId1))

    buildActionFromJavaFile("select * from Foo where id = ?1 and name = ?2")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(
        SqliteParameter("id") to SqliteParameterValue.fromAny("1"),
        SqliteParameter("name") to SqliteParameterValue.fromAny("name")
      )
    )

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(
        sqliteDatabaseId1,
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where id = ? and name = ?",
          listOf("1", "name").toSqliteValues(),
          "select * from Foo where id = '1' and name = 'name'"
        )
      )
  }

  fun testSqlStatementContainsPositionalParametersInComparison() {
    // Prepare
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    whenever(mockDatabaseInspectorProjectService.getOpenDatabases())
      .thenReturn(listOf(sqliteDatabaseId1))

    buildActionFromJavaFile("select * from Foo where id > ?")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("id") to SqliteParameterValue.fromAny("1"))
    )

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(
        sqliteDatabaseId1,
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where id > ?",
          listOf("1").toSqliteValues(),
          "select * from Foo where id > '1'"
        )
      )
  }

  fun testSqlStatementContainsPositionalParametersInExpressionAndComparison() {
    // Prepare
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    whenever(mockDatabaseInspectorProjectService.getOpenDatabases())
      .thenReturn(listOf(sqliteDatabaseId1))

    buildActionFromJavaFile("select * from Foo where id = (? >> name)")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("id") to SqliteParameterValue.fromAny("1"))
    )

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(
        sqliteDatabaseId1,
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where id = (? >> name)",
          listOf("1").toSqliteValues(),
          "select * from Foo where id = ('1' >> name)"
        )
      )
  }

  fun testSqlStatementContainsNamedParameters1() {
    // Prepare
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    whenever(mockDatabaseInspectorProjectService.getOpenDatabases())
      .thenReturn(listOf(sqliteDatabaseId1))

    buildActionFromJavaFile("select * from Foo where id = :anId")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter(":anId") to SqliteParameterValue.fromAny("1"))
    )

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(
        sqliteDatabaseId1,
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where id = ?",
          listOf("1").toSqliteValues(),
          "select * from Foo where id = '1'"
        )
      )
  }

  fun testSqlStatementContainsMultipleNamedParameters() {
    // Prepare
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    whenever(mockDatabaseInspectorProjectService.getOpenDatabases())
      .thenReturn(listOf(sqliteDatabaseId1))

    buildActionFromJavaFile("select * from Foo where id = :anId and name = :aName")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(
        SqliteParameter(":anId") to SqliteParameterValue.fromAny("1"),
        SqliteParameter(":aName") to SqliteParameterValue.fromAny("name")
      )
    )

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(
        sqliteDatabaseId1,
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where id = ? and name = ?",
          listOf("1", "name").toSqliteValues(),
          "select * from Foo where id = '1' and name = 'name'"
        )
      )
  }

  fun testSqlStatementContainsNamedParameters2() {
    // Prepare
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    whenever(mockDatabaseInspectorProjectService.getOpenDatabases())
      .thenReturn(listOf(sqliteDatabaseId1))

    buildActionFromJavaFile("select * from Foo where id = ?1")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("id") to SqliteParameterValue.fromAny("1"))
    )

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(
        sqliteDatabaseId1,
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where id = ?",
          listOf("1").toSqliteValues(),
          "select * from Foo where id = '1'"
        )
      )
  }

  fun testSqlStatementContainsNamedParameters3() {
    // Prepare
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    whenever(mockDatabaseInspectorProjectService.getOpenDatabases())
      .thenReturn(listOf(sqliteDatabaseId1))

    buildActionFromJavaFile("select * from Foo where id = ?")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("id") to SqliteParameterValue.fromAny("1"))
    )

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(
        sqliteDatabaseId1,
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where id = ?",
          listOf("1").toSqliteValues(),
          "select * from Foo where id = '1'"
        )
      )
  }

  fun testSqlStatementContainsNamedParameters4() {
    // Prepare
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    whenever(mockDatabaseInspectorProjectService.getOpenDatabases())
      .thenReturn(listOf(sqliteDatabaseId1))

    buildActionFromJavaFile("select * from Foo where id = @anId")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("@anId") to SqliteParameterValue.fromAny("1"))
    )

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(
        sqliteDatabaseId1,
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where id = ?",
          listOf("1").toSqliteValues(),
          "select * from Foo where id = '1'"
        )
      )
  }

  fun testSqlStatementContainsNamedParameters5() {
    // Prepare
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    whenever(mockDatabaseInspectorProjectService.getOpenDatabases())
      .thenReturn(listOf(sqliteDatabaseId1))

    buildActionFromJavaFile("select * from Foo where id = \$anId")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter("\$anId") to SqliteParameterValue.fromAny("1"))
    )

    // Assert
    verify(mockDatabaseInspectorProjectService)
      .runSqliteStatement(
        sqliteDatabaseId1,
        SqliteStatement(
          SqliteStatementType.SELECT,
          "select * from Foo where id = ?",
          listOf("1").toSqliteValues(),
          "select * from Foo where id = '1'"
        )
      )
  }

  fun testRunSqliteStatementOnSingleDBAnalytics() {
    // Prepare
    val mockTrackerService = mock(DatabaseInspectorAnalyticsTracker::class.java)
    project.registerServiceInstance(
      DatabaseInspectorAnalyticsTracker::class.java,
      mockTrackerService
    )

    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    whenever(mockDatabaseInspectorProjectService.getOpenDatabases())
      .thenReturn(listOf(sqliteDatabaseId1))

    buildActionFromJavaFile("select * from Foo where id")

    // Act
    anAction.actionPerformed(anActionEvent)

    // Assert
    verify(mockTrackerService)
      .trackStatementExecuted(
        AppInspectionEvent.DatabaseInspectorEvent.ConnectivityState.CONNECTIVITY_ONLINE,
        AppInspectionEvent.DatabaseInspectorEvent.StatementContext.GUTTER_STATEMENT_CONTEXT
      )
  }

  fun testRunSqliteStatementOnMultipleDBAnalytics() {
    // Prepare
    val mockTrackerService = mock(DatabaseInspectorAnalyticsTracker::class.java)
    project.registerServiceInstance(
      DatabaseInspectorAnalyticsTracker::class.java,
      mockTrackerService
    )

    val databases =
      listOf(sqliteDatabaseId1, sqliteDatabaseId2).sortedBy { database -> database.name }
    val mockJBPopupFactory = ideComponents.mockApplicationService(JBPopupFactory::class.java)
    val spyPopupChooserBuilder = spy(FakePopupChooserBuilder::class.java)
    whenever(mockJBPopupFactory.createPopupChooserBuilder(databases.toList()))
      .thenReturn(spyPopupChooserBuilder)
    whenever(mockJBPopupFactory.createComponentPopupBuilder(any(JComponent::class.java), eq(null)))
      .thenReturn(FakeComponentPopupBuilder())

    whenever(mockDatabaseInspectorProjectService.getOpenDatabases()).thenReturn(databases)
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)

    buildActionFromJavaFile("select * from Foo")

    // Act
    anAction.actionPerformed(anActionEvent)
    spyPopupChooserBuilder.callback?.consume(sqliteDatabaseId1)

    // Assert
    verify(mockTrackerService)
      .trackStatementExecuted(
        AppInspectionEvent.DatabaseInspectorEvent.ConnectivityState.CONNECTIVITY_ONLINE,
        AppInspectionEvent.DatabaseInspectorEvent.StatementContext.GUTTER_STATEMENT_CONTEXT
      )
  }

  fun testRunFromGutterIconOpensToolWindowDirectly() {
    // Prepare
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    whenever(mockDatabaseInspectorProjectService.getOpenDatabases())
      .thenReturn(listOf(sqliteDatabaseId1))

    buildActionFromJavaFile("select * from Foo where id = 1")

    // Act
    anAction.actionPerformed(anActionEvent)

    // Assert
    verify(mockAppInspectionIdeServices).showToolWindow()
  }

  fun testRunFromGutterIconOpensToolWindowFromDialog() {
    // Prepare
    whenever(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)
    whenever(mockDatabaseInspectorProjectService.getOpenDatabases())
      .thenReturn(listOf(sqliteDatabaseId1))

    buildActionFromJavaFile("select * from Foo where id = :anId")

    // Act
    anAction.actionPerformed(anActionEvent)

    val listener = viewFactory.parametersBindingDialogView.listeners.first()
    listener.bindingCompletedInvoked(
      mapOf(SqliteParameter(":anId") to SqliteParameterValue.fromAny("1"))
    )

    // Assert
    verify(mockAppInspectionIdeServices).showToolWindow()
  }

  private fun buildActionFromJavaFile(sqlStatement: String) {
    setUpJavaFixture(sqlStatement)
    setUpAction()
  }

  private fun buildActionFromKotlinFile(sqlStatement: String) {
    setUpKotlinFixture(sqlStatement)
    setUpAction()
  }

  private fun setUpAction() {
    val hostElement = myFixture.file.findElementAt(myFixture.caretOffset)!!.parent
    anAction =
      RunSqliteStatementGutterIconAction(
        hostElement.project,
        hostElement,
        viewFactory,
        mockDatabaseInspectorProjectService
      )
    anActionEvent =
      TestActionEvent.createFromAnAction(anAction, mouseEvent, "", DataContext.EMPTY_CONTEXT)
  }

  private fun setUpJavaFixture(sqlStatement: String) {
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
  }

  private fun setUpKotlinFixture(sqlStatement: String) {
    val psiFile =
      myFixture.addFileToProject(
        "Foo.kt",
        // language=kotlin
        """
        package com.example;
        class Foo {
          fun bar() {
            // language=RoomSql
            val query = $caret"$sqlStatement"
          }
        }
        """.trimIndent()
      )

    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
  }
}
