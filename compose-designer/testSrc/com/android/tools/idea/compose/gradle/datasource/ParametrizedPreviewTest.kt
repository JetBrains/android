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
package com.android.tools.idea.compose.gradle.datasource

import com.android.tools.idea.compose.ComposeGradleProjectRule
import com.android.tools.idea.compose.PsiComposePreviewElement
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.SimpleComposeAppPaths
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.compose.preview.TestComposePreviewView
import com.android.tools.idea.compose.renderer.renderPreviewElementForResult
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.concurrency.awaitStatus
import com.android.tools.idea.editors.build.RenderingBuildStatus
import com.android.tools.idea.preview.find.PreviewElementProvider
import com.android.tools.idea.preview.find.StaticPreviewProvider
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.UiCheckInstance
import com.android.tools.idea.preview.uicheck.UiCheckModeFilter
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.android.tools.preview.ComposePreviewElementInstance
import com.android.tools.preview.FAKE_PREVIEW_PARAMETER_PROVIDER_METHOD
import com.android.tools.preview.ParametrizedComposePreviewElementInstance
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.assertInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class ParametrizedPreviewTest {
  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  val facet
    get() = projectRule.androidFacet(":app")

  @get:Rule val edtRule = EdtRule()

  @Before
  fun setUp() {
    Logger.getInstance(ComposePreviewRepresentation::class.java).setLevel(LogLevel.ALL)
    Logger.getInstance(RenderingBuildStatus::class.java).setLevel(LogLevel.ALL)
  }

  /** Checks the rendering of the default `@Preview` in the Compose template. */
  @Test
  fun testParametrizedPreviews() = runBlocking {
    val project = projectRule.project

    val parametrizedPreviews =
      VfsUtil.findRelativeFile(
        SimpleComposeAppPaths.APP_PARAMETRIZED_PREVIEWS.path,
        ProjectRootManager.getInstance(project).contentRoots[0],
      ) ?: throw RuntimeException("Cannot find relative file")

    run {
      val elements =
        StaticPreviewProvider(
            AnnotationFilePreviewElementFinder.findPreviewElements(project, parametrizedPreviews)
              .filter { it.displaySettings.name == "TestWithProvider" }
          )
          .resolve()
      assertEquals(3, elements.count())

      elements.forEach {
        assertTrue(
          renderPreviewElementForResult(facet, parametrizedPreviews, it)
            .future
            .get()
            ?.renderResult
            ?.isSuccess ?: false
        )
      }
    }

    run {
      val elements =
        StaticPreviewProvider(
            AnnotationFilePreviewElementFinder.findPreviewElements(project, parametrizedPreviews)
              .filter { it.displaySettings.name == "TestWithProviderInExpression" }
          )
          .resolve()
      assertEquals(3, elements.count())

      elements.forEach {
        assertTrue(
          renderPreviewElementForResult(facet, parametrizedPreviews, it)
            .future
            .get()
            ?.renderResult
            ?.isSuccess ?: false
        )
      }
    }

    // Test LoremIpsum default provider
    run {
      val elements =
        StaticPreviewProvider(
            AnnotationFilePreviewElementFinder.findPreviewElements(project, parametrizedPreviews)
              .filter { it.displaySettings.name == "TestLorem" }
          )
          .resolve()
      assertEquals(1, elements.count())

      elements.forEach {
        assertTrue(
          renderPreviewElementForResult(facet, parametrizedPreviews, it)
            .future
            .get()
            ?.renderResult
            ?.isSuccess ?: false
        )
      }
    }

    // Test handling provider that throws an exception
    run {
      val elements =
        StaticPreviewProvider(
            AnnotationFilePreviewElementFinder.findPreviewElements(project, parametrizedPreviews)
              .filter { it.displaySettings.name == "TestFailingProvider" }
          )
          .resolve()
      assertEquals(1, elements.count())

      elements.forEach {
        // Check that we create a SingleComposePreviewElementInstance that fails to render because
        // we'll try to render a composable
        // pointing to the fake method used to handle failures to load the PreviewParameterProvider.
        assertEquals(
          "google.simpleapplication.FailingProvider.$FAKE_PREVIEW_PARAMETER_PROVIDER_METHOD",
          it.methodFqn,
        )
        assertTrue(it is SingleComposePreviewElementInstance)
        assertNull(renderPreviewElementForResult(facet, parametrizedPreviews, it).future.get())
      }
    }

    // Test handling provider with 11 values
    run {
      val elements =
        StaticPreviewProvider(
            AnnotationFilePreviewElementFinder.findPreviewElements(project, parametrizedPreviews)
              .filter { it.displaySettings.name == "TestLargeProvider" }
          )
          .resolve()

      assertEquals(11, elements.count())

      assertEquals(
        listOf("00", "01", "02", "03", "04", "05", "06", "07", "08", "09", "10"),
        getEnumerationNumberFromPreviewName(elements),
      )

      elements.forEach {
        assertTrue(
          renderPreviewElementForResult(facet, parametrizedPreviews, it)
            .future
            .get()
            ?.renderResult
            ?.isSuccess ?: false
        )
      }
    }

    // Test handling provider with no values
    run {
      val elements =
        StaticPreviewProvider(
            AnnotationFilePreviewElementFinder.findPreviewElements(project, parametrizedPreviews)
              .filter { it.displaySettings.name == "TestEmptyProvider" }
          )
          .resolve()
          .toList()

      // The error preview is shown.
      assertEquals(1, elements.count())

      assertEquals(listOf("0"), getEnumerationNumberFromPreviewName(elements))

      elements.forEach {
        // Check that we create a ParametrizedComposePreviewElementInstance that fails to render
        // because
        // we'll try to render a composable with an empty sequence defined in ParametrizedPreviews
        assertEquals(
          "google.simpleapplication.ParametrizedPreviewsKt.TestEmptyProvider",
          it.methodFqn,
        )
        assertTrue(it is ParametrizedComposePreviewElementInstance)
        assertNull(renderPreviewElementForResult(facet, parametrizedPreviews, it).future.get())
      }
    }
  }

  @Test
  fun testUiCheckForParametrizedPreview(): Unit = runBlocking {
    val project = projectRule.project

    val parametrizedPreviews =
      VfsUtil.findRelativeFile(
        SimpleComposeAppPaths.APP_PARAMETRIZED_PREVIEWS.path,
        ProjectRootManager.getInstance(project).contentRoots[0],
      ) ?: throw RuntimeException("Cannot find relative file")
    val psiFile = runReadAction { PsiManager.getInstance(project).findFile(parametrizedPreviews)!! }

    val elements =
      StaticPreviewProvider(
          AnnotationFilePreviewElementFinder.findPreviewElements(project, parametrizedPreviews)
            .filter { it.displaySettings.name == "TestWithProvider" }
        )
        .resolve()
    assertEquals(3, elements.count())

    val mainSurface =
      NlSurfaceBuilder.builder(project, projectRule.fixture.testRootDisposable).build()

    val composeView = TestComposePreviewView(mainSurface)
    val preview =
      ComposePreviewRepresentation(psiFile, PreferredVisibility.SPLIT) { _, _, _, _, _, _ ->
        composeView
      }
    Disposer.register(projectRule.fixture.testRootDisposable, preview)

    composeView.runAndWaitForRefresh { preview.onActivate() }

    val uiCheckElement = elements.first() as ParametrizedComposePreviewElementInstance<*>
    composeView.runAndWaitForRefresh {
      preview.setMode(PreviewMode.UiCheck(UiCheckInstance(uiCheckElement, isWearPreview = false)))
    }

    assertInstanceOf<UiCheckModeFilter.Enabled<PsiComposePreviewElementInstance>>(
      preview.uiCheckFilterFlow.value
    )

    assertThat(preview.composePreviewFlowManager.availableGroupsFlow.value.map { it.displayName })
      .containsExactly("Screen sizes", "Font scales", "Light/Dark", "Colorblind filters")
      .inOrder()

    assertThat(
        preview.composePreviewFlowManager.renderedPreviewElementsFlow.value
          .asCollection()
          .map { it.displaySettings.organizationName!! }
          .toSet()
      )
      .containsExactly(
        "Screen sizes - TestWithProvider (name 0)",
        "Font scales - TestWithProvider (name 0)",
        "Light/Dark - TestWithProvider (name 0)",
        "Colorblind filters - TestWithProvider (name 0)",
      )
      .inOrder()

    assertThat(
        preview.composePreviewFlowManager.renderedPreviewElementsFlow.value
          .asCollection()
          .map { it.displaySettings.organizationGroup!! }
          .toSet()
      )
      .containsExactly(
        "google.simpleapplication.ParametrizedPreviewsKt.TestWithProviderScreen sizes",
        "google.simpleapplication.ParametrizedPreviewsKt.TestWithProviderFont scales",
        "google.simpleapplication.ParametrizedPreviewsKt.TestWithProviderLight/Dark",
        "google.simpleapplication.ParametrizedPreviewsKt.TestWithProviderColorblind filters",
      )
      .inOrder()

    preview.renderedPreviewElementsInstancesFlowForTest().awaitStatus(
      "Failed waiting to start UI check mode",
      5.seconds,
    ) {
      val stringValue =
        it
          .asCollection()
          .filterIsInstance<ParametrizedComposePreviewElementInstance<*>>()
          .joinToString("\n") {
            "${it.methodFqn} provider=${it.providerClassFqn} index=${it.index} max=${it.maxIndex}"
          }

      stringValue ==
        """
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
          google.simpleapplication.ParametrizedPreviewsKt.TestWithProvider provider=google.simpleapplication.TestProvider index=0 max=2
        """
          .trimIndent()
    }
    preview.renderedPreviewElementsInstancesFlowForTest().value.asCollection().forEach {
      assertTrue(it.displaySettings.name.endsWith("TestWithProvider (name 0)"))
    }
  }

  @Test
  fun testParametrizedPreviewMultiplePreviewsAnnotationOrder(): Unit = runBlocking {
    val project = projectRule.project

    val parametrizedPreviews =
      VfsUtil.findRelativeFile(
        SimpleComposeAppPaths.APP_PARAMETRIZED_PREVIEWS.path,
        ProjectRootManager.getInstance(project).contentRoots[0],
      ) ?: throw RuntimeException("Cannot find relative file")
    val psiFile = runReadAction { PsiManager.getInstance(project).findFile(parametrizedPreviews)!! }

    val mainSurface =
      NlSurfaceBuilder.builder(project, projectRule.fixture.testRootDisposable).build()

    val composeView = TestComposePreviewView(mainSurface)
    val preview =
      ComposePreviewRepresentation(psiFile, PreferredVisibility.SPLIT) { _, _, _, _, _, _ ->
        composeView
      }
    Disposer.register(projectRule.fixture.testRootDisposable, preview)

    composeView.runAndWaitForRefresh { preview.onActivate() }
    composeView.runAndWaitForRefresh { preview.setMode(PreviewMode.Default()) }

    val elements =
      StaticPreviewProvider(
          AnnotationFilePreviewElementFinder.findPreviewElements(project, parametrizedPreviews)
            .filter {
              it.displaySettings.organizationName == "TestWithProviderMultiplePreviewsAnnotation"
            }
        )
        .resolve()
    assertEquals(6, elements.count())

    assertThat(
        preview.composePreviewFlowManager.renderedPreviewElementsFlow.value
          .asCollection()
          .filter {
            it.displaySettings.organizationName == "TestWithProviderMultiplePreviewsAnnotation"
          }
          .map { it.displaySettings.name }
      )
      .containsExactly(
        "DefaultName - TestWithProviderMultiplePreviewsAnnotation (name 0)",
        "DefaultName - TestWithProviderMultiplePreviewsAnnotation (name 0)",
        "DefaultName - TestWithProviderMultiplePreviewsAnnotation (name 1)",
        "DefaultName - TestWithProviderMultiplePreviewsAnnotation (name 1)",
        "DefaultName - TestWithProviderMultiplePreviewsAnnotation (name 2)",
        "DefaultName - TestWithProviderMultiplePreviewsAnnotation (name 2)",
      )
      .inOrder()
  }

  @Test
  fun testParametrizedPreviewMultiplePreviewsOrder(): Unit = runBlocking {
    val project = projectRule.project

    val parametrizedPreviews =
      VfsUtil.findRelativeFile(
        SimpleComposeAppPaths.APP_PARAMETRIZED_PREVIEWS.path,
        ProjectRootManager.getInstance(project).contentRoots[0],
      ) ?: throw RuntimeException("Cannot find relative file")
    val psiFile = runReadAction { PsiManager.getInstance(project).findFile(parametrizedPreviews)!! }

    val elements =
      StaticPreviewProvider(
          AnnotationFilePreviewElementFinder.findPreviewElements(project, parametrizedPreviews)
            .filter { it.displaySettings.organizationName == "TestWithProviderMultiplePreviews" }
        )
        .resolve()
    assertEquals(6, elements.count())

    val mainSurface =
      NlSurfaceBuilder.builder(project, projectRule.fixture.testRootDisposable).build()

    val composeView = TestComposePreviewView(mainSurface)
    val preview =
      ComposePreviewRepresentation(psiFile, PreferredVisibility.SPLIT) { _, _, _, _, _, _ ->
        composeView
      }
    Disposer.register(projectRule.fixture.testRootDisposable, preview)

    composeView.runAndWaitForRefresh { preview.onActivate() }
    composeView.runAndWaitForRefresh { preview.setMode(PreviewMode.Default()) }

    assertThat(
        preview.composePreviewFlowManager.renderedPreviewElementsFlow.value
          .asCollection()
          .filter { it.displaySettings.organizationName == "TestWithProviderMultiplePreviews" }
          .map { it.displaySettings.name }
      )
      .containsExactly(
        "DefaultName - TestWithProviderMultiplePreviews (name 0)",
        "DefaultName - TestWithProviderMultiplePreviews (name 1)",
        "DefaultName - TestWithProviderMultiplePreviews (name 2)",
        "DefaultName - TestWithProviderMultiplePreviews (name 0)",
        "DefaultName - TestWithProviderMultiplePreviews (name 1)",
        "DefaultName - TestWithProviderMultiplePreviews (name 2)",
      )
      .inOrder()
  }

  private suspend fun PreviewElementProvider<PsiComposePreviewElement>.resolve() =
    this.previewElements().flatMap { it.resolve() }.toList()

  private fun getEnumerationNumberFromPreviewName(
    elements: List<ComposePreviewElementInstance<*>>
  ) = elements.map { it.displaySettings.name.removeSuffix(")").substringAfterLast(' ') }
}
