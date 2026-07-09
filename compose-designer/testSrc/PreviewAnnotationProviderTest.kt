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
@file:OptIn(ExperimentalCoroutinesApi::class)

import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.compose.preview.service.PreviewAnnotationProvider
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val PREVIEW_ANNOTATION_FQN = "androidx.compose.ui.tooling.preview.Preview"

@RunWith(JUnit4::class)
class PreviewAnnotationProviderTest {

  @get:Rule val projectRule = ComposeProjectRule()

  @Test
  fun testCancellation() = runTest {
    if (!KotlinPluginModeProvider.isK2Mode()) return@runTest

    val collectedPreviews = mutableListOf<Set<String>>()

    val provider = projectRule.project.service<PreviewAnnotationProvider>()
    // Before starting the test, wait for the initial, eager calculation to complete.
    // This ensures that our collector starts with a known, stable state.
    provider.allPreviewAnnotationsFlow.first { it.isNotEmpty() }

    // Launch a background coroutine to collect all subsequent emissions from the flow.
    val job =
      launch(UnconfinedTestDispatcher(testScheduler)) {
        provider.allPreviewAnnotationsFlow.collect { set -> collectedPreviews.add(set) }
      }

    // Trigger the first real calculation by adding a new annotation.
    // We then wait for the flow to emit a value containing this new annotation,
    // which confirms that the second calculation has completed.
    projectRule.fixture.addPreviewAnnotation("MyInitialPreview")
    provider.allPreviewAnnotationsFlow.first { it.contains("com.example.MyInitialPreview") }

    // Trigger two more calculations in quick succession.
    // Because of the `debounce(250)` and the `mapLatest` operator in the flow,
    // the calculation for "MySecondPreview" should be cancelled by the one for "MyThirdPreview".
    projectRule.fixture.addPreviewAnnotation("MySecondPreview")
    projectRule.fixture.addPreviewAnnotation("MyThirdPreview")

    // Wait for the final calculation (for "MyThirdPreview") to complete.
    provider.allPreviewAnnotationsFlow.first { it.contains("com.example.MyThirdPreview") }

    // The final assertions verify the core cancellation logic:
    // 1. We expect exactly 3 emissions in total:
    //    - The initial value from the flow's eager start (just @Preview).
    //    - The result after adding "MyInitialPreview".
    //    - The result after adding "MyThirdPreview".
    //    The calculation for "MySecondPreview" was cancelled and should not have produced an
    // emission.
    // 2. We check the content of each emission to ensure the state was updated correctly at each
    // step.
    assertThat(collectedPreviews).hasSize(3)
    assertThat(collectedPreviews[0]).containsExactly(PREVIEW_ANNOTATION_FQN)
    assertThat(collectedPreviews[1])
      .containsExactly(PREVIEW_ANNOTATION_FQN, "com.example.MyInitialPreview")
    assertThat(collectedPreviews[2])
      .containsExactly(
        PREVIEW_ANNOTATION_FQN,
        "com.example.MyInitialPreview",
        "com.example.MySecondPreview",
        "com.example.MyThirdPreview",
      )

    job.cancel()
  }

  @Test
  fun testFindsDirectAnnotationClass() = runTest {
    if (!KotlinPluginModeProvider.isK2Mode()) return@runTest

    projectRule.fixture.addFileToProject(
      "src/com/example/Annotations.kt",
      """
        package com.example

        import androidx.compose.ui.tooling.preview.Preview

        @Preview
        annotation class MyMultipreview
      """
        .trimIndent(),
    )

    val provider = projectRule.project.service<PreviewAnnotationProvider>()
    val allPreviews = provider.allPreviewAnnotationsFlow.first { it.size > 1 }

    assertThat(allPreviews).containsExactly(PREVIEW_ANNOTATION_FQN, "com.example.MyMultipreview")
  }

  @Test
  fun testFindsDirectTypeAlias() = runTest {
    if (!KotlinPluginModeProvider.isK2Mode()) return@runTest

    projectRule.fixture.addFileToProject(
      "src/com/example/Aliases.kt",
      """
        package com.example

        import androidx.compose.ui.tooling.preview.Preview

        typealias MyPreviewAlias = Preview
      """
        .trimIndent(),
    )

    val provider = projectRule.project.service<PreviewAnnotationProvider>()
    val allPreviews = provider.allPreviewAnnotationsFlow.first { it.size > 1 }

    assertThat(allPreviews).containsExactly(PREVIEW_ANNOTATION_FQN, "com.example.MyPreviewAlias")
  }

  @Test
  fun testFindsTransitiveAnnotations() = runTest {
    if (!KotlinPluginModeProvider.isK2Mode()) return@runTest

    projectRule.fixture.addFileToProject(
      "src/com/example/Annotations.kt",
      """
        package com.example

        import androidx.compose.ui.tooling.preview.Preview

        @Preview
        annotation class Level1

        @Level1
        annotation class Level2

        @Level2
        annotation class Level3
      """
        .trimIndent(),
    )

    val provider = projectRule.project.service<PreviewAnnotationProvider>()
    val allPreviews = provider.allPreviewAnnotationsFlow.first { it.size > 1 }

    assertThat(allPreviews)
      .containsExactly(
        PREVIEW_ANNOTATION_FQN,
        "com.example.Level1",
        "com.example.Level2",
        "com.example.Level3",
      )
  }

