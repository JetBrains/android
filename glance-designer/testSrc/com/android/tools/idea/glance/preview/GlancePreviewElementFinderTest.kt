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

import com.android.tools.idea.preview.sortByDisplayAndSourcePosition
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.android.tools.preview.PreviewDisplaySettings
import com.intellij.psi.PsiFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GlancePreviewElementFinderTest {
  @get:Rule val projectRule: GlanceProjectRule = GlanceProjectRule()

  private val project
    get() = projectRule.project

  private val fixture
    get() = projectRule.fixture

  private lateinit var sourceFileAppWidgets: PsiFile
  private lateinit var sourceFileAppWidgetsWithSize: PsiFile
  private lateinit var sourceFileNone: PsiFile

  @Before
  fun setUp() {
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
      var elements =
        AppWidgetPreviewElementFinder.findPreviewElements(project, sourceFileAppWidgets.virtualFile)
      Assert.assertEquals(
        listOf("com.android.test.SourceFileWidgetKt.Foo31"),
        elements.map { it.methodFqn },
      )
      Assert.assertEquals(
        listOf(
          PreviewDisplaySettings(
            name = "Foo31",
            baseName = "Foo31",
            parameterName = null,
            group = null,
            showDecoration = false,
            showBackground = false,
            backgroundColor = null,
            organizationName = "Foo31",
            organizationGroup = "com.android.test.SourceFileWidgetKt.Foo31",
          )
        ),
        elements.map { it.displaySettings },
      )
      elements =
        AppWidgetPreviewElementFinder.findPreviewElements(
          project,
          sourceFileAppWidgetsWithSize.virtualFile,
        )
      Assert.assertEquals(
        listOf("com.android.test.SourceFileWidgetWithSizeKt.Foo41"),
        elements.map { it.methodFqn },
      )
      Assert.assertEquals(
        listOf(
          PreviewDisplaySettings(
            name = "Foo41",
            baseName = "Foo41",
            parameterName = null,
            group = null,
            showDecoration = false,
            showBackground = false,
            backgroundColor = null,
            organizationName = "Foo41",
            organizationGroup = "com.android.test.SourceFileWidgetWithSizeKt.Foo41",
          )
        ),
        elements.map { it.displaySettings },
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
    Assert.assertEquals(
      listOf(
        PreviewDisplaySettings(
          name = "1 MultiPreviewFromOtherFile - GlancePreviewFun",
          baseName = "GlancePreviewFun",
          parameterName = "1 MultiPreviewFromOtherFile",
          group = null,
          showDecoration = false,
          showBackground = false,
          backgroundColor = null,
          organizationName = "GlancePreviewFun",
          organizationGroup = "com.android.test.MultiPreviewTestKt.GlancePreviewFun",
        ),
        PreviewDisplaySettings(
          name = "2 MultiPreviewFromOtherFile - GlancePreviewFun",
          baseName = "GlancePreviewFun",
          parameterName = "2 MultiPreviewFromOtherFile",
          group = null,
          showDecoration = false,
          showBackground = false,
          backgroundColor = null,
          organizationName = "GlancePreviewFun",
          organizationGroup = "com.android.test.MultiPreviewTestKt.GlancePreviewFun",
        ),
        PreviewDisplaySettings(
          name = "GlancePreviewFun",
          baseName = "GlancePreviewFun",
          parameterName = null,
          group = null,
          showDecoration = false,
          showBackground = false,
          backgroundColor = null,
          organizationName = "GlancePreviewFun",
          organizationGroup = "com.android.test.MultiPreviewTestKt.GlancePreviewFun",
        ),
        PreviewDisplaySettings(
          name = "1 MultiPreviewLevel1 - GlancePreviewFun",
          baseName = "GlancePreviewFun",
          parameterName = "1 MultiPreviewLevel1",
          group = null,
          showDecoration = false,
          showBackground = false,
          backgroundColor = null,
          organizationName = "GlancePreviewFun",
          organizationGroup = "com.android.test.MultiPreviewTestKt.GlancePreviewFun",
        ),
        PreviewDisplaySettings(
          name = "1 MultiPreviewLevel2 - GlancePreviewFun",
          baseName = "GlancePreviewFun",
          parameterName = "1 MultiPreviewLevel2",
          group = null,
          showDecoration = false,
          showBackground = false,
          backgroundColor = null,
          organizationName = "GlancePreviewFun",
          organizationGroup = "com.android.test.MultiPreviewTestKt.GlancePreviewFun",
        ),
      ),
      previewElements.map { it.displaySettings },
    )
  }

  @Test
  fun testPreviewNameAndOrder(): Unit = runBlocking {
    val composeTest =
      fixture.addFileToProjectAndInvalidate(
        "src/Test.kt",
        // language=kotlin
        """
        import androidx.glance.preview.Preview
        import androidx.compose.runtime.Composable

        @Annot3
        @Preview
        annotation class Annot1(){}

        @Preview
        annotation class Annot2(){}

        @Composable
        @Annot1
        @Preview
        fun C() {
        }

        @Annot1
        @Preview
        annotation class Annot3(){}

        @Composable
        @Annot2
        @Preview
        @Annot3
        fun A() {
        }

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

        @Composable
        @Many
        fun f(){
        }
      """
          .trimIndent(),
      )

    val previewElements =
      AppWidgetPreviewElementFinder.findPreviewElements(project, composeTest.virtualFile)
        .toMutableList()
        .apply {
          // Randomize to make sure the ordering works
          shuffle()
        }
        .sortByDisplayAndSourcePosition()

    val basisSetting =
      PreviewDisplaySettings(
        name = "",
        baseName = "",
        parameterName = null,
        group = null,
        showDecoration = false,
        showBackground = false,
        backgroundColor = null,
        organizationName = "",
        organizationGroup = "",
      )

    previewElements
      .map { it.displaySettings }
      .toTypedArray()
      .let {
        assertArrayEquals(
          arrayOf(
            basisSetting.copy(
              name = "1 Annot1 - C",
              baseName = "C",
              parameterName = "1 Annot1",
              organizationName = "C",
              organizationGroup = "TestKt.C",
            ),
            basisSetting.copy(
              name = "1 Annot3 - C",
              baseName = "C",
              parameterName = "1 Annot3",
              organizationName = "C",
              organizationGroup = "TestKt.C",
            ),
            basisSetting.copy(
              name = "C",
              baseName = "C",
              parameterName = null,
              organizationName = "C",
              organizationGroup = "TestKt.C",
            ), // Previews of 'C'
            basisSetting.copy(
              name = "1 Annot2 - A",
              baseName = "A",
              parameterName = "1 Annot2",
              organizationName = "A",
              organizationGroup = "TestKt.A",
            ),
            basisSetting.copy(
              name = "A",
              baseName = "A",
              parameterName = null,
              organizationName = "A",
              organizationGroup = "TestKt.A",
            ),
            basisSetting.copy(
              name = "1 Annot1 - A",
              baseName = "A",
              parameterName = "1 Annot1",
              organizationName = "A",
              organizationGroup = "TestKt.A",
            ),
            basisSetting.copy(
              name = "1 Annot3 - A",
              baseName = "A",
              parameterName = "1 Annot3",
              organizationName = "A",
              organizationGroup = "TestKt.A",
            ), // Previews of 'A'
            basisSetting.copy(
              name = "01 Many - f",
              baseName = "f",
              parameterName = "01 Many",
              organizationName = "f",
              organizationGroup = "TestKt.f",
            ),
            basisSetting.copy(
              name = "02 Many - f",
              baseName = "f",
              parameterName = "02 Many",
              organizationName = "f",
              organizationGroup = "TestKt.f",
            ),
            basisSetting.copy(
              name = "03 Many - f",
              baseName = "f",
              parameterName = "03 Many",
              organizationName = "f",
              organizationGroup = "TestKt.f",
            ),
            basisSetting.copy(
              name = "04 Many - f",
              baseName = "f",
              parameterName = "04 Many",
              organizationName = "f",
              organizationGroup = "TestKt.f",
            ),
            basisSetting.copy(
              name = "05 Many - f",
              baseName = "f",
              parameterName = "05 Many",
              organizationName = "f",
              organizationGroup = "TestKt.f",
            ),
            basisSetting.copy(
              name = "06 Many - f",
              baseName = "f",
              parameterName = "06 Many",
              organizationName = "f",
              organizationGroup = "TestKt.f",
            ),
            basisSetting.copy(
              name = "07 Many - f",
              baseName = "f",
              parameterName = "07 Many",
              organizationName = "f",
              organizationGroup = "TestKt.f",
            ),
            basisSetting.copy(
              name = "08 Many - f",
              baseName = "f",
              parameterName = "08 Many",
              organizationName = "f",
              organizationGroup = "TestKt.f",
            ),
            basisSetting.copy(
              name = "09 Many - f",
              baseName = "f",
              parameterName = "09 Many",
              organizationName = "f",
              organizationGroup = "TestKt.f",
            ),
            basisSetting.copy(
              name = "10 Many - f",
              baseName = "f",
              parameterName = "10 Many",
              organizationName = "f",
              organizationGroup = "TestKt.f",
            ), // Previews of 'f'
          ),
          it,
        )
      }
  }
}
