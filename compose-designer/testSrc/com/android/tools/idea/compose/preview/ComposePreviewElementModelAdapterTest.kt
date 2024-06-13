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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.junit.Assert
import org.junit.Test

class ComposePreviewElementModelAdapterTest {

  private val adapter =
    object : ComposePreviewElementModelAdapter() {
      override fun toXml(previewElement: PsiComposePreviewElementInstance) = ""

      override fun createDataContext(previewElement: PsiComposePreviewElementInstance) =
        DataContext {}
    }

  @Test
  fun testAffinity() {
    val composable0 =
      SingleComposePreviewElementInstance<SmartPsiElementPointer<PsiElement>>(
        "composableMethodName",
        PreviewDisplaySettings("A name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet(),
      )

    // The same as composable0, just a different instance
    val composable0b =
      SingleComposePreviewElementInstance<SmartPsiElementPointer<PsiElement>>(
        "composableMethodName",
        PreviewDisplaySettings("A name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet(),
      )

    // Same as composable0 but with different display settings
    val composable1 =
      SingleComposePreviewElementInstance<SmartPsiElementPointer<PsiElement>>(
        "composableMethodName",
        PreviewDisplaySettings("Different name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet(),
      )

    // Same as composable0 but with different display settings
    val composable2 =
      SingleComposePreviewElementInstance<SmartPsiElementPointer<PsiElement>>(
        "composableMethodName",
        PreviewDisplaySettings("Different name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet(),
      )

    val result =
      listOf(composable2, composable1, composable0b)
        .shuffled()
        .sortedBy { adapter.calcAffinity(it, composable0) }
        .toTypedArray()

    // The more similar, the lower result of modelAffinity.
    Assert.assertArrayEquals(arrayOf(composable0b, composable1, composable2), result)
  }

  @Test
  fun testCalcAffinityPriority() {
    val composable0 =
      SingleComposePreviewElementInstance<SmartPsiElementPointer<PsiElement>>(
        "composableMethodName",
        PreviewDisplaySettings("A name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet(),
      )

    // The same as composable0, just a different instance
    val composable0b =
      SingleComposePreviewElementInstance<SmartPsiElementPointer<PsiElement>>(
        "composableMethodName",
        PreviewDisplaySettings("A name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet(),
      )

    // Same as composable0 but with different display settings
    val composable1 =
      SingleComposePreviewElementInstance<SmartPsiElementPointer<PsiElement>>(
        "composableMethodName",
        PreviewDisplaySettings("Different name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet(),
      )

    // Different methodFqn as composable0 but with the same display settings
    val composable2 =
      SingleComposePreviewElementInstance<SmartPsiElementPointer<PsiElement>>(
        "composableMethodName2",
        PreviewDisplaySettings("A name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet(),
      )

    Assert.assertTrue(
      adapter.calcAffinity(composable0, composable0) ==
        adapter.calcAffinity(composable0, composable0b)
    )
    Assert.assertTrue(
      adapter.calcAffinity(composable0, composable0b) <
        adapter.calcAffinity(composable0, composable1)
    )
    Assert.assertTrue(
      adapter.calcAffinity(composable0, composable1) < adapter.calcAffinity(composable0, null)
    )
    Assert.assertTrue(
      adapter.calcAffinity(composable0, null) < adapter.calcAffinity(composable0, composable2)
    )
  }
}
