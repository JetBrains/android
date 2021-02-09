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
package com.android.tools.idea.compose.preview.animation

import com.android.tools.idea.compose.preview.addFileToProjectAndInvalidate
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test


private const val LABEL_NOT_SET_INSPECTION_MESSAGE =
  "PropKeys should set the label parameter so they can be inspected in the Animation Preview."

class AnimationInspectionsTest {

  @get:Rule
  val projectRule: AndroidProjectRule = AndroidProjectRule.inMemory()
  private val fixture get() = projectRule.fixture

  @Before
  fun setUp() {
    fixture.addFileToProjectAndInvalidate(
      "src/androidx/compose/animation/core/PropKey.kt",
      // language=kotlin
      """
      package androidx.compose.animation.core

      class IntPropKey(val label: String = "IntPropKey")
      """.trimIndent()
    )
    fixture.addFileToProjectAndInvalidate(
      "src/androidx/compose/animation/PropertyKeys.kt",
      // language=kotlin
      """
      package androidx.compose.animation

      class ColorPropKey(val anotherParam: String = "Something", val label: String = "ColorPropKey")
      """.trimIndent()
    )
    fixture.enableInspections(PropKeysLabelInspection() as InspectionProfileEntry)
  }

  @Test
  fun testLabelNotSet() {
    // language=kotlin
    val fileContent = """
      import androidx.compose.animation.core.IntPropKey

      private val scale = IntPropKey()
    """.trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    assertEquals(LABEL_NOT_SET_INSPECTION_MESSAGE, fixture.doHighlighting(HighlightSeverity.WEAK_WARNING).single().description)
  }

  @Test
  fun testLabelSetExplicitly() {
    // language=kotlin
    val fileContent = """
      import androidx.compose.animation.core.IntPropKey

      private val scale = IntPropKey("key")
    """.trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    assertTrue(fixture.doHighlighting(HighlightSeverity.WEAK_WARNING).isEmpty())
  }

  @Test
  fun testLabelSetImplicitly() {
    // language=kotlin
    val fileContent = """
      import androidx.compose.animation.core.IntPropKey

      private val scale = IntPropKey(label = "key")
    """.trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    assertTrue(fixture.doHighlighting(HighlightSeverity.WEAK_WARNING).isEmpty())
  }

  @Test
  fun testSetOtherParameterImplicitly() {
    // language=kotlin
    val fileContent = """
      import androidx.compose.animation.ColorPropKey

      private val scale = ColorPropKey("key")
    """.trimIndent()

    fixture.configureByText("Test.kt", fileContent)
    assertEquals(LABEL_NOT_SET_INSPECTION_MESSAGE, fixture.doHighlighting(HighlightSeverity.WEAK_WARNING).single().description)
  }

  @Test
  fun testQuickFix() {
    // language=kotlin
    val originalFileContent = """
      import androidx.compose.animation.core.IntPropKey

      private val scale = IntPropKey()
    """.trimIndent()
    fixture.configureByText("Test.kt", originalFileContent)

    // language=kotlin
    val fileContentAfterFix = """
      import androidx.compose.animation.core.IntPropKey

      private val scale = IntPropKey(label = "")
    """.trimIndent()

    val quickFix = (fixture.getAllQuickFixes().single() as QuickFixWrapper).fix as LocalQuickFixOnPsiElement
    assertEquals("Add label parameter", quickFix.text)
    assertEquals("Compose preview", quickFix.familyName)

    ApplicationManager.getApplication().invokeAndWait {
      CommandProcessor.getInstance().executeCommand(fixture.project, { runWriteAction { quickFix.applyFix() } }, "Add Label Argument", null)
    }

    fixture.checkResult(fileContentAfterFix)
  }
}