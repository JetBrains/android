/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.testutils.delayUntilCondition
import com.android.tools.idea.compose.PsiComposePreviewElement
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.gradle.DEFAULT_KOTLIN_VERSION
import com.android.tools.idea.compose.gradle.renderer.renderPreviewElementForResult
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.compose.preview.TestComposePreviewView
import com.android.tools.idea.compose.preview.waitForAllRefreshesToFinish
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.concurrency.awaitStatus
import com.android.tools.idea.editors.build.RenderingBuildStatus
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.StaticPreviewProvider
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.UiCheckInstance
import com.android.tools.idea.preview.uicheck.UiCheckModeFilter
import com.android.tools.idea.rendering.StudioRenderService
import com.android.tools.idea.rendering.createNoSecurityRenderService
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.withKotlin
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService
import com.android.tools.preview.ComposePreviewElementInstance
import com.android.tools.preview.FAKE_PREVIEW_PARAMETER_PROVIDER_METHOD
import com.android.tools.preview.ParametrizedComposePreviewElementInstance
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.android.tools.rendering.RenderService
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
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ParametrizedPreviewTest {
  @get:Rule val projectRule = AndroidGradleProjectRule()

  @get:Rule val edtRule = EdtRule()

  @Before
  fun setUp() {
    Logger.getInstance(ComposePreviewRepresentation::class.java).setLevel(LogLevel.ALL)
    Logger.getInstance(RenderingBuildStatus::class.java).setLevel(LogLevel.ALL)
    RenderService.shutdownRenderExecutor(5)
    RenderService.initializeRenderExecutor()
    StudioRenderService.setForTesting(projectRule.project, createNoSecurityRenderService())
    projectRule.fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/compose-designer/testData").toString()
    projectRule.load(SIMPLE_COMPOSE_PROJECT_PATH, AGP_CURRENT.withKotlin(DEFAULT_KOTLIN_VERSION))
    val gradleInvocationResult = projectRule.invokeTasks("compileDebugSources")
    if (!gradleInvocationResult.isBuildSuccessful) {
      Assert.fail(
        """
        The project must compile correctly for the test to pass.

        ${gradleInvocationResult.buildError}
      """
          .trimIndent()
      )
    }

    assertTrue(
      "The project must compile correctly for the test to pass",
      projectRule.invokeTasks("compileDebugSources").isBuildSuccessful,
    )

    // Create VisualLintService early to avoid it being created at the time of project disposal
    VisualLintService.getInstance(projectRule.project)
  }

  @After
  fun tearDown() {
    StudioRenderService.setForTesting(projectRule.project, null)
    StudioFlags.COMPOSE_UI_CHECK_COLORBLIND_MODE.clearOverride()
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
          renderPreviewElementForResult(projectRule.androidFacet(":app"), parametrizedPreviews, it)
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
          renderPreviewElementForResult(projectRule.androidFacet(":app"), parametrizedPreviews, it)
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
          renderPreviewElementForResult(projectRule.androidFacet(":app"), parametrizedPreviews, it)
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
        assertNull(
          renderPreviewElementForResult(projectRule.androidFacet(":app"), parametrizedPreviews, it)
            .future
            .get()
        )
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
          renderPreviewElementForResult(projectRule.androidFacet(":app"), parametrizedPreviews, it)
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
        assertNull(
          renderPreviewElementForResult(projectRule.androidFacet(":app"), parametrizedPreviews, it)
            .future
            .get()
        )
      }
    }
  }

  @Test
  fun testUiCheckForParametrizedPreview(): Unit = runBlocking {
    StudioFlags.COMPOSE_UI_CHECK_COLORBLIND_MODE.override(true)

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
    preview.onActivate()

    waitForAllRefreshesToFinish(30.seconds)
    val uiCheckElement = elements.first() as ParametrizedComposePreviewElementInstance<*>
    run {
      var refreshCompleted = false
      composeView.refreshCompletedListeners.add { refreshCompleted = true }
      preview.setMode(PreviewMode.UiCheck(UiCheckInstance(uiCheckElement, isWearPreview = false)))
      delayUntilCondition(250) { refreshCompleted }
    }

    assertInstanceOf<UiCheckModeFilter.Enabled<PsiComposePreviewElementInstance>>(
      preview.uiCheckFilterFlow.value
    )

    assertThat(preview.composePreviewFlowManager.availableGroupsFlow.value.map { it.displayName })
      .containsExactly("Screen sizes", "Font scales", "Light/Dark", "Colorblind filters")
      .inOrder()
    preview.filteredPreviewElementsInstancesFlowForTest().awaitStatus(
      "Failed waiting to start UI check mode",
      5.seconds,
    ) {
      val stringValue =
        it
          .asCollection()
          .filterIsInstance<ParametrizedComposePreviewElementInstance<*>>()
          .map {
            "${it.methodFqn} provider=${it.providerClassFqn} index=${it.index} max=${it.maxIndex}"
          }
          .joinToString("\n")

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
    preview.filteredPreviewElementsInstancesFlowForTest().value.asCollection().forEach {
      assertTrue(it.displaySettings.name.endsWith("TestWithProvider (name 0)"))
    }
  }

  private suspend fun PreviewElementProvider<PsiComposePreviewElement>.resolve() =
    this.previewElements().flatMap { it.resolve() }.toList()

  private fun getEnumerationNumberFromPreviewName(
    elements: List<ComposePreviewElementInstance<*>>
  ) = elements.map { it.displaySettings.name.removeSuffix(")").substringAfterLast(' ') }
}
