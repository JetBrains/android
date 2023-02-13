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

  private lateinit var sourceFileBoth: PsiFile
  private lateinit var sourceFileTiles: PsiFile
  private lateinit var sourceFileAppWidgets: PsiFile
  private lateinit var sourceFileNone: PsiFile

  @Before
  fun setUp() {
    fixture.addFileToProjectAndInvalidate(
      "androidx/glance/preview/GlancePreview.kt",
      // language=kotlin
      """
        package androidx.glance.preview

        annotation class GlancePreview(surface: String)
        """.trimIndent()
    )
    sourceFileBoth =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/SourceFileBoth.kt",
        // language=kotlin
        """
        package com.android.test

        import androidx.glance.preview.GlancePreview

        @GlancePreview("AppWidget")
        fun Foo1() { }

        @GlancePreview("Tile")
        fun Foo2() { }

        @GlancePreview("AppWidget")
        @GlancePreview("Tile")
        fun Foo3() { }

        fun Foo4() { }
        """.trimIndent()
      )

    sourceFileTiles =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/SourceFileTile.kt",
        // language=kotlin
        """
        package com.android.test

        import androidx.glance.preview.GlancePreview

        @GlancePreview("Tile")
        fun Foo21() { }
        """.trimIndent()
      )

    sourceFileAppWidgets =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/SourceFileWidget.kt",
        // language=kotlin
        """
        package com.android.test

        import androidx.glance.preview.GlancePreview

        @GlancePreview("AppWidget")
        fun Foo31() { }
        """.trimIndent()
      )

    sourceFileNone =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/SourceFileNone.kt",
        // language=kotlin
        """
        package com.android.test

        import androidx.glance.preview.GlancePreview

        fun Foo41() { }
        """.trimIndent()
      )
  }

  @Test
  fun testAppWidgetElementsFinder() = runBlocking {
    Assert.assertTrue(
      AppWidgetPreviewElementFinder.hasPreviewElements(project, sourceFileBoth.virtualFile)
    )
    Assert.assertFalse(
      AppWidgetPreviewElementFinder.hasPreviewElements(project, sourceFileTiles.virtualFile)
    )
    Assert.assertTrue(
      AppWidgetPreviewElementFinder.hasPreviewElements(project, sourceFileAppWidgets.virtualFile)
    )
    Assert.assertFalse(
      AppWidgetPreviewElementFinder.hasPreviewElements(project, sourceFileNone.virtualFile)
    )

    runBlocking {
      Assert.assertEquals(
        listOf("com.android.test.SourceFileBothKt.Foo1", "com.android.test.SourceFileBothKt.Foo3"),
        AppWidgetPreviewElementFinder.findPreviewElements(project, sourceFileBoth.virtualFile).map {
          it.methodFqcn
        }
      )
      Assert.assertEquals(
        listOf("com.android.test.SourceFileWidgetKt.Foo31"),
        AppWidgetPreviewElementFinder.findPreviewElements(project, sourceFileAppWidgets.virtualFile)
          .map { it.methodFqcn }
      )
    }
  }

  @Test
  fun testWearTileElementsFinder() = runBlocking {
    Assert.assertTrue(
      TilePreviewElementFinder.hasPreviewElements(project, sourceFileBoth.virtualFile)
    )
    Assert.assertTrue(
      TilePreviewElementFinder.hasPreviewElements(project, sourceFileTiles.virtualFile)
    )
    Assert.assertFalse(
      TilePreviewElementFinder.hasPreviewElements(project, sourceFileAppWidgets.virtualFile)
    )
    Assert.assertFalse(
      TilePreviewElementFinder.hasPreviewElements(project, sourceFileNone.virtualFile)
    )

    runBlocking {
      Assert.assertEquals(
        listOf("com.android.test.SourceFileBothKt.Foo2", "com.android.test.SourceFileBothKt.Foo3"),
        TilePreviewElementFinder.findPreviewElements(project, sourceFileBoth.virtualFile).map {
          it.methodFqcn
        }
      )
      Assert.assertEquals(
        listOf("com.android.test.SourceFileTileKt.Foo21"),
        TilePreviewElementFinder.findPreviewElements(project, sourceFileTiles.virtualFile).map {
          it.methodFqcn
        }
      )
    }
  }
}
