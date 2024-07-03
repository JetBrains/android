/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wear.preview.lint

import com.android.tools.idea.wear.preview.WearPreviewBundle.message
import com.android.tools.idea.wear.preview.WearTileProjectRule
import com.intellij.ide.highlighter.JavaFileType
import org.jetbrains.kotlin.idea.KotlinFileType
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WearTilePreviewShouldBeTopLevelFunctionTest {
  @get:Rule val projectRule = WearTileProjectRule()

  private val fixture
    get() = projectRule.fixture

  private val inspection = WearTilePreviewShouldBeTopLevelFunction()

  @Before
  fun setUp() {
    fixture.enableInspections(inspection)
  }

  @Test
  fun checkKotlinErrors() {
    fixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      import androidx.wear.tiles.tooling.preview.Preview
      import androidx.wear.tiles.tooling.preview.TilePreviewData

      @Preview(name = "top level preview")
      fun topLevelPreview(): TilePreviewData {
        <error descr="${message("inspection.top.level.function")}">@Preview(name = "not a top level preview")
        fun notTopLevelFunctionPreview(): TilePreviewData {
            <error descr="${message("inspection.top.level.function")}">@Preview(name = "not a top level preview, with a lot of nesting")
            fun superNestedPreview() = TilePreviewData()</error>

            fun validSuperNestedMethod() = TilePreviewData()

            return TilePreviewData()
        }</error>

        fun validNotTopLevelMethod() = TilePreviewData()

        return TilePreviewData()
      }

      class SomeClass {
        @Preview
        fun classMethodPreview(): TilePreviewData {
          <error descr="${message("inspection.top.level.function")}">@Preview(name = "not a top level preview in a class")
          fun notTopLevelFunctionPreviewInAClass() = TilePreviewData()</error>

          return TilePreviewData()
        }

        @Preview(name = "preview in a class with default constructor")
        private fun privateClassMethodPreview() = TilePreviewData()
      }

      private class PrivateClass {
        class NotTopLevelClass {
          <error descr="${message("inspection.top.level.function")}">@Preview("in a non top level class")
          fun classMethodPreview() = TilePreviewData()</error>

          fun validClassMethod() = TilePreviewData()
        }

        @Preview
        fun classMethodPreview() = TilePreviewData()
      }

      class SomeClassWithoutDefaultConstructor(i: Int) {
        <error descr="${message("inspection.top.level.function")}">@Preview("in a class with parameters")
        fun classMethodPreview() = TilePreviewData()</error>

        fun validClassMethod() = TilePreviewData()
      }
    """
        .trimIndent(),
    )

    fixture.checkHighlighting(false, false, false)
  }

  @Test
  fun checkJavaErrors() {
    fixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      import androidx.wear.tiles.tooling.preview.Preview;
      import androidx.wear.tiles.tooling.preview.TilePreviewData;

      class ClassWithDefaultConstructor {
        @Preview(name = "top level preview")
        TilePreviewData topLevelPreview() {
          return new TilePreviewData();
        }

        class NestedClass {
          <error descr="${message("inspection.top.level.function")}">@Preview(name = "not in a top level class")
          TilePreviewData classMethodPreview() {
            return new TilePreviewData();
          }</error>

          TilePreviewData validClassMethod() {
            return new TilePreviewData();
          }
        }

        static class SomeClassWithoutDefaultConstructor {
          SomeClassWithoutDefaultConstructor(int i) {
          }

          <error descr="${message("inspection.top.level.function")}">@Preview(name = "in a class with parameters")
          TilePreviewData classMethodPreview() {
            return new TilePreviewData();
          }</error>

          TilePreviewData validClassMethod() {
            return new TilePreviewData();
          }
        }
      }
    """
        .trimIndent(),
    )

    fixture.checkHighlighting(false, false, false)
  }
}
