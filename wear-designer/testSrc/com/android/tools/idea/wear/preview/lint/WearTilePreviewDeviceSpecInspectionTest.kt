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

import com.android.tools.idea.testing.Sdks
import com.android.tools.idea.wear.preview.WearTileProjectRule
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.application.runWriteActionAndWait
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WearTilePreviewDeviceSpecInspectionTest {

  @get:Rule val projectRule = WearTileProjectRule()

  private val fixture
    get() = projectRule.fixture

  private val inspection = WearTilePreviewDeviceSpecInspection()

  @Before
  fun setUp() {
    runWriteActionAndWait { Sdks.addLatestAndroidSdk(fixture.projectDisposable, fixture.module) }
    fixture.enableInspections(inspection)
  }

  @Test
  fun checkErrorsKotlin() {
    val file =
      fixture.addFileToProject(
        "src/main/test.kt",
        // language=kotlin
        """
        import androidx.wear.tiles.tooling.preview.Preview
        import androidx.wear.tiles.tooling.preview.TilePreviewData

        @Preview(device = "invalid multipreview spec")
        annotation class MultiPreviewWithInvalidSpec

        @Preview(device = "id:wearos_small_round")
        annotation class MultiPreviewWithValidSpec

        @Preview(device = "id:wearos_small_round")
        fun validDeviceId() = TilePreviewData()

        @Preview(device = "spec:width=400px,height=400px")
        fun validDeviceSpec() = TilePreviewData()

        @Preview(device = "spec:width=400,height=400")
        fun invalidSpec() = TilePreviewData()

        @Preview(device = "id:unknown_id")
        fun unknownId() = TilePreviewData()
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(file.virtualFile)

    checkErrorsAndFixActions()
  }

  @Test
  fun checkErrorsJava() {
    val file =
      fixture.addFileToProject(
        "src/main/Test.java",
        // language=java
        """
        import androidx.wear.tiles.tooling.preview.Preview;
        import androidx.wear.tiles.tooling.preview.TilePreviewData;

        class Test {
          @Preview(device = "invalid multipreview spec")
          public @interface MultiPreviewWithInvalidSpec {}

          @Preview(device = "id:wearos_small_round")
          public @interface MultiPreviewWithValidSpec {}

          @Preview(device = "id:wearos_small_round")
          TilePreviewData validDeviceId() { return new TilePreviewData(); }

          @Preview(device = "spec:width=400px,height=400px")
          TilePreviewData validDeviceSpec() { return new TilePreviewData(); }

          @Preview(device = "spec:width=400,height=400")
          TilePreviewData invalidSpec() { return new TilePreviewData(); }

          @Preview(device = "id:unknown_id")
          TilePreviewData unknownId() { return new TilePreviewData(); }

        }
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(file.virtualFile)

    checkErrorsAndFixActions()
  }

  private fun checkErrorsAndFixActions() {
    val issues =
      fixture.doHighlighting(HighlightSeverity.ERROR).filter {
        it.inspectionToolId == inspection.id
      }
    assertEquals(3, issues.size)

    val invalidMultiPreviewSpec = issues.single { it.text.contains("invalid multipreview spec") }
    assertEquals(
      "Unknown parameter: Must be a Device ID or a Device specification: \"id:...\", \"spec:...\"..",
      invalidMultiPreviewSpec.description,
    )

    var fixAction = invalidMultiPreviewSpec.findRegisteredQuickFix { desc, _ -> desc.action }
    assertEquals("Replace with id:wearos_small_round", fixAction.text)
    fixAction.apply()

    val invalidSpec = issues.single { it.text.contains("spec:width=400,height=400") }
    assertEquals(
      """
      Bad value type for: width, height.

      Parameter: width, height should have Float(dp/px) value.
    """
        .trimIndent(),
      invalidSpec.description,
    )

    fixAction = invalidSpec.findRegisteredQuickFix { desc, _ -> desc.action }
    assertEquals("Replace with spec:width=400dp,height=400dp", fixAction.text)
    fixAction.apply()

    val unknownId = issues.single { it.text.contains("id:unknown_id") }
    assertEquals("Unknown parameter: unknown_id.", unknownId.description)

    fixAction = unknownId.findRegisteredQuickFix { desc, _ -> desc.action }
    assertEquals("Replace with id:wearos_small_round", fixAction.text)
    fixAction.apply()

    // after applying all the fixes, there should no longer be any issues
    assertEquals(0, fixture.doHighlighting(HighlightSeverity.ERROR).size)
  }

  private fun IntentionAction.apply() = runInEdt {
    runUndoTransparentWriteAction { invoke(fixture.project, fixture.editor, fixture.file) }
  }
}
