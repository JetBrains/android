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
import com.android.tools.idea.sqlite.SqliteService
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqliteExplorer.SqliteExplorerProjectService
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.ui.EmptyIcon
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import javax.swing.Icon

class RunSqlQueryAnnotatorTest : LightJavaCodeInsightFixtureTestCase() {
  private lateinit var ideComponents: IdeComponents

  private lateinit var mockSqliteExplorerProjectService: SqliteExplorerProjectService
  private lateinit var sqliteDatabase1: SqliteDatabase
  private lateinit var sqliteDatabase2: SqliteDatabase

  override fun setUp() {
    super.setUp()
    StudioFlags.SQLITE_VIEWER_ENABLED.override(true)

    sqliteDatabase1 = SqliteDatabase("db1", mock(SqliteService::class.java))
    sqliteDatabase2 = SqliteDatabase("db2", mock(SqliteService::class.java))

    ideComponents = IdeComponents(myFixture)
    mockSqliteExplorerProjectService = ideComponents.mockProjectService(SqliteExplorerProjectService::class.java)
    `when`(mockSqliteExplorerProjectService.getOpenDatabases()).thenReturn(setOf(sqliteDatabase1))
  }

  override fun tearDown() {
    try {
      StudioFlags.SQLITE_VIEWER_ENABLED.clearOverride()
    } finally {
      super.tearDown()
    }
  }

  fun testAnnotatorDoesntWorkIfSqliteInspectorFlagIsDisabled() {
    StudioFlags.SQLITE_VIEWER_ENABLED.override(false)
    `when`(mockSqliteExplorerProjectService.hasOpenDatabase()).thenReturn(false)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
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
    `when`(mockSqliteExplorerProjectService.hasOpenDatabase()).thenReturn(false)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
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
    `when`(mockSqliteExplorerProjectService.hasOpenDatabase()).thenReturn(true)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
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
    `when`(mockSqliteExplorerProjectService.hasOpenDatabase()).thenReturn(true)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
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
    `when`(mockSqliteExplorerProjectService.hasOpenDatabase()).thenReturn(false)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
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