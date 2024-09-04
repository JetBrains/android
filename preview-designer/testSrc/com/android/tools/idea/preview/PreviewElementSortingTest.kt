/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.preview

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.preview.ParametrizedComposePreviewElementInstance
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class PreviewElementSortingTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testPreviewSortingOneSingleInstance() {
    val singlePreviewElementInstance = previewInstance(name = "PreviewComposableName")

    runBlocking {
      val actual = listOf(singlePreviewElementInstance).sortByDisplayAndSourcePosition()
      val expected = listOf(singlePreviewElementInstance)
      assertEquals(actual, expected)
    }
  }

  @Test
  fun testPreviewSortingMultipleInstances() {
    val expectedPreviews =
      (0..3).map {
        ParametrizedComposePreviewElementInstance(
          previewInstance(name = "PreviewComposableName"),
          "param-$it",
          "ProviderClass",
          it,
          3,
        )
      }

    // Because we  want to check if we correctly sort the PreviewElement we shuffle
    // the previews
    val shuffledPreviews = expectedPreviews.shuffled()

    runBlocking {
      assertEquals(shuffledPreviews.sortByDisplayAndSourcePosition(), expectedPreviews)
    }
  }

  @Test
  fun testPreviewSortingGroupedPreviewsAreOrderedLexicographicallyByName() {
    val preview1 = previewInstance(name = "Preview1", group = "2")
    val preview2 = previewInstance(name = "Preview2", group = null)
    val preview3 = previewInstance(name = "Preview3", group = "2")
    val preview4 = previewInstance(name = "Preview4", group = "1")
    val preview5 = previewInstance(name = "Preview5", group = null)
    val preview6 = previewInstance(name = "Preview6", group = "3")

    val expectedPreviews = listOf(preview1, preview2, preview3, preview4, preview5, preview6)
    val shuffledPreviews = expectedPreviews.shuffled()

    runBlocking {
      assertEquals(shuffledPreviews.sortByDisplayAndSourcePosition(), expectedPreviews)
    }
  }

  private fun previewInstance(name: String, group: String? = null) =
    SingleComposePreviewElementInstance<SmartPsiElementPointer<PsiElement>>(
      methodFqn = "ComposableName",
      displaySettings =
        PreviewDisplaySettings(
          name = name,
          group = group,
          showDecoration = false,
          showBackground = false,
          backgroundColor = null,
        ),
      previewElementDefinition = null,
      previewBody = null,
      configuration = PreviewConfiguration.cleanAndGet(),
    )
}
