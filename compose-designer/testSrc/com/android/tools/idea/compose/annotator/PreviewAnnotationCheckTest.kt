/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.annotator

import com.android.tools.compose.COMPOSABLE_ANNOTATION_FQ_NAME
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.Sdks
import com.android.tools.idea.testing.moveCaret
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.runInEdtAndGet
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.intellij.lang.annotations.Language
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.android.compose.stubPreviewAnnotation
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class PreviewAnnotationCheckTest {

  @get:Rule val rule = AndroidProjectRule.inMemory()

  val fixture
    get() = rule.fixture

  @Before
  fun setup() {
    fixture.stubPreviewAnnotation()
    fixture.stubComposableAnnotation()
  }

  @Test
  fun testGoodAnnotations() {
    var issue =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import $COMPOSABLE_ANNOTATION_FQ_NAME

        @Preview(device = "spec:width=1080px,height=1920px,dpi=320")
        @Composable
        fun myFun() {}
      """
          .trimIndent()
      )
    assertNull(issue)

    issue =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import $COMPOSABLE_ANNOTATION_FQ_NAME

        @Preview(device = "   ")
        @Composable
        fun myFun() {}
      """
          .trimIndent()
      )
    assertNull(issue)
  }

  @Test
  fun testAnnotationWithIssues() {
    var issue =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import $COMPOSABLE_ANNOTATION_FQ_NAME

        @Preview(device = "spec:width=20sp,dpi=320,dpi=320,chinSize=qwe,madeUpParam")
        @Composable
        fun myFun() {}
"""
          .trimIndent()
      )
    assertNotNull(issue)
    assertEquals(
      """
      Bad value type for: width, chinSize.

      Parameter: width, chinSize should have Float(dp/px) value.

      Unknown parameter: madeUpParam.


      Parameters should not be repeated: dpi.


      Missing parameter: height.
    """
        .trimIndent(),
      issue.descriptionTemplate,
    )
    assertEquals(1, issue.fixes?.size)

    issue =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import $COMPOSABLE_ANNOTATION_FQ_NAME

        @Preview(device = " abc ")
        @Composable
        fun myFun() {}
"""
          .trimIndent()
      )
    assertNotNull(issue)
    assertEquals(
      "Unknown parameter: Must be a Device ID or a Device specification: \"id:...\", \"spec:...\"..",
      issue.descriptionTemplate,
    )
    assertEquals(1, issue.fixes?.size)
  }

  @Test
  fun testBadTarget() {
    val issue =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(device = "spec:width=1080px,height=1920px,dpi=320")
        class myNotAnnotation() {}
      """
          .trimIndent()
      )
    assertNotNull(issue)
    assertEquals(
      "Preview target must be a composable function or an annotation class",
      issue.descriptionTemplate,
    )
    assertTrue(issue.fixes.isNullOrEmpty())
  }

  @Test
  fun testMultipreviewAnnotation() {
    val issue =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(device = "spec:width=1080px,height=1920px,dpi=320")
        annotation class myAnnotation() {}
      """
          .trimIndent()
      )
    assertNull(issue)
  }

  @Test
  fun testDeviceIdCheck() {
    runWriteActionAndWait { Sdks.addLatestAndroidSdk(rule.fixture.projectDisposable, rule.module) }

    var issue =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import $COMPOSABLE_ANNOTATION_FQ_NAME

        @Preview(device = "id:device_1")
        @Composable
        fun myFun() {}
"""
          .trimIndent()
      )
    assertNotNull(issue)
    assertEquals("Unknown parameter: device_1.", issue.descriptionTemplate)
    assertEquals(1, issue.fixes?.size)

    issue =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import $COMPOSABLE_ANNOTATION_FQ_NAME

        @Preview(device = "id:pixel_4")
        @Composable
        fun myFun() {}
"""
          .trimIndent()
      )
    assertNull(issue)
  }

  /**
   * Adds file with the given [fileContents] and runs [PreviewAnnotationCheck.checkAnnotation] on
   * the first Preview annotation found.
   *
   * This method returns a list of [ProblemDescriptor]s for the issues found.
   */
  private fun addKotlinFileAndCheckPreviewAnnotation(
    @Language("kotlin") fileContents: String
  ): ProblemDescriptor? {
    rule.fixture.configureByText(KotlinFileType.INSTANCE, fileContents)

    val annotationEntry = runInEdtAndGet {
      rule.fixture.moveCaret("@Prev|iew").parentOfType<KtAnnotationEntry>()
    }
    assertNotNull(annotationEntry)

    val manager = InspectionManager.getInstance(fixture.project)
    return runReadAction { PreviewAnnotationCheck.checkAnnotation(annotationEntry, manager, true) }
  }
}
