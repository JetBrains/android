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

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.psi.util.parentOfType
import org.jetbrains.android.compose.stubPreviewAnnotation
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class PreviewAnnotationCheckTest {

  @get:Rule
  val rule = AndroidProjectRule.inMemory()

  val fixture get() = rule.fixture

  @Before
  fun setup() {
    fixture.stubPreviewAnnotation()
  }

  @Test
  fun testGoodAnnotations() {
    val vFile = rule.fixture.addFileToProject(
      "test.kt",
      // language=kotlin
      """
        package example
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(device = "spec:shape=Normal,width=1080,height=1920,unit=px,dpi=320")
        fun myFun() {}
      """.trimIndent()
    ).virtualFile

    val annotationEntry = runWriteActionAndWait {
      rule.fixture.openFileInEditor(vFile)
      rule.fixture.moveCaret("@Prev|iew").parentOfType<KtAnnotationEntry>()
    }
    assertNotNull(annotationEntry)

    val result = runReadAction { PreviewAnnotationCheck.checkPreviewAnnotationIfNeeded(annotationEntry) }
    assert(result.issues.isEmpty())
  }

  @Test
  fun testAnnotationWithIssues() {
    val vFile = rule.fixture.addFileToProject(
      "test.kt",
      // language=kotlin
      """
        package example
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(device = "spec:shape=Tablet,shape=Normal,width=qwe,unit=sp,dpi=320,madeUpParam")
        fun myFun() {}
""".trimIndent()).virtualFile

    val annotationEntry = runWriteActionAndWait {
      rule.fixture.openFileInEditor(vFile)
      rule.fixture.moveCaret("@Prev|iew").parentOfType<KtAnnotationEntry>()
    }
    assertNotNull(annotationEntry)

    val result = runReadAction { PreviewAnnotationCheck.checkPreviewAnnotationIfNeeded(annotationEntry) }
    assertEquals(6, result.issues.size)
    assertEquals(
      listOf(
        BadType::class,
        BadType::class,
        BadType::class,
        Unknown::class,
        Repeated::class,
        Missing::class
      ),
      result.issues.map { it::class }
    )
    assertEquals(
      "spec:shape=Normal,width=1080,unit=px,dpi=320,height=1920",
      result.proposedFix
    )
  }

  @Test
  fun testFailure() {
    val vFile = rule.fixture.addFileToProject(
      "test.kt",
      // language=kotlin
      """
        package example
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(device = "spec:shape=Normal,width=1080,height=1920,unit=px,dpi=320")
        fun myFun() {}
""".trimIndent()).virtualFile

    val annotationEntry = runWriteActionAndWait {
      rule.fixture.openFileInEditor(vFile)
      rule.fixture.moveCaret("@Prev|iew").parentOfType<KtAnnotationEntry>()
    }
    assertNotNull(annotationEntry)

    val result = PreviewAnnotationCheck.checkPreviewAnnotationIfNeeded(annotationEntry)
    assertEquals(1, result.issues.size)
    assertEquals(Failure::class, result.issues[0]::class)
    assertEquals("No read access", (result.issues[0] as Failure).failureMessage)
  }
}