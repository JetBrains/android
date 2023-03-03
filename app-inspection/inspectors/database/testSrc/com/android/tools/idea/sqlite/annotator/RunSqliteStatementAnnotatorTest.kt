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

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.sqlite.DatabaseInspectorMessenger
import com.android.tools.idea.sqlite.DatabaseInspectorProjectService
import com.android.tools.idea.sqlite.DatabaseInspectorProjectServiceImpl
import com.android.tools.idea.sqlite.databaseConnection.live.LiveDatabaseConnection
import com.android.tools.idea.sqlite.mocks.FakeDatabaseInspectorController
import com.android.tools.idea.sqlite.model.DatabaseInspectorModelImpl
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.repository.DatabaseRepositoryImpl
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.EmptyIcon
import icons.StudioIcons
import javax.swing.Icon
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.ide.PooledThreadExecutor

class RunSqliteStatementAnnotatorTest : LightJavaCodeInsightFixtureTestCase() {
  private lateinit var ideComponents: IdeComponents

  private lateinit var databaseInspectorProjectService: DatabaseInspectorProjectService
  private lateinit var sqliteDatabaseId1: SqliteDatabaseId

  private val taskExecutor = PooledThreadExecutor.INSTANCE
  private val scope = CoroutineScope(EmptyCoroutineContext)

  override fun setUp() {
    super.setUp()
    sqliteDatabaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)

    val model = DatabaseInspectorModelImpl()
    databaseInspectorProjectService =
      DatabaseInspectorProjectServiceImpl(
        project = project,
        model = model,
        fileDatabaseManager = mock(),
        createController = { _, _, _, _ ->
          FakeDatabaseInspectorController(
            DatabaseRepositoryImpl(project, EdtExecutorService.getInstance()),
            model
          )
        }
      )

    ideComponents = IdeComponents(myFixture)
    ideComponents.replaceProjectService(
      DatabaseInspectorProjectService::class.java,
      databaseInspectorProjectService
    )
  }

  fun testNoIconWhenDatabaseIsNotOpen() {
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
        """
        .trimIndent()
    )

    val highlightInfo = findHighlightInfo()
    checkGutterIconRenderer(highlightInfo.gutterIconRenderer, EmptyIcon.ICON_0)
  }

  fun testRunIconWhenDatabaseIsOpen() {
    databaseInspectorProjectService.openSqliteDatabase(
      sqliteDatabaseId1,
      getMockLiveDatabaseConnection()
    )

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
        """
        .trimIndent()
    )

    val highlightInfo = findHighlightInfo()
    checkGutterIconRenderer(
      highlightInfo.gutterIconRenderer,
      StudioIcons.DatabaseInspector.NEW_QUERY
    )
  }

  fun testRendererVisibleWhenSqlStatementMadeOfMultipleStrings() {
    databaseInspectorProjectService.openSqliteDatabase(
      sqliteDatabaseId1,
      getMockLiveDatabaseConnection()
    )

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
        """
        .trimIndent()
    )

    val highlightInfo = findHighlightInfo()
    checkGutterIconRenderer(
      highlightInfo.gutterIconRenderer,
      StudioIcons.DatabaseInspector.NEW_QUERY
    )
  }

  fun testAnnotatorWorksWithKotlin() {
    databaseInspectorProjectService.openSqliteDatabase(
      sqliteDatabaseId1,
      getMockLiveDatabaseConnection()
    )

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
        """
        .trimIndent()
    )

    val highlightInfo = findHighlightInfo()
    checkGutterIconRenderer(
      highlightInfo.gutterIconRenderer,
      StudioIcons.DatabaseInspector.NEW_QUERY
    )
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

  private fun getMockLiveDatabaseConnection(): LiveDatabaseConnection {
    val databaseInspectorMessenger = DatabaseInspectorMessenger(mock(), scope, taskExecutor)
    return LiveDatabaseConnection(
      testRootDisposable,
      databaseInspectorMessenger,
      0,
      EdtExecutorService.getInstance()
    )
  }
}
