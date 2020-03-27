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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sqlite.DatabaseInspectorProjectService
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.model.LiveSqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.ui.EmptyIcon
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import javax.swing.Icon

class RunSqliteStatementAnnotatorTest : LightJavaCodeInsightFixtureTestCase() {
  private lateinit var ideComponents: IdeComponents

  private lateinit var mockDatabaseInspectorProjectService: DatabaseInspectorProjectService
  private lateinit var sqliteDatabase1: SqliteDatabase
  private lateinit var sqliteDatabase2: SqliteDatabase

  override fun setUp() {
    super.setUp()
    StudioFlags.DATABASE_INSPECTOR_ENABLED.override(true)

    sqliteDatabase1 = LiveSqliteDatabase("db1", mock(DatabaseConnection::class.java))
    sqliteDatabase2 = LiveSqliteDatabase("db2", mock(DatabaseConnection::class.java))

    ideComponents = IdeComponents(myFixture)
    mockDatabaseInspectorProjectService = ideComponents.mockProjectService(DatabaseInspectorProjectService::class.java)
    `when`(mockDatabaseInspectorProjectService.getOpenDatabases()).thenReturn(listOf(sqliteDatabase1))
  }

  override fun tearDown() {
    try {
      StudioFlags.DATABASE_INSPECTOR_ENABLED.clearOverride()
    } finally {
      super.tearDown()
    }
  }

  fun testAnnotatorDoesntWorkIfSqliteInspectorFlagIsDisabled() {
    StudioFlags.DATABASE_INSPECTOR_ENABLED.override(false)
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(false)

    myFixture.configureByText(
      StdFileTypes.JAVA,
      // language=java
      """
        package com.example;
        class Foo {
          void bar() {
            // language=RoomSql
            String query = "select * from Foo";${caret}
          }
        }
        """.trimIndent()
    )

    try {
      findHighlightInfo()
      fail("should have failed")
    } catch (e: Exception) {
      assertInstanceOf(e, NoSuchElementException::class.java)
    }
  }

  fun testNoIconWhenDatabaseIsNotOpen() {
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(false)

    myFixture.configureByText(
      StdFileTypes.JAVA,
      // language=java
      """
        package com.example;
        class Foo {
          void bar() {
            // language=RoomSql
            String query = "select * from Foo";${caret}
          }
        }
        """.trimIndent()
    )

    val highlightInfo = findHighlightInfo()
    checkGutterIconRenderer(highlightInfo.gutterIconRenderer, EmptyIcon.ICON_0)
  }

  fun testRunIconWhenDatabaseIsOpen() {
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)

    myFixture.configureByText(
      StdFileTypes.JAVA,
      // language=java
      """
        package com.example;
        class Foo {
          void bar() {
            // language=RoomSql
            String query = "select * from Foo";${caret}
          }
        }
        """.trimIndent()
    )

    val highlightInfo = findHighlightInfo()
    checkGutterIconRenderer(highlightInfo.gutterIconRenderer, AllIcons.RunConfigurations.TestState.Run)
  }

  fun testRendererVisibleWhenSqlStatementMadeOfMultipleStrings() {
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(true)

    myFixture.configureByText(
      StdFileTypes.JAVA,
      // language=java
      """
        package com.example;
        class Foo {
          void bar() {
            // language=RoomSql
            String query = "select " +"*" +" from Foo";${caret}
          }
        }
        """.trimIndent()
    )

    val highlightInfo = findHighlightInfo()
    checkGutterIconRenderer(highlightInfo.gutterIconRenderer, AllIcons.RunConfigurations.TestState.Run)
  }

  fun testAnnotatorWorksWithKotlin() {
    `when`(mockDatabaseInspectorProjectService.hasOpenDatabase()).thenReturn(false)

    myFixture.configureByText(
      StdFileTypes.JAVA,
      // language=kotlin
      """
        package com.example;
        class Foo {
          fun bar() {
            // language=RoomSql
            val query = "select * from Foo" ${caret}
          }
        }
        """.trimIndent()
    )

    val highlightInfo = findHighlightInfo()
    checkGutterIconRenderer(highlightInfo.gutterIconRenderer, EmptyIcon.ICON_0)
  }

  private fun findHighlightInfo(): HighlightInfo {
    val document = myFixture.editor.document
    val lineNumberOfTarget = document.getLineNumber(myFixture.caretOffset)
    val highlightInfos = myFixture.doHighlighting()
    return highlightInfos
      .filter { info -> info.gutterIconRenderer != null }
      .first { info -> document.getLineNumber(info.startOffset) == lineNumberOfTarget }
  }

  private fun checkGutterIconRenderer(gutterIconRenderer: GutterMark?, expectedIcon: Icon) {
    assertThat(gutterIconRenderer).isNotNull()
    assertThat(gutterIconRenderer).isInstanceOf(GutterIconRenderer::class.java)
    val renderer = gutterIconRenderer as GutterIconRenderer
    val icon = renderer.icon
    assertThat(icon).isEqualTo(expectedIcon)
  }
}