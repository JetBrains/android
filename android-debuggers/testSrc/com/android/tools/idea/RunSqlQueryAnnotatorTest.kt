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
package com.android.tools.idea

import com.android.testutils.MockitoKt.any
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sqliteExplorer.SqliteExplorerProjectService
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.ui.EmptyIcon
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import javax.swing.Icon

class RunSqlQueryAnnotatorTest : BasePlatformTestCase() {

  private lateinit var mockSqliteExplorerProjectService: SqliteExplorerProjectService

  override fun setUp() {
    super.setUp()
    StudioFlags.SQLITE_VIEWER_ENABLED.override(true)

    mockSqliteExplorerProjectService = IdeComponents(myFixture).mockProjectService(SqliteExplorerProjectService::class.java)
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
      assert(e is NoSuchElementException)
    }
  }

  fun testGutterIconRendererWhenDatabaseIsNotOpen() {
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

    val gutterIconRenderer = highlightInfo.gutterIconRenderer as GutterIconRenderer
    gutterIconRenderer.clickAction?.actionPerformed(mock(AnActionEvent::class.java))
    verify(mockSqliteExplorerProjectService, times(0)).runQuery(any(String::class.java))
  }

  fun testGutterIconRendererWhenDatabaseIsOpen() {
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

    val gutterIconRenderer = highlightInfo.gutterIconRenderer as GutterIconRenderer
    gutterIconRenderer.clickAction?.actionPerformed(mock(AnActionEvent::class.java))
    verify(mockSqliteExplorerProjectService).runQuery("select * from Foo")
  }

  fun testSqlStatementIsMadeOfMultipleStrings() {
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

    val gutterIconRenderer = highlightInfo.gutterIconRenderer as GutterIconRenderer
    gutterIconRenderer.clickAction?.actionPerformed(mock(AnActionEvent::class.java))
    verify(mockSqliteExplorerProjectService).runQuery("select * from Foo")
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