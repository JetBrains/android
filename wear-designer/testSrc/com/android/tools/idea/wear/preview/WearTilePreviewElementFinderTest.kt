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
package com.android.tools.idea.wear.preview

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.intellij.psi.PsiFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WearTilePreviewElementFinderTest {
  @get:Rule
  val projectRule: AndroidProjectRule = AndroidProjectRule.inMemory()

  private val project
    get() = projectRule.project
  private val fixture
    get() = projectRule.fixture

  private lateinit var sourceFileTwo: PsiFile
  private lateinit var sourceFileSolo: PsiFile
  private lateinit var sourceFileNone: PsiFile

  @Before
  fun setUp() {
    fixture.addFileToProjectAndInvalidate(
      "androidx/wear/tiles/TileService.kt",
      // language=kotlin
      """
        package androidx.wear.tiles

        class TileService {}
        """
        .trimIndent()
    )
    sourceFileTwo =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/SourceFileTwo.kt",
        // language=kotlin
        """
        package com.android.test

        import androidx.wear.tiles.TileService

        class MyTile1 : TileService {

        }

        class MyTile2 : TileService {

        }

        """
          .trimIndent()
      )

    sourceFileSolo =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/SourceFileOne.kt",
        // language=kotlin
        """
        package com.android.test

        import androidx.wear.tiles.TileService

        class MyTileSolo : TileService {

        }
        """
          .trimIndent()
      )

    sourceFileNone =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/SourceFileNone.kt",
        // language=kotlin
        """
        package com.android.test

        import androidx.wear.tiles.TileService

        class NotWearTile {}
        """
          .trimIndent()
      )
  }

  @Test
  fun testWearTileElementsFinder() = runBlocking {
    Assert.assertTrue(
      WearTilePreviewElementFinder.hasPreviewElements(project, sourceFileTwo.virtualFile)
    )
    Assert.assertTrue(
      WearTilePreviewElementFinder.hasPreviewElements(project, sourceFileSolo.virtualFile)
    )
    Assert.assertFalse(
      WearTilePreviewElementFinder.hasPreviewElements(project, sourceFileNone.virtualFile)
    )

    runBlocking {
      Assert.assertEquals(
        listOf("com.android.test.MyTile1", "com.android.test.MyTile2"),
        WearTilePreviewElementFinder.findPreviewElements(project, sourceFileTwo.virtualFile).map {
          it.fqcn
        }
      )
      Assert.assertEquals(
        listOf("com.android.test.MyTileSolo"),
        WearTilePreviewElementFinder.findPreviewElements(project, sourceFileSolo.virtualFile)
          .map { it.fqcn }
      )
    }
  }
}