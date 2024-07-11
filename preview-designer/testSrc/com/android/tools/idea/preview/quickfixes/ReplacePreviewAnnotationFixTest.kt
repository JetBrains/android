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
package com.android.tools.idea.preview.quickfixes

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import kotlin.test.assertEquals
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ReplacePreviewAnnotationFixTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val fixture
    get() = projectRule.fixture

  private val project
    get() = projectRule.project

  @Before
  fun setUp() {
    fixture.addFileToProject(
      "src/invalid/Preview.kt",
      // language=kotlin
      """
        package invalid

        annotation class Preview(val device: String = "")
      """
        .trimIndent(),
    )
    fixture.addFileToProject(
      "src/valid/Preview.kt",
      // language=kotlin
      """
        package valid

        annotation class Preview(val device: String = "")
      """
        .trimIndent(),
    )
  }

  @Test
  fun quickFixMessage() {
    fixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
        package test

        import invalid.Preview

        @Preview
        fun somePreview() {}
      """
        .trimIndent(),
    )

    val quickFix = runReadAction {
      val invalidAnnotation = fixture.findElementByText("@Preview", KtAnnotationEntry::class.java)
      ReplacePreviewAnnotationFix(invalidAnnotation, withAnnotationFqn = "valid.Preview")
    }

    assertEquals("Replace annotation with \"valid.Preview\"", quickFix.text)
  }

  @Test
  fun replacesInvalidImportWithCorrectImportKotlin() {
    fixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
        package test

        import invalid.Preview

        @Preview
        fun somePreview() {}
      """
        .trimIndent(),
    )

    invokeQuickFixOnElement<KtAnnotationEntry>("@Preview")

    fixture.checkResult(
      // language=kotlin
      """
        package test

        import valid.Preview

        @Preview
        fun somePreview() {}
      """
        .trimIndent()
    )
  }

  @Test
  fun replacesInvalidImportWithCorrectImportJava() {
    fixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
        package test;

        import invalid.Preview;

        class Test {
          @Preview
          void somePreview() {
          }
        }
      """
        .trimIndent(),
    )

    invokeQuickFixOnElement<PsiAnnotation>("@Preview")

    fixture.checkResult(
      // language=java
      """
        package test;

        import valid.Preview;

        class Test {
          @Preview
          void somePreview() {
          }
        }
      """
        .trimIndent()
    )
  }

  @Test
  fun preservesAnnotationAttributesKotlin() {
    fixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
        package test

        import invalid.Preview

        @Preview(device = "some device")
        fun somePreview() {}
      """
        .trimIndent(),
    )

    invokeQuickFixOnElement<KtAnnotationEntry>("@Preview")

    fixture.checkResult(
      // language=kotlin
      """
        package test

        import valid.Preview

        @Preview(device = "some device")
        fun somePreview() {}
      """
        .trimIndent()
    )
  }

  @Test
  fun preservesAnnotationAttributesJava() {
    fixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
        package test;

        import invalid.Preview;

        class Test {
          @Preview(device = "some device")
          void somePreview() {
          }
        }
      """
        .trimIndent(),
    )

    invokeQuickFixOnElement<PsiAnnotation>("@Preview")

    fixture.checkResult(
      // language=java
      """
        package test;

        import valid.Preview;

        class Test {
          @Preview(device = "some device")
          void somePreview() {
          }
        }
      """
        .trimIndent()
    )
  }

  @Test
  fun replacesInvalidFqnPreviewAnnotationKotlin() {
    fixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
        package test

        @invalid.Preview
        fun somePreview() {}
      """
        .trimIndent(),
    )

    invokeQuickFixOnElement<KtAnnotationEntry>("@invalid.Preview")

    fixture.checkResult(
      // language=kotlin
      """
        package test

        import valid.Preview

        @Preview
        fun somePreview() {}
      """
        .trimIndent()
    )
  }

  @Test
  fun replacesInvalidFqnPreviewAnnotationJava() {
    fixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
        package test;

        class Test {
          @invalid.Preview
          void somePreview() {
          }
        }
      """
        .trimIndent(),
    )

    invokeQuickFixOnElement<PsiAnnotation>("@invalid.Preview")

    fixture.checkResult(
      // language=java
      """
        package test;

        import valid.Preview;

        class Test {
          @Preview
          void somePreview() {
          }
        }
      """
        .trimIndent()
    )
  }

  @Test
  fun usesFqnWhenInvalidAnnotationUsedElsewhereKotlin() {
    fixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
        package test

        import invalid.Preview

        @Preview
        fun somePreview() {}

        @Preview
        fun anotherPreview() {}
      """
        .trimIndent(),
    )

    invokeQuickFixOnElement<KtAnnotationEntry>("@Preview\nfun somePreview")

    fixture.checkResult(
      // language=kotlin
      """
        package test

        import invalid.Preview

        @valid.Preview
        fun somePreview() {}

        @Preview
        fun anotherPreview() {}
      """
        .trimIndent()
    )
  }

  @Test
  fun usesFqnWhenInvalidAnnotationUsedElsewhereJava() {
    fixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
        package test;

        import invalid.Preview;

        class Test {
          @Preview
          void somePreview() {
          }

          @Preview
          void anotherPreview() {
          }
        }
      """
        .trimIndent(),
    )

    invokeQuickFixOnElement<PsiAnnotation>("@Preview\n  void somePreview")

    fixture.checkResult(
      // language=java
      """
        package test;

        import invalid.Preview;

        class Test {
          @valid.Preview
          void somePreview() {
          }

          @Preview
          void anotherPreview() {
          }
        }
      """
        .trimIndent()
    )
  }

  private inline fun <reified T : PsiElement> invokeQuickFixOnElement(searchText: String) {
    val fix =
      runReadAction {
        val invalidAnnotation = fixture.findElementByText(searchText, T::class.java)
        ReplacePreviewAnnotationFix(invalidAnnotation, withAnnotationFqn = "valid.Preview").takeIf {
          it.isAvailable(
            fixture.project,
            invalidAnnotation.containingFile,
            invalidAnnotation,
            invalidAnnotation,
          )
        }
      } ?: return
    WriteCommandAction.runWriteCommandAction(project) { fix.applyFix() }
  }
}
