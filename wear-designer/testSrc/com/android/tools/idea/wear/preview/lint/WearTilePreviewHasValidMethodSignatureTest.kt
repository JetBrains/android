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

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wear.preview.WearPreviewBundle.message
import com.android.tools.idea.wear.preview.WearTileProjectRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WearTilePreviewHasValidMethodSignatureTest {
  @get:Rule val projectRule = WearTileProjectRule(AndroidProjectRule.withAndroidModel())

  private val fixture
    get() = projectRule.fixture

  private val inspection = WearTilePreviewHasValidMethodSignature()

  @Before
  fun setUp() {
    fixture.enableInspections(inspection)
  }

  @Test
  fun checkErrorsKotlin() {
    val file =
      fixture.addFileToProject(
        "src/main/test.kt",
        // language=kotlin
        """
        import android.content.Context
        import androidx.wear.tiles.tooling.preview.Preview
        import androidx.wear.tiles.tooling.preview.TilePreviewData

        @Preview
        fun validPreviewSignature() = TilePreviewData()

        @Preview
        fun validPreviewSignatureWithContext(context: Context) = TilePreviewData()

        @Preview
        fun <error descr="${message("inspection.invalid.return.type")}">invalidSignatureNoReturnType</error>() {}

        @Preview
        fun <error descr="${message("inspection.invalid.return.type")}">invalidSignatureWrongReturnType</error>() = "some string"

        @Preview
        fun invalidSignatureWithInvalidParameter<error descr="${message("inspection.invalid.parameters")}">(parameter: Int)</error> = TilePreviewData()

        @Preview
        fun invalidSignatureWithTooManyParameters<error descr="${message("inspection.invalid.parameters")}">(context: Context, other: Int)</error> = TilePreviewData()
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting(false, false, false)
  }

  @Test
  fun checkErrorsJava() {
    val file =
      fixture.addFileToProject(
        "src/main/Test.java",
        // language=java
        """
        import android.content.Context;
        import androidx.wear.tiles.tooling.preview.Preview;
        import androidx.wear.tiles.tooling.preview.TilePreviewData;

        class Test {

          @Preview
          TilePreviewData validPreviewSignature() {
            return new TilePreviewData();
          }

          @Preview
          TilePreviewData validPreviewSignatureWithContext(Context context) {
            return new TilePreviewData();
          }

          @Preview
          void <error descr="${message("inspection.invalid.return.type")}">invalidSignatureNoReturnType</error>() {}

          @Preview
          String <error descr="${message("inspection.invalid.return.type")}">invalidSignatureWrongReturnType</error>() {
            return "";
          }

          @Preview
          TilePreviewData invalidSignatureWithInvalidParameter<error descr="${message("inspection.invalid.parameters")}">(int parameter)</error> {
            return new TilePreviewData();
          }

          @Preview
          TilePreviewData invalidSignatureWithTooManyParameters<error descr="${message("inspection.invalid.parameters")}">(Context context, int other)</error> {
            return new TilePreviewData();
          }
        }
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting(false, false, false)
  }
}