  @Test
  fun testFindsTransitiveAliases() = runTest {
    if (!KotlinPluginModeProvider.isK2Mode()) return@runTest

    projectRule.fixture.addFileToProject(
      "src/com/example/Aliases.kt",
      """
        package com.example

        import androidx.compose.ui.tooling.preview.Preview

        typealias Alias1 = Preview
        typealias Alias2 = Alias1
        typealias Alias3 = Alias2
      """
        .trimIndent(),
    )

    val provider = projectRule.project.service<PreviewAnnotationProvider>()
    val allPreviews = provider.allPreviewAnnotationsFlow.first { it.size > 1 }

    assertThat(allPreviews)
      .containsExactly(
        PREVIEW_ANNOTATION_FQN,
        "com.example.Alias1",
        "com.example.Alias2",
        "com.example.Alias3",
      )
  }

  @Test
  fun testFindsMixedChain() = runTest {
    if (!KotlinPluginModeProvider.isK2Mode()) return@runTest

    projectRule.fixture.addFileToProject(
      "src/com/example/Mixed.kt",
      """
        package com.example

        import androidx.compose.ui.tooling.preview.Preview

        @Preview
        annotation class Annotation1

        typealias Alias1 = Annotation1

        @Alias1
        annotation class Annotation2

        typealias Alias2 = Annotation2
      """
        .trimIndent(),
    )

    val provider = projectRule.project.service<PreviewAnnotationProvider>()
    val allPreviews = provider.allPreviewAnnotationsFlow.first { it.size > 1 }

    assertThat(allPreviews)
      .containsExactly(
        PREVIEW_ANNOTATION_FQN,
        "com.example.Annotation1",
        "com.example.Alias1",
        "com.example.Annotation2",
        "com.example.Alias2",
      )
  }

  @Test
  fun testHandlesCircularDependencies() = runTest {
    if (!KotlinPluginModeProvider.isK2Mode()) return@runTest

    projectRule.fixture.addFileToProject(
      "src/com/example/Circular.kt",
      """
        package com.example

        @CircularB
        annotation class CircularA

        @CircularA
        annotation class CircularB
      """
        .trimIndent(),
    )

    val provider = projectRule.project.service<PreviewAnnotationProvider>()
    val allPreviews = provider.allPreviewAnnotationsFlow.first { it.size == 1 }

    assertThat(allPreviews).containsExactly(PREVIEW_ANNOTATION_FQN)
  }

  @Test
  fun testDoesNotIncludeAliasesWithSameShortName() = runTest {
    if (!KotlinPluginModeProvider.isK2Mode()) return@runTest

    projectRule.fixture.addFileToProject(
      "src/com/other/library/Annotations.kt",
      """
        package com.other.library

        annotation class Preview
      """
        .trimIndent(),
    )
    projectRule.fixture.addFileToProject(
      "src/com/example/Aliases.kt",
      """
        package com.example

        import com.other.library.Preview

        typealias BogusPreview = Preview
      """
        .trimIndent(),
    )

    val provider = projectRule.project.service<PreviewAnnotationProvider>()
    val allPreviews = provider.allPreviewAnnotationsFlow.first { it.size == 1 }

    assertThat(allPreviews).doesNotContain("com.example.BogusPreview")
  }

  @Test
  fun testFindsNestedAnnotation() = runTest {
    if (!KotlinPluginModeProvider.isK2Mode()) return@runTest

    projectRule.fixture.addFileToProject(
      "src/com/example/Nested.kt",
      """
        package com.example

        import androidx.compose.ui.tooling.preview.Preview

        object MyPreviews {
          @Preview
          annotation class NestedPreview
        }
      """
        .trimIndent(),
    )

    val provider = projectRule.project.service<PreviewAnnotationProvider>()
    val allPreviews = provider.allPreviewAnnotationsFlow.first { it.size > 1 }

    assertThat(allPreviews)
      .containsExactly(PREVIEW_ANNOTATION_FQN, "com.example.MyPreviews.NestedPreview")
  }

  @Test
  fun testFindsDiamondDependencies() = runTest {
    if (!KotlinPluginModeProvider.isK2Mode()) return@runTest

    projectRule.fixture.addFileToProject(
      "src/com/example/Diamond.kt",
      """
        package com.example

        import androidx.compose.ui.tooling.preview.Preview

        @Preview
        annotation class BaseDevicePreview

        @BaseDevicePreview
        annotation class PhonePreview

        @BaseDevicePreview
        annotation class TabletPreview

        typealias WearablePreview = BaseDevicePreview
      """
        .trimIndent(),
    )

    val provider = projectRule.project.service<PreviewAnnotationProvider>()
    val allPreviews = provider.allPreviewAnnotationsFlow.first { it.size > 1 }

    assertThat(allPreviews)
      .containsExactly(
        PREVIEW_ANNOTATION_FQN,
        "com.example.BaseDevicePreview",
        "com.example.PhonePreview",
        "com.example.TabletPreview",
        "com.example.WearablePreview",
      )
  }

  @Test
  fun testFindsWithImportAlias() = runTest {
    if (!KotlinPluginModeProvider.isK2Mode()) return@runTest

    projectRule.fixture.addFileToProject(
      "src/com/example/Annotations.kt",
      """
        package com.example

        import androidx.compose.ui.tooling.preview.Preview as MyPreview

        @MyPreview
        annotation class MyCustomPreviewWithAlias
      """
        .trimIndent(),
    )

    val provider = projectRule.project.service<PreviewAnnotationProvider>()
    val allPreviews = provider.allPreviewAnnotationsFlow.first { it.size > 1 }

    assertThat(allPreviews)
      .containsExactly(PREVIEW_ANNOTATION_FQN, "com.example.MyCustomPreviewWithAlias")
  }

  private fun CodeInsightTestFixture.addPreviewAnnotation(name: String) {
    this.addFileToProject(
        "src/com/example/$name.kt",
        """
      package com.example
      import androidx.compose.ui.tooling.preview.Preview
      @Preview annotation class $name
    """
          .trimIndent(),
      )
      .virtualFile
  }
}
