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
package com.android.tools.idea.compose.gradle.uicheck

import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.preview.uicheck.UiCheckModeFilter
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.kotlin.idea.gradleTooling.get
import org.junit.Rule
import org.junit.Test

class UiCheckModeFilterTest {
  @get:Rule val projectRule = ComposeGradleProjectRule(projectPath = SIMPLE_COMPOSE_PROJECT_PATH)

  @Test
  fun `test calculate preview on empty base`() {
    val previewElements = UiCheckModeFilter.Enabled.calculatePreviews(null, isWearPreview = false)
    assertTrue(previewElements.isEmpty())
  }

  @Test
  fun `test calculate non Wear preview with no decorations shown`() {
    val elementInstanceTest =
      SingleComposePreviewElementInstance.Companion.forTesting<SmartPsiElementPointer<PsiElement>>(
        "google.simpleapplication.MainActivityKt.DefaultPreview",
        showDecorations = false,
      )

    val previewElements =
      UiCheckModeFilter.Enabled.calculatePreviews(elementInstanceTest, isWearPreview = false)

    val generatedDisplaySettings = previewElements.toList().map { it.displaySettings }

    // Show decoration flag shouldn't affect screen sizes previews because show previews on
    // different phone sizes:
    // - Medium phone
    // - Unfolded phone
    // - Medium tablet
    // - Desktop
    // - Medium phone landscape
    assertTrue(generatedDisplaySettings[0].showDecoration)
    assertTrue(generatedDisplaySettings[1].showDecoration)
    assertTrue(generatedDisplaySettings[2].showDecoration)
    assertTrue(generatedDisplaySettings[3].showDecoration)
    assertTrue(generatedDisplaySettings[4].showDecoration)

    // Font scales are affected by show decoration flag change.
    // They show previews with different font sizes:
    // - 85%
    // - 100%
    // - 115%
    // - 130%
    // - 180%
    // - 200%
    assertFalse(generatedDisplaySettings[5].showDecoration)
    assertFalse(generatedDisplaySettings[6].showDecoration)
    assertFalse(generatedDisplaySettings[7].showDecoration)
    assertFalse(generatedDisplaySettings[8].showDecoration)
    assertFalse(generatedDisplaySettings[9].showDecoration)
    assertFalse(generatedDisplaySettings[10].showDecoration)

    // Light/Dark are affected by show decoration flag
    assertFalse(generatedDisplaySettings[11].showDecoration)
    assertFalse(generatedDisplaySettings[12].showDecoration)

    // Colorblind filters are affected by show decoration flag
    // They show:
    // - Original
    // - Deuteranopes
    // - Deuteranomaly
    // - Tritanopes
    // - Tritanomaly
    // - Protanopes
    // - Protanomaly
    assertFalse(generatedDisplaySettings[13].showDecoration)
    assertFalse(generatedDisplaySettings[14].showDecoration)
    assertFalse(generatedDisplaySettings[15].showDecoration)
    assertFalse(generatedDisplaySettings[16].showDecoration)
    assertFalse(generatedDisplaySettings[17].showDecoration)
    assertFalse(generatedDisplaySettings[18].showDecoration)
    assertFalse(generatedDisplaySettings[19].showDecoration)
  }

