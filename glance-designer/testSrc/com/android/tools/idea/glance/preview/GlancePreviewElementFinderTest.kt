/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.glance.preview

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.intellij.psi.PsiFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GlancePreviewElementFinderTest {
  @get:Rule val projectRule: AndroidProjectRule = AndroidProjectRule.inMemory()

  private val project
    get() = projectRule.project

  private val fixture
    get() = projectRule.fixture

  private lateinit var sourceFileAppWidgets: PsiFile
  private lateinit var sourceFileAppWidgetsWithSize: PsiFile
  private lateinit var sourceFileNone: PsiFile

  @Before
  fun setUp() {
    fixture.addFileToProjectAndInvalidate(
      "androidx/glance/preview/Preview.kt",
      // language=kotlin
      """
        package androidx.glance.preview

        annotation class Preview(surface: String)
        """
        .trimIndent(),
    )

    fixture.addFileToProjectAndInvalidate(
      "androidx/compose/runtime/Composable.kt",
      // language=kotlin
      """
        package androidx.compose.runtime

        annotation class Composable
        """
        .trimIndent(),
    )

    sourceFileAppWidgets =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/SourceFileWidget.kt",
        // language=kotlin
        """
        package com.android.test

        import androidx.glance.preview.Preview
        import androidx.compose.runtime.Composable

        @Preview
        @Composable
        fun Foo31() { }
        """
          .trimIndent(),
      )

    sourceFileAppWidgetsWithSize =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/SourceFileWidgetWithSize.kt",
        // language=kotlin
        """
        package com.android.test

        import androidx.glance.preview.Preview
        import androidx.compose.runtime.Composable

        @Preview(widthDp = 1234, heightDp = 5678)
        @Composable
        fun Foo41() { }
        """
          .trimIndent(),
      )

    sourceFileNone =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/SourceFileNone.kt",
        // language=kotlin
        """
        package com.android.test

        import androidx.glance.preview.Preview
        import androidx.compose.runtime.Composable

        @Composable
        fun Foo51() { }
        """
          .trimIndent(),
      )
  }

  @Test
  fun testAppWidgetElementsFinder() = runBlocking {
    Assert.assertTrue(
      AppWidgetPreviewElementFinder.hasPreviewElements(project, sourceFileAppWidgets.virtualFile)
    )
    Assert.assertTrue(
      AppWidgetPreviewElementFinder.hasPreviewElements(
        project,
        sourceFileAppWidgetsWithSize.virtualFile,
      )
    )
    Assert.assertFalse(
      AppWidgetPreviewElementFinder.hasPreviewElements(project, sourceFileNone.virtualFile)
    )

    runBlocking {
      Assert.assertEquals(
        listOf("com.android.test.SourceFileWidgetKt.Foo31"),
        AppWidgetPreviewElementFinder.findPreviewElements(project, sourceFileAppWidgets.virtualFile)
          .map { it.methodFqn },
      )
      Assert.assertEquals(
        listOf("com.android.test.SourceFileWidgetWithSizeKt.Foo41"),
        AppWidgetPreviewElementFinder.findPreviewElements(
            project,
            sourceFileAppWidgetsWithSize.virtualFile,
          )
          .map { it.methodFqn },
      )
    }
  }

  @Test
  fun testFindsMultiPreviews() = runBlocking {
    fixture.addFileToProjectAndInvalidate(
      "com/android/test/SomeGlancePreviews.kt",
      // language=kotlin
      """
        package com.android.test

        import androidx.glance.preview.Preview

        @Preview(widthDp = 1234, heightDp = 5678)
        @Preview
        annotation class MultiPreviewFromOtherFile
        """
        .trimIndent(),
    )

    val multipreviewTest =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/MultiPreviewTest.kt",
        // language=kotlin
        """
        package com.android.test

        import androidx.glance.preview.Preview
        import androidx.compose.runtime.Composable

        @Preview(widthDp = 2, heightDp = 2)
        annotation class MultiPreviewLevel2

        @Preview(widthDp = 1, heightDp = 1)
        @MultiPreviewLevel2
        annotation class MultiPreviewLevel1

        @MultiPreviewFromOtherFile
        @Preview(widthDp = 0, heightDp = 0)
        @MultiPreviewLevel1
        @Composable
        fun GlancePreviewFun() { }
        """
          .trimIndent(),
      )

    Assert.assertTrue(
      AppWidgetPreviewElementFinder.hasPreviewElements(project, multipreviewTest.virtualFile)
    )

    val previewElements =
      AppWidgetPreviewElementFinder.findPreviewElements(project, multipreviewTest.virtualFile)

    Assert.assertEquals(5, previewElements.size)
    Assert.assertEquals(
      listOf("com.android.test.MultiPreviewTestKt.GlancePreviewFun"),
      previewElements.map { it.methodFqn }.distinct(),
    )
  }
}
