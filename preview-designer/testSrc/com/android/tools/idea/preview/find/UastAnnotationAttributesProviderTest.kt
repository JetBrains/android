/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.preview.find

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.DumbModeTestUtils
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElement
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class UastAnnotationAttributesProviderTest {

  @get:Rule val projectRule = AndroidProjectRule.Companion.inMemory()
  private val project
    get() = projectRule.project

  private val fixture
    get() = projectRule.fixture

  /**
   * Ensures that calling findPreviewMethods returns an empty. Although the method is guaranteed to
   * be called under smart mode,
   */
  @Test
  fun testDumbMode(): Unit = runBlocking {
    val composeTest =
      fixture.addFileToProjectAndInvalidate(
        "src/Test.kt",
        // language=kotlin
        """

     @Repeatable
     annotation class TestAnnotation(
      val name: String = "",
     )

      @TestAnnotation(name = "preview1")
      fun review1() {
      }

      @TestAnnotation(name = "preview2")
      fun Preview1() {
      }
    """
          .trimIndent(),
      )

    val annotations = readAction {
      PsiTreeUtil.findChildrenOfType<KtNamedFunction>(composeTest, KtNamedFunction::class.java)
        .flatMap { it.annotationEntries }
        .filter { it.shortName?.identifier == "TestAnnotation" }
        .mapNotNull { it.toUElement(UAnnotation::class.java) }
    }
    val outputInSmartMode =
      annotations.joinToString("\n") {
        runReadAction {
          UastAnnotationAttributesProvider(it, emptyMap()).getStringAttribute("name") ?: "<null>"
        }
      }
    Assert.assertEquals(
      """
      preview1
      preview2
    """
        .trimIndent(),
      outputInSmartMode,
    )

    val output =
      DumbModeTestUtils.computeInDumbModeSynchronously(project) {
        annotations.joinToString("\n") {
          runReadAction {
            UastAnnotationAttributesProvider(it, emptyMap()).getStringAttribute("name") ?: "<null>"
          }
        }
      }

    // This checks the result when UastAnnotationAttributesProvider runs without the index being
    // ready.
    // This behaviour differs from K1 to K2 so the test has different expectations.
    // In K1, there is no use of the index, so the resolution is expected to proceed as in the case
    // above.
    val expectedOutput =
      if (KotlinPluginModeProvider.isK2Mode())
        """
        <null>
        <null>
      """
          .trimIndent()
      else
        """
        preview1
        preview2
      """
          .trimIndent()
    Assert.assertEquals(expectedOutput, output)
  }
}