  @Test
  fun `test calculate non Wear preview with decorations shown`() {
    val elementInstanceTest =
      SingleComposePreviewElementInstance.Companion.forTesting<SmartPsiElementPointer<PsiElement>>(
        "google.simpleapplication.MainActivityKt.DefaultPreview",
        showDecorations = true,
      )

    val previewElements =
      UiCheckModeFilter.Enabled.calculatePreviews(elementInstanceTest, isWearPreview = false)

    val generatedDisplaySettings = previewElements.toList().map { it.displaySettings }

    // Show decoration flag shouldn't affect screen sizes previews because show previews on
    // different phone sizes:
    // - Medium phone
    // - Unfolded phone
    // - Medium tablet
    // - Desktop
    // - Medium phone landscape
    assertTrue(generatedDisplaySettings[0].showDecoration)
    assertTrue(generatedDisplaySettings[1].showDecoration)
    assertTrue(generatedDisplaySettings[2].showDecoration)
    assertTrue(generatedDisplaySettings[3].showDecoration)
    assertTrue(generatedDisplaySettings[4].showDecoration)

    // Font scales are affected by show decoration flag change.
    // They show previews with different font sizes:
    // - 85%
    // - 100%
    // - 115%
    // - 130%
    // - 180%
    // - 200%
    assertTrue(generatedDisplaySettings[5].showDecoration)
    assertTrue(generatedDisplaySettings[6].showDecoration)
    assertTrue(generatedDisplaySettings[7].showDecoration)
    assertTrue(generatedDisplaySettings[8].showDecoration)
    assertTrue(generatedDisplaySettings[9].showDecoration)
    assertTrue(generatedDisplaySettings[10].showDecoration)

    // Light/Dark are affected by show decoration flag.
    assertTrue(generatedDisplaySettings[11].showDecoration)
    assertTrue(generatedDisplaySettings[12].showDecoration)

    // Colorblind filters are affected by show decoration flag.
    // They show:
    // - Original
    // - Deuteranopes
    // - Deuteranomaly
    // - Tritanopes
    // - Tritanomaly
    // - Protanopes
    // - Protanomaly
    assertTrue(generatedDisplaySettings[13].showDecoration)
    assertTrue(generatedDisplaySettings[14].showDecoration)
    assertTrue(generatedDisplaySettings[15].showDecoration)
    assertTrue(generatedDisplaySettings[16].showDecoration)
    assertTrue(generatedDisplaySettings[17].showDecoration)
    assertTrue(generatedDisplaySettings[18].showDecoration)
    assertTrue(generatedDisplaySettings[19].showDecoration)
  }

  @Test
  fun `test calculate Wear preview with no decorations shown`() {
    val elementInstanceTest =
      SingleComposePreviewElementInstance.Companion.forTesting<SmartPsiElementPointer<PsiElement>>(
        "google.simpleapplication.MainActivityKt.DefaultPreview",
        showDecorations = false,
      )

    val previewElements =
      UiCheckModeFilter.Enabled.calculatePreviews(elementInstanceTest, isWearPreview = true)

    // Either if changing show decoration Wear previews shouldn't change.
    assertWearPreviewElements(previewElements)
  }

  @Test
  fun `test calculate Wear preview with decorations shown`() {
    val elementInstanceTest =
      SingleComposePreviewElementInstance.Companion.forTesting<SmartPsiElementPointer<PsiElement>>(
        "google.simpleapplication.MainActivityKt.DefaultPreview",
        showDecorations = true,
      )

    val previewElements =
      UiCheckModeFilter.Enabled.calculatePreviews(elementInstanceTest, isWearPreview = true)

    // Either if changing show decoration Wear previews shouldn't change.
    assertWearPreviewElements(previewElements)
  }

  private fun assertWearPreviewElements(
    previewElements:
      Collection<SingleComposePreviewElementInstance<SmartPsiElementPointer<PsiElement>>>
  ) {
    val generatedDisplaySettings = previewElements.toList().map { it.displaySettings }

    // Screen sizes are affected by show decoration flag because They show Wear sizes.
    // They show different Wear sizes:
    // - Wear OS Large Round
    // - Wear 0S Small Round
    assertTrue(generatedDisplaySettings[0].showDecoration)
    assertTrue(generatedDisplaySettings[1].showDecoration)

    // Font scales are affected by show decoration.
    // They show Wear previews with different font sizes:
    // - Small
    // - Normal
    // - Medium
    // - Large
    // - Larger
    // - Largest
    assertTrue(generatedDisplaySettings[2].showDecoration)
    assertTrue(generatedDisplaySettings[3].showDecoration)
    assertTrue(generatedDisplaySettings[4].showDecoration)
    assertTrue(generatedDisplaySettings[5].showDecoration)
    assertTrue(generatedDisplaySettings[6].showDecoration)
    assertTrue(generatedDisplaySettings[7].showDecoration)

    // Colorblind filters are affected by show decoration flag
    // They show Wear previews with different colorblind filters:
    // - Original
    // - Deuteranopes
    // - Deuteranomaly
    // - Tritanopes
    // - Tritanomaly
    // - Protanopes
    // - Protanomaly
    assertTrue(generatedDisplaySettings[8].showDecoration)
    assertTrue(generatedDisplaySettings[9].showDecoration)
    assertTrue(generatedDisplaySettings[10].showDecoration)
    assertTrue(generatedDisplaySettings[11].showDecoration)
    assertTrue(generatedDisplaySettings[12].showDecoration)
    assertTrue(generatedDisplaySettings[13].showDecoration)
    assertTrue(generatedDisplaySettings[14].showDecoration)
  }
}
