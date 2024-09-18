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

import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_APP
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_LIBRARY
import com.android.tools.idea.preview.sortByDisplayAndSourcePosition
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder.Companion.rootModuleBuilder
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.android.tools.idea.wear.preview.WearTilePreviewElementSubject.Companion.assertThat
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.jetbrains.rd.generator.nova.fail
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.atMost
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WearTilePreviewElementFinderTest {
  @get:Rule
  val projectRule: AndroidProjectRule =
    AndroidProjectRule.withAndroidModels(
      rootModuleBuilder,
      AndroidModuleModelBuilder(
        ":lib",
        "debug",
        AndroidProjectBuilder(projectType = { PROJECT_TYPE_LIBRARY }),
      ),
      AndroidModuleModelBuilder(
        ":app",
        "debug",
        AndroidProjectBuilder(
          projectType = { PROJECT_TYPE_APP },
          androidModuleDependencyList = {
            listOf(AndroidModuleDependency(moduleGradlePath = ":lib", variant = "debug"))
          },
        ),
      ),
    )

  private val project
    get() = projectRule.project

  private val fixture
    get() = projectRule.fixture

  private val elementFinder = WearTilePreviewElementFinder()

  @Before
  fun setUp() {
    fixture.addFileToProjectAndInvalidate(
      "lib/src/main/java/android/content/Context.kt",
      // language=kotlin
      """
        package android.content

        class Context
      """
        .trimIndent(),
    )
    fixture.stubWearTilePreviewAnnotation("lib")
  }

  @Test
  fun testWearTileElementsFinder() = runBlocking {
    val previewsTest =
      fixture.addFileToProjectAndInvalidate(
        "app/src/main/java/com/android/test/Src.kt",
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

        @Preview
        @Preview(device = WearDevices.LARGE_ROUND)
        @Preview(name = "some name")
        fun tilePreviewWithMultipleAnnotations(): TilePreviewData {
          return TilePreviewData()
        }
        """
          .trimIndent(),
      )

    val otherFileTest =
      fixture.addFileToProjectAndInvalidate(
        "app/src/main/java/com/android/test/OtherFile.kt",
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
          .trimIndent(),
      )

    val fileWithNoPreviews =
      fixture.addFileToProjectAndInvalidate(
        "app/src/main/java/com/android/test/SourceFileNone.kt",
        // language=kotlin
        """
        package com.android.test

        import androidx.wear.tiles.TileService
        import androidx.wear.tiles.tooling.preview.TilePreviewData

        class WearTileService : TileService

        fun someRandomMethod() {
        }

        fun someMethodWithATilePreviewSignatureAndWithoutAPreviewAnnotation(): TilePreviewData {
          return TilePreviewData()
        }
        """
          .trimIndent(),
      )

    assertThat(elementFinder.hasPreviewElements(project, previewsTest.virtualFile)).isTrue()
    assertThat(elementFinder.hasPreviewElements(project, otherFileTest.virtualFile)).isTrue()
    assertThat(elementFinder.hasPreviewElements(project, fileWithNoPreviews.virtualFile)).isFalse()

    runBlocking {
      val previewElements = elementFinder.findPreviewElements(project, previewsTest.virtualFile)
      assertThat(previewElements).hasSize(10)

      previewElements.elementAt(0).let {
        assertThat(it)
          .hasDisplaySettings(
            defaultDisplaySettings(
              name = "tilePreview",
              baseName = "tilePreview",
              parameterName = null,
            )
          )
        assertThat(it)
          .hasPreviewConfiguration(defaultConfiguration(device = "id:wearos_small_round"))
        assertThat(it).previewBodyHasTextRange(previewsTest.textRange("tilePreview"))
        assertThat(it).hasAnnotationDefinition("@Preview")
      }

      previewElements.elementAt(1).let {
        assertThat(it)
          .hasDisplaySettings(
            defaultDisplaySettings(
              name = "largeRoundTilePreview",
              baseName = "largeRoundTilePreview",
              parameterName = null,
            )
          )
        assertThat(it)
          .hasPreviewConfiguration(defaultConfiguration(device = "id:wearos_large_round"))
        assertThat(it).previewBodyHasTextRange(previewsTest.textRange("largeRoundTilePreview"))
        assertThat(it)
          .hasAnnotationDefinition(
            """
            @Preview(
              device = WearDevices.LARGE_ROUND
            )
          """
              .trimIndent()
          )
      }

      previewElements.elementAt(2).let {
        assertThat(it)
          .hasDisplaySettings(
            defaultDisplaySettings(
              name = "namedTilePreview - some name",
              baseName = "namedTilePreview",
              parameterName = "some name",
            )
          )
        assertThat(it).hasPreviewConfiguration(defaultConfiguration())
        assertThat(it).previewBodyHasTextRange(previewsTest.textRange("namedTilePreview"))
        assertThat(it)
          .hasAnnotationDefinition(
            """
            @Preview(
              name = "some name"
            )
          """
              .trimIndent()
          )
      }

      previewElements.elementAt(3).let {
        assertThat(it)
          .hasDisplaySettings(
            defaultDisplaySettings(
              name = "tilePreviewWithGroup",
              baseName = "tilePreviewWithGroup",
              parameterName = null,
              group = "some group",
            )
          )
        assertThat(it).hasPreviewConfiguration(defaultConfiguration(device = "id:wearos_square"))
        assertThat(it).previewBodyHasTextRange(previewsTest.textRange("tilePreviewWithGroup"))
        assertThat(it)
          .hasAnnotationDefinition(
            """
            @Preview(
              group = "some group",
              device = WearDevices.SQUARE
            )
          """
              .trimIndent()
          )
      }
      previewElements.elementAt(4).let {
        assertThat(it)
          .hasDisplaySettings(
            defaultDisplaySettings(
              name = "tilePreviewWithLocale",
              baseName = "tilePreviewWithLocale",
              parameterName = null,
            )
          )
        assertThat(it).hasPreviewConfiguration(defaultConfiguration(locale = "fr"))
        assertThat(it).previewBodyHasTextRange(previewsTest.textRange("tilePreviewWithLocale"))
        assertThat(it)
          .hasAnnotationDefinition(
            """
            @Preview(
              locale = "fr"
            )
          """
              .trimIndent()
          )
      }
      previewElements.elementAt(5).let {
        assertThat(it)
          .hasDisplaySettings(
            defaultDisplaySettings(
              name = "tilePreviewWithFontScale",
              baseName = "tilePreviewWithFontScale",
              parameterName = null,
            )
          )
        assertThat(it).hasPreviewConfiguration(defaultConfiguration(fontScale = 1.2f))
        assertThat(it).previewBodyHasTextRange(previewsTest.textRange("tilePreviewWithFontScale"))
        assertThat(it)
          .hasAnnotationDefinition(
            """
            @Preview(
              fontScale = 1.2f
            )
          """
              .trimIndent()
          )
      }
      previewElements.elementAt(6).let {
        assertThat(it)
          .hasDisplaySettings(
            defaultDisplaySettings(
              name = "tilePreviewWithContextParameter",
              baseName = "tilePreviewWithContextParameter",
              parameterName = null,
            )
          )
        assertThat(it).hasPreviewConfiguration(defaultConfiguration())
        assertThat(it)
          .previewBodyHasTextRange(previewsTest.textRange("tilePreviewWithContextParameter"))
        assertThat(it).hasAnnotationDefinition("@Preview")
      }
      previewElements.elementAt(7).let {
        assertThat(it)
          .hasDisplaySettings(
            defaultDisplaySettings(
              name = "tilePreviewWithMultipleAnnotations",
              baseName = "tilePreviewWithMultipleAnnotations",
              parameterName = null,
            )
          )
        assertThat(it).hasPreviewConfiguration(defaultConfiguration())
        assertThat(it)
          .previewBodyHasTextRange(previewsTest.textRange("tilePreviewWithMultipleAnnotations"))
        assertThat(it).hasAnnotationDefinition("@Preview")
      }
      previewElements.elementAt(8).let {
        assertThat(it)
          .hasDisplaySettings(
            defaultDisplaySettings(
              name = "tilePreviewWithMultipleAnnotations",
              baseName = "tilePreviewWithMultipleAnnotations",
              parameterName = null,
            )
          )
        assertThat(it)
          .hasPreviewConfiguration(defaultConfiguration(device = "id:wearos_large_round"))
        assertThat(it)
          .previewBodyHasTextRange(previewsTest.textRange("tilePreviewWithMultipleAnnotations"))
        assertThat(it).hasAnnotationDefinition("@Preview(device = WearDevices.LARGE_ROUND)")
      }
      previewElements.elementAt(9).let {
        assertThat(it)
          .hasDisplaySettings(
            defaultDisplaySettings(
              name = "tilePreviewWithMultipleAnnotations - some name",
              baseName = "tilePreviewWithMultipleAnnotations",
              parameterName = "some name",
            )
          )
        assertThat(it).hasPreviewConfiguration(defaultConfiguration())
        assertThat(it)
          .previewBodyHasTextRange(previewsTest.textRange("tilePreviewWithMultipleAnnotations"))
        assertThat(it).hasAnnotationDefinition("@Preview(name = \"some name\")")
      }
    }
  }

  @Test
  fun testWearTileElementsFinderFindsAliasImports() = runBlocking {
    val previewsTest =
      fixture.addFileToProjectAndInvalidate(
        "app/src/main/java/com/android/test/Src.kt",
        // language=kotlin
        """
        package com.android.test

        import android.content.Context
        import androidx.wear.tiles.TileService
        import androidx.wear.tiles.tooling.preview.Preview as PreviewAlias
        import androidx.wear.tiles.tooling.preview.TilePreviewData
        import androidx.wear.tiles.tooling.preview.WearDevices

        @PreviewAlias
        private fun tilePreview(): TilePreviewData {
          return TilePreviewData()
        }
        """
          .trimIndent(),
      )

    assertThat(elementFinder.hasPreviewElements(project, previewsTest.virtualFile)).isTrue()

    runBlocking {
      val previewElements = elementFinder.findPreviewElements(project, previewsTest.virtualFile)
      assertThat(previewElements).hasSize(1)

      previewElements.first().let {
        assertThat(it)
          .hasDisplaySettings(
            defaultDisplaySettings(
              name = "tilePreview",
              baseName = "tilePreview",
              parameterName = null,
            )
          )
        assertThat(it).hasPreviewConfiguration(defaultConfiguration())
        assertThat(it).previewBodyHasTextRange(previewsTest.textRange("tilePreview"))
        assertThat(it).hasAnnotationDefinition("@PreviewAlias")
      }
    }
  }

  @Test
  fun testWearTileElementsFinderFindsJavaPreviews() = runBlocking {
    val previewsTest =
      fixture.addFileToProjectAndInvalidate(
        "app/src/main/java/com/android/test/JavaPreview.java",
        // language=java
        """
        package com.android.test;

        import androidx.wear.tiles.tooling.preview.Preview;
        import androidx.wear.tiles.tooling.preview.TilePreviewData;

        class JavaPreview {
          @Preview
          private TilePreviewData tilePreviewInJavaFile() {
            return new TilePreviewData();
          }
        }
        """
          .trimIndent(),
      )

    assertThat(elementFinder.hasPreviewElements(project, previewsTest.virtualFile)).isTrue()

    runBlocking {
      val previewElements = elementFinder.findPreviewElements(project, previewsTest.virtualFile)
      assertThat(previewElements).hasSize(1)

      previewElements.elementAt(0).let {
        assertThat(it)
          .hasDisplaySettings(
            defaultDisplaySettings(
              name = "tilePreviewInJavaFile",
              baseName = "tilePreviewInJavaFile",
              parameterName = null,
            )
          )
        assertThat(it).hasPreviewConfiguration(defaultConfiguration())
        assertThat(it).previewBodyHasTextRange(previewsTest.textRange("tilePreviewInJavaFile"))
        assertThat(it).hasAnnotationDefinition("@Preview")
      }
    }
  }

  @Test
  fun testFindsMultiPreviews() = runBlocking {
    fixture.addFileToProjectAndInvalidate(
      "app/src/main/java/com/android/test/AllWearDevices.kt",
      // language=kotlin
      """
        package com.android.test

        import androidx.wear.tiles.tooling.preview.Preview
        import androidx.wear.tiles.tooling.preview.WearDevices

        @Preview(device = WearDevices.LARGE_ROUND)
        @Preview(device = WearDevices.SMALL_ROUND)
        @Preview(device = WearDevices.SQUARE)
        @Preview(device = WearDevices.RECT)
        annotation class AllWearDevices
        """
        .trimIndent(),
    )

    val previewsTest =
      fixture.addFileToProjectAndInvalidate(
        "app/src/main/java/com/android/test/Src.kt",
        // language=kotlin
        """
        package com.android.test

        import android.content.Context
        import androidx.wear.tiles.TileService
        import androidx.wear.tiles.tooling.preview.Preview
        import androidx.wear.tiles.tooling.preview.TilePreviewData
        import androidx.wear.tiles.tooling.preview.WearDevices

        @Preview(name = "multipreview level 2")
        annotation class MultiPreviewLevel2

        @Preview(name = "multipreview level 1")
        @MultiPreviewLevel2
        annotation class MultiPreviewLevel1

        @AllWearDevices
        @Preview(name = "some preview")
        @MultiPreviewLevel1
        private fun tilePreview(): TilePreviewData {
          return TilePreviewData()
        }
        """
          .trimIndent(),
      )

    val previewsWithoutDirectUseOfPreviewTest =
      fixture.addFileToProjectAndInvalidate(
        "app/src/main/java/com/android/test/OtherSrc.kt",
        // language=kotlin
        """
        package com.android.test

        import androidx.wear.tiles.tooling.preview.TilePreviewData

        @MultiPreviewLevel1
        private fun tileWithMultiPreviewAnnotationFromAnotherFile(): TilePreviewData {
          return TilePreviewData()
        }

        """
          .trimIndent(),
      )

    assertTrue(elementFinder.hasPreviewElements(project, previewsTest.virtualFile))
    assertTrue(
      elementFinder.hasPreviewElements(project, previewsWithoutDirectUseOfPreviewTest.virtualFile)
    )

    val previewElements = elementFinder.findPreviewElements(project, previewsTest.virtualFile)
    assertThat(previewElements).hasSize(7)

    previewElements.elementAt(0).let {
      assertThat(it)
        .hasDisplaySettings(
          defaultDisplaySettings(
            name = "tilePreview - AllWearDevices 1",
            baseName = "tilePreview",
            parameterName = "AllWearDevices 1",
          )
        )
      assertThat(it).hasPreviewConfiguration(defaultConfiguration(device = "id:wearos_large_round"))
      assertThat(it).previewBodyHasTextRange(previewsTest.textRange("tilePreview"))
      assertThat(it).hasAnnotationDefinition("@AllWearDevices")
    }
    previewElements.elementAt(1).let {
      assertThat(it)
        .hasDisplaySettings(
          defaultDisplaySettings(
            name = "tilePreview - AllWearDevices 2",
            baseName = "tilePreview",
            parameterName = "AllWearDevices 2",
          )
        )
      assertThat(it).hasPreviewConfiguration(defaultConfiguration(device = "id:wearos_small_round"))
      assertThat(it).previewBodyHasTextRange(previewsTest.textRange("tilePreview"))
      assertThat(it).hasAnnotationDefinition("@AllWearDevices")
    }
    previewElements.elementAt(2).let {
      assertThat(it)
        .hasDisplaySettings(
          defaultDisplaySettings(
            name = "tilePreview - AllWearDevices 3",
            baseName = "tilePreview",
            parameterName = "AllWearDevices 3",
          )
        )
      assertThat(it).hasPreviewConfiguration(defaultConfiguration(device = "id:wearos_square"))
      assertThat(it).previewBodyHasTextRange(previewsTest.textRange("tilePreview"))
      assertThat(it).hasAnnotationDefinition("@AllWearDevices")
    }
    previewElements.elementAt(3).let {
      assertThat(it)
        .hasDisplaySettings(
          defaultDisplaySettings(
            name = "tilePreview - AllWearDevices 4",
            baseName = "tilePreview",
            parameterName = "AllWearDevices 4",
          )
        )
      assertThat(it).hasPreviewConfiguration(defaultConfiguration(device = "id:wearos_rect"))
      assertThat(it).previewBodyHasTextRange(previewsTest.textRange("tilePreview"))
      assertThat(it).hasAnnotationDefinition("@AllWearDevices")
    }
    previewElements.elementAt(4).let {
      assertThat(it)
        .hasDisplaySettings(
          defaultDisplaySettings(
            name = "tilePreview - some preview",
            baseName = "tilePreview",
            parameterName = "some preview",
          )
        )
      assertThat(it).hasPreviewConfiguration(defaultConfiguration())
      assertThat(it).previewBodyHasTextRange(previewsTest.textRange("tilePreview"))
      assertThat(it).hasAnnotationDefinition("@Preview(name = \"some preview\")")
    }
    previewElements.elementAt(5).let {
      assertThat(it)
        .hasDisplaySettings(
          defaultDisplaySettings(
            name = "tilePreview - multipreview level 1",
            baseName = "tilePreview",
            parameterName = "multipreview level 1",
          )
        )
      assertThat(it).hasPreviewConfiguration(defaultConfiguration())
      assertThat(it).previewBodyHasTextRange(previewsTest.textRange("tilePreview"))
      assertThat(it).hasAnnotationDefinition("@MultiPreviewLevel1")
    }
    previewElements.elementAt(6).let {
      assertThat(it)
        .hasDisplaySettings(
          defaultDisplaySettings(
            name = "tilePreview - multipreview level 2",
            baseName = "tilePreview",
            parameterName = "multipreview level 2",
          )
        )
      assertThat(it).hasPreviewConfiguration(defaultConfiguration())
      assertThat(it).previewBodyHasTextRange(previewsTest.textRange("tilePreview"))
      assertThat(it).hasAnnotationDefinition("@MultiPreviewLevel1")
    }

    val previewsWithoutDirectUseOfPreview =
      elementFinder.findPreviewElements(project, previewsWithoutDirectUseOfPreviewTest.virtualFile)
    assertThat(previewsWithoutDirectUseOfPreview).hasSize(2)

    previewsWithoutDirectUseOfPreview.elementAt(0).let {
      assertThat(it)
        .hasDisplaySettings(
          defaultDisplaySettings(
            name = "tileWithMultiPreviewAnnotationFromAnotherFile - multipreview level 1",
            baseName = "tileWithMultiPreviewAnnotationFromAnotherFile",
            parameterName = "multipreview level 1",
          )
        )
      assertThat(it).hasPreviewConfiguration(defaultConfiguration())
      assertThat(it)
        .previewBodyHasTextRange(
          previewsWithoutDirectUseOfPreviewTest.textRange(
            "tileWithMultiPreviewAnnotationFromAnotherFile"
          )
        )
      assertThat(it).hasAnnotationDefinition("@MultiPreviewLevel1")
    }
    previewsWithoutDirectUseOfPreview.elementAt(1).let {
      assertThat(it)
        .hasDisplaySettings(
          defaultDisplaySettings(
            name = "tileWithMultiPreviewAnnotationFromAnotherFile - multipreview level 2",
            baseName = "tileWithMultiPreviewAnnotationFromAnotherFile",
            parameterName = "multipreview level 2",
          )
        )
      assertThat(it).hasPreviewConfiguration(defaultConfiguration())
      assertThat(it)
        .previewBodyHasTextRange(
          previewsWithoutDirectUseOfPreviewTest.textRange(
            "tileWithMultiPreviewAnnotationFromAnotherFile"
          )
        )
      assertThat(it).hasAnnotationDefinition("@MultiPreviewLevel1")
    }
  }

  @Test
  fun testFindsMultiPreviewsFromLibrary() = runBlocking {
    fixture.addFileToProjectAndInvalidate(
      "lib/src/main/java/com/android/test/AllWearDevices.kt",
      // language=kotlin
      """
        package com.android.test

        import androidx.wear.tiles.tooling.preview.Preview
        import androidx.wear.tiles.tooling.preview.WearDevices

        @Preview
        annotation class MultiPreview
        """
        .trimIndent(),
    )

    val testFile =
      fixture.addFileToProjectAndInvalidate(
        "app/src/main/java/com/android/test/Src.kt",
        // language=kotlin
        """
        package com.android.test

        import androidx.wear.tiles.tooling.preview.TilePreviewData

        @MultiPreview
        private fun tilePreview(): TilePreviewData {
          return TilePreviewData()
        }
        """
          .trimIndent(),
      )

    assertTrue(elementFinder.hasPreviewElements(project, testFile.virtualFile))

    val previewElements = elementFinder.findPreviewElements(project, testFile.virtualFile)
    assertThat(previewElements).hasSize(1)

    previewElements.single().let {
      assertThat(it)
        .hasDisplaySettings(
          defaultDisplaySettings(
            name = "tilePreview - MultiPreview 1",
            baseName = "tilePreview",
            parameterName = "MultiPreview 1",
          )
        )
      assertThat(it).hasPreviewConfiguration(defaultConfiguration(device = "id:wearos_small_round"))
      assertThat(it).previewBodyHasTextRange(testFile.textRange("tilePreview"))
      assertThat(it).hasAnnotationDefinition("@MultiPreview")
    }
  }

  // Regression test for b/368402966
  @Test
  fun testHandlesInvalidPsiElements() = runBlocking {
    val previewFile =
      fixture.addFileToProjectAndInvalidate(
        "app/src/main/java/com/android/test/Test.kt",
        // language=kotlin
        """
          package com.android.test

          import androidx.wear.tiles.tooling.preview.Preview
          import androidx.wear.tiles.tooling.preview.TilePreviewData

          @Preview
          private fun tilePreview(): TilePreviewData {
            return TilePreviewData()
          }
          """
          .trimIndent(),
      )

    val invalidPsiElement = mock<PsiMethod>()
    whenever(invalidPsiElement.isValid).thenReturn(false)

    val methodReturningInvalidElements = { _: PsiFile? -> listOf(invalidPsiElement) }

    try {
      assertThat(
          WearTilePreviewElementFinder(findMethods = methodReturningInvalidElements)
            .hasPreviewElements(project, previewFile.virtualFile)
        )
        .isFalse()
    } catch (e: Exception) {
      fail("The invalid PSI element should be handled")
    }
  }

  // Regression test for b/344639845
  @Test
  fun testFindMethodsResultIsCachedEvenWhenTakingALongTime() =
    runBlocking<Unit> {
      val previewFile =
        fixture.addFileToProjectAndInvalidate(
          "app/src/main/java/com/android/test/Test.kt",
          // language=kotlin
          """
          package com.android.test

          import androidx.wear.tiles.tooling.preview.Preview
          import androidx.wear.tiles.tooling.preview.TilePreviewData

          @Preview
          private fun tilePreview(): TilePreviewData {
            return TilePreviewData()
          }
          """
            .trimIndent(),
        )

      val mockFindMethods = mock<(PsiFile?) -> Collection<PsiElement>>()
      whenever(mockFindMethods.invoke(previewFile)).then {
        runBlocking {
          // simulate taking a bit of time to ensure we don't end up re-calling this method instead
          // of re-using an existing computation
          delay(2.seconds)
        }
        emptyList<PsiElement>()
      }

      val finder = WearTilePreviewElementFinder(findMethods = mockFindMethods)
      withContext(AndroidDispatchers.workerThread) {
        (0 until 20).forEach {
          launch { finder.hasPreviewElements(project, previewFile.virtualFile) }
          launch { finder.findPreviewElements(project, previewFile.virtualFile) }
        }
      }

      verify(mockFindMethods, atLeastOnce()).invoke(previewFile)
      // Checking if the cached value has been set and then setting it is not atomic, so in some
      // cases the method can be called more than once. The import thing is to ensure the method
      // is not called 40 times which was the case before. Here "4" should be a safe upper bound
      // to prevent flakiness.
      verify(mockFindMethods, atMost(4)).invoke(previewFile)
    }

  @Test
  fun testPreviewNameAndOrder(): Unit = runBlocking {
    val testFile =
      fixture.addFileToProjectAndInvalidate(
        "app/src/main/java/com/android/test/Test.kt",
        // language=kotlin
        """
        import androidx.wear.tiles.tooling.preview.Preview
        import androidx.wear.tiles.tooling.preview.TilePreviewData

        @Annot3
        @Preview
        annotation class Annot1(){}

        @Preview
        annotation class Annot2(){}

        @Annot1
        @Preview
        fun c() = TilePreviewData()

        @Annot1
        @Preview
        annotation class Annot3(){}

        @Annot2
        @Preview
        @Annot3
        fun a() = TilePreviewData()

        @Preview
        @Preview
        @Preview
        @Preview
        @Preview
        @Preview
        @Preview
        @Preview
        @Preview
        @Preview // 10 previews, for testing lexicographic order with double-digit numbers in the names
        annotation class Many() {}

        @Many
        fun f() = TilePreviewData()
      """
          .trimIndent(),
      )

    elementFinder
      .findPreviewElements(project, testFile.virtualFile)
      .toMutableList()
      .apply {
        // Randomize to make sure the ordering works
        shuffle()
      }
      .sortByDisplayAndSourcePosition()
      .map { it.displaySettings.name }
      .toTypedArray()
      .let {
        assertArrayEquals(
          arrayOf(
            "c - Annot1 1",
            "c - Annot3 1",
            "c", // Previews of 'C'
            "a - Annot2 1",
            "a",
            "a - Annot1 1",
            "a - Annot3 1", // Previews of 'a'
            "f - Many 01",
            "f - Many 02",
            "f - Many 03",
            "f - Many 04",
            "f - Many 05",
            "f - Many 06",
            "f - Many 07",
            "f - Many 08",
            "f - Many 09",
            "f - Many 10", // Previews of 'f'
          ),
          it,
        )
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

private class WearTilePreviewElementSubject(
  metadata: FailureMetadata?,
  actual: PsiWearTilePreviewElement?,
) : Subject<WearTilePreviewElementSubject, PsiWearTilePreviewElement?>(metadata, actual) {

  fun hasDisplaySettings(settings: PreviewDisplaySettings) {
    assertThat(actual()?.displaySettings).isEqualTo(settings)
  }

  fun hasPreviewConfiguration(configuration: PreviewConfiguration) {
    assertThat(actual()?.configuration).isEqualTo(configuration)
  }

  fun previewBodyHasTextRange(textRange: TextRange) {
    ReadAction.run<Throwable> {
      val previewBodyTextRange = actual()?.previewBody?.psiRange?.let { TextRange.create(it) }
      assertThat(previewBodyTextRange).isEqualTo(textRange)
    }
  }

  fun hasAnnotationDefinition(definition: String) {
    ReadAction.run<Throwable> {
      assertThat(actual()?.previewElementDefinition?.element?.text).isEqualTo(definition)
    }
  }

  companion object {
    private fun factory() = ::WearTilePreviewElementSubject

    fun assertThat(previewElement: PsiWearTilePreviewElement): WearTilePreviewElementSubject =
      Truth.assertAbout(factory()).that(previewElement)
  }
}

private fun defaultDisplaySettings(
  name: String,
  baseName: String,
  parameterName: String?,
  group: String? = null,
) =
  PreviewDisplaySettings(
    name = name,
    baseName = baseName,
    parameterName = parameterName,
    group = group,
    showBackground = true,
    showDecoration = false,
    backgroundColor = null,
  )

private fun defaultConfiguration(
  device: String = "id:wearos_small_round",
  locale: String? = null,
  fontScale: Float = 1.0f,
) = PreviewConfiguration.cleanAndGet(device = device, locale = locale, fontScale = fontScale)
