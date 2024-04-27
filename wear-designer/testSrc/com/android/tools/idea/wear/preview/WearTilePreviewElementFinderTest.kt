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

import com.android.ide.common.resources.Locale
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import kotlinx.coroutines.runBlocking
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WearTilePreviewElementFinderTest {
  @get:Rule val projectRule: AndroidProjectRule = AndroidProjectRule.inMemory()

  private val project
    get() = projectRule.project
  private val fixture
    get() = projectRule.fixture

  @Before
  fun setUp() {
    fixture.addFileToProjectAndInvalidate(
      "android/content/Context.kt",
      // language=kotlin
      """
        package android.content

        class Context
      """.trimIndent()
    )
    fixture.addFileToProjectAndInvalidate(
      "androidx/wear/tiles/tooling/preview/Preview.kt",
      // language=kotlin
      """
        package androidx.wear.tiles.tooling.preview

        import androidx.annotation.FloatRange

        object WearDevices {
            const val LARGE_ROUND = "id:wearos_large_round"
            const val SMALL_ROUND = "id:wearos_small_round"
            const val SQUARE = "id:wearos_square"
            const val RECT = "id:wearos_rect"
        }

        class TilePreviewData

        annotation class Preview(
            val name: String = "",
            val group: String = "",
            val device: String = WearDevices.SMALL_ROUND,
            val locale: String = "",
            @FloatRange(from = 0.01) val fontScale: Float = 1f,
        )
        """
        .trimIndent()
    )
  }

  @Test
  fun testWearTileElementsFinder() = runBlocking {
    val previewsTest =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/Src.kt",
        // language=kotlin
        """
        package com.android.test

        import android.content.Context
        import androidx.wear.tiles.TileService
        import androidx.wear.tiles.tooling.preview.Preview
        import androidx.wear.tiles.tooling.preview.TilePreviewData
        import androidx.wear.tiles.tooling.preview.WearDevices

        @Preview
        class ThisShouldNotBePreviewed : TileService

        @Preview
        private fun tilePreview(): TilePreviewData {
          return TilePreviewData()
        }

        @Preview(
          device = WearDevices.LARGE_ROUND
        )
        private fun largeRoundTilePreview(): TilePreviewData {
          return TilePreviewData()
        }

        @Preview(
          name = "some name"
        )
        private fun namedTilePreview(): TilePreviewData {
          return TilePreviewData()
        }

        @Preview(
          group = "some group",
          device = WearDevices.SQUARE
        )
        private fun tilePreviewWithGroup(): TilePreviewData {
          return TilePreviewData()
        }

        fun someRandomMethod() {
        }

        fun anotherRandomMethodReturningTilePreviewData(): TilePreviewData {
          return TilePreviewData()
        }

        @Preview(
          locale = "fr"
        )
        private fun tilePreviewWithLocale(): TilePreviewData {
          return TilePreviewData()
        }

        @Preview(
          fontScale = 1.2f
        )
        private fun tilePreviewWithFontScale(): TilePreviewData {
          return TilePreviewData()
        }

        @Preview
        fun tilePreviewWithParameter(x: Int): TilePreviewData {
          return TilePreviewData()
        }

        @Preview
        fun tilePreviewWithWrongReturnType(): Int {
          return 42
        }

        @Preview
        fun tilePreviewWithNoReturnType() {
        }

        @Preview
        fun tilePreviewWithContextParameter(context: Context): TilePreviewData {
          return TilePreviewData()
        }

        @Preview
        fun tilePreviewWithTooManyParameters(context: Context, x: Int): TilePreviewData {
          return TilePreviewData()
        }
        """
          .trimIndent()
      )

    val otherFileTest =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/OtherFile.kt",
        // language=kotlin
        """
        package com.android.test

        import androidx.wear.tiles.tooling.preview.Preview
        import androidx.wear.tiles.tooling.preview.TilePreviewData

        @Preview
        private fun tilePreviewInAnotherFile(): TilePreviewData {
          return TilePreviewData()
        }
        """
          .trimIndent()
      )

    val fileWithNoPreviews =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/SourceFileNone.kt",
        // language=kotlin
        """
        package com.android.test

        import androidx.wear.tiles.TileService

        class WearTileService : TileService

        fun someRandomMethod() {
        }
        """
          .trimIndent()
      )

    assertThat(WearTilePreviewElementFinder.hasPreviewElements(project, previewsTest.virtualFile))
      .isTrue()
    assertThat(WearTilePreviewElementFinder.hasPreviewElements(project, otherFileTest.virtualFile))
      .isTrue()
    assertThat(
        WearTilePreviewElementFinder.hasPreviewElements(project, fileWithNoPreviews.virtualFile)
      )
      .isFalse()

    runBlocking {
      val previewElements =
        WearTilePreviewElementFinder.findPreviewElements(project, previewsTest.virtualFile)
      assertThat(previewElements).hasSize(7)

      previewElements.elementAt(0).let {
        assertThat(it.displaySettings.name).isEqualTo("tilePreview")
        assertThat(it.displaySettings.group).isNull()
        assertThat(it.displaySettings.showBackground).isTrue()
        assertThat(it.displaySettings.showDecoration).isFalse()
        assertThat(it.displaySettings.backgroundColor).isEqualTo("#ff000000")
        assertThat(it.configuration.device).isEqualTo("id:wearos_small_round")
        assertThat(it.configuration.locale).isNull()
        assertThat(it.configuration.fontScale).isEqualTo(1f)

        ReadAction.run<Throwable> {
          assertThat(TextRange.create(it.previewBodyPsi!!.psiRange!!))
            .isEqualTo(previewsTest.textRange("tilePreview"))
          assertThat(it.previewElementDefinitionPsi?.element?.text).isEqualTo("@Preview")
        }
      }

      previewElements.elementAt(1).let {
        assertThat(it.displaySettings.name).isEqualTo("largeRoundTilePreview")
        assertThat(it.displaySettings.group).isNull()
        assertThat(it.displaySettings.showBackground).isTrue()
        assertThat(it.displaySettings.showDecoration).isFalse()
        assertThat(it.displaySettings.backgroundColor).isEqualTo("#ff000000")
        assertThat(it.configuration.device).isEqualTo("id:wearos_large_round")
        assertThat(it.configuration.locale).isNull()
        assertThat(it.configuration.fontScale).isEqualTo(1f)

        ReadAction.run<Throwable> {
          assertThat(TextRange.create(it.previewBodyPsi!!.psiRange!!))
            .isEqualTo(previewsTest.textRange("largeRoundTilePreview"))
          assertThat(it.previewElementDefinitionPsi?.element?.text)
            .isEqualTo(
              """
            @Preview(
              device = WearDevices.LARGE_ROUND
            )
          """
                .trimIndent()
            )
        }
      }

      previewElements.elementAt(2).let {
        assertThat(it.displaySettings.name).isEqualTo("namedTilePreview - some name")
        assertThat(it.displaySettings.group).isNull()
        assertThat(it.displaySettings.showBackground).isTrue()
        assertThat(it.displaySettings.showDecoration).isFalse()
        assertThat(it.displaySettings.backgroundColor).isEqualTo("#ff000000")
        assertThat(it.configuration.device).isEqualTo("id:wearos_small_round")
        assertThat(it.configuration.locale).isNull()
        assertThat(it.configuration.fontScale).isEqualTo(1f)

        ReadAction.run<Throwable> {
          assertThat(TextRange.create(it.previewBodyPsi!!.psiRange!!))
            .isEqualTo(previewsTest.textRange("namedTilePreview"))
          assertThat(it.previewElementDefinitionPsi?.element?.text)
            .isEqualTo(
              """
            @Preview(
              name = "some name"
            )
          """
                .trimIndent()
            )
        }
      }

      previewElements.elementAt(3).let {
        assertThat(it.displaySettings.name).isEqualTo("tilePreviewWithGroup")
        assertThat(it.displaySettings.group).isEqualTo("some group")
        assertThat(it.displaySettings.showBackground).isTrue()
        assertThat(it.displaySettings.showDecoration).isFalse()
        assertThat(it.displaySettings.backgroundColor).isEqualTo("#ff000000")
        assertThat(it.configuration.device).isEqualTo("id:wearos_square")
        assertThat(it.configuration.locale).isNull()
        assertThat(it.configuration.fontScale).isEqualTo(1f)

        ReadAction.run<Throwable> {
          assertThat(TextRange.create(it.previewBodyPsi!!.psiRange!!))
            .isEqualTo(previewsTest.textRange("tilePreviewWithGroup"))
          assertThat(it.previewElementDefinitionPsi?.element?.text)
            .isEqualTo(
              """
            @Preview(
              group = "some group",
              device = WearDevices.SQUARE
            )
          """
                .trimIndent()
            )
        }
      }
      previewElements.elementAt(4).let {
        assertThat(it.displaySettings.name).isEqualTo("tilePreviewWithLocale")
        assertThat(it.displaySettings.group).isNull()
        assertThat(it.displaySettings.showBackground).isTrue()
        assertThat(it.displaySettings.showDecoration).isFalse()
        assertThat(it.displaySettings.backgroundColor).isEqualTo("#ff000000")
        assertThat(it.configuration.device).isEqualTo("id:wearos_small_round")
        assertThat(it.configuration.locale).isEqualTo(Locale.create("fr"))
        assertThat(it.configuration.fontScale).isEqualTo(1f)

        ReadAction.run<Throwable> {
          assertThat(TextRange.create(it.previewBodyPsi!!.psiRange!!))
            .isEqualTo(previewsTest.textRange("tilePreviewWithLocale"))
          assertThat(it.previewElementDefinitionPsi?.element?.text)
            .isEqualTo(
              """
           @Preview(
             locale = "fr"
           )
         """
                .trimIndent()
            )
        }
      }
      previewElements.elementAt(5).let {
        assertThat(it.displaySettings.name).isEqualTo("tilePreviewWithFontScale")
        assertThat(it.displaySettings.group).isNull()
        assertThat(it.displaySettings.showBackground).isTrue()
        assertThat(it.displaySettings.showDecoration).isFalse()
        assertThat(it.displaySettings.backgroundColor).isEqualTo("#ff000000")
        assertThat(it.configuration.device).isEqualTo("id:wearos_small_round")
        assertThat(it.configuration.locale).isNull()
        assertThat(it.configuration.fontScale).isEqualTo(1.2f)

        ReadAction.run<Throwable> {
          assertThat(TextRange.create(it.previewBodyPsi!!.psiRange!!))
            .isEqualTo(previewsTest.textRange("tilePreviewWithFontScale"))
          assertThat(it.previewElementDefinitionPsi?.element?.text)
            .isEqualTo(
              """
           @Preview(
             fontScale = 1.2f
           )
         """
                .trimIndent()
            )
        }
      }
      previewElements.elementAt(6).let {
        assertThat(it.displaySettings.name).isEqualTo("tilePreviewWithContextParameter")
        assertThat(it.displaySettings.group).isNull()
        assertThat(it.displaySettings.showBackground).isTrue()
        assertThat(it.displaySettings.showDecoration).isFalse()
        assertThat(it.displaySettings.backgroundColor).isEqualTo("#ff000000")
        assertThat(it.configuration.device).isEqualTo("id:wearos_small_round")
        assertThat(it.configuration.locale).isNull()
        assertThat(it.configuration.fontScale).isEqualTo(1f)

        ReadAction.run<Throwable> {
          assertThat(TextRange.create(it.previewBodyPsi!!.psiRange!!))
            .isEqualTo(previewsTest.textRange("tilePreviewWithContextParameter"))
          assertThat(it.previewElementDefinitionPsi?.element?.text)
            .isEqualTo("@Preview")
        }
      }
    }
  }
}

private fun PsiFile.textRange(methodName: String): TextRange {
  return ReadAction.compute<TextRange, Throwable> {
    toUElementOfType<UFile>()?.method(methodName)?.uastBody?.sourcePsi?.textRange!!
  }
}

private fun UFile.declaredMethods(): Sequence<UMethod> =
  classes.asSequence().flatMap { it.methods.asSequence() }

private fun UFile.method(name: String): UMethod? =
  declaredMethods().filter { it.name == name }.singleOrNull()
