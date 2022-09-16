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
import com.android.tools.idea.compose.gradle.DEFAULT_KOTLIN_VERSION
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.compose.preview.renderer.renderPreviewElementForResult
import com.android.tools.idea.compose.preview.util.FAKE_PREVIEW_PARAMETER_PROVIDER_METHOD
import com.android.tools.idea.compose.preview.util.PreviewElementTemplateInstanceProvider
import com.android.tools.idea.compose.preview.util.SingleComposePreviewElementInstance
import com.android.tools.idea.preview.StaticPreviewProvider
import com.android.tools.idea.rendering.NoSecurityManagerRenderService
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.withKotlin
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.EdtRule
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
    RenderService.shutdownRenderExecutor(5)
    RenderService.initializeRenderExecutor()
    RenderService.setForTesting(
      projectRule.project,
      NoSecurityManagerRenderService(projectRule.project)
    )
    projectRule.fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/compose-designer/testData").toString()
    projectRule.load(SIMPLE_COMPOSE_PROJECT_PATH, AGP_CURRENT.withKotlin(DEFAULT_KOTLIN_VERSION))
    val gradleInvocationResult = projectRule.invokeTasks("compileDebugSources")
    if (!gradleInvocationResult.isBuildSuccessful) {
      Assert.fail(
        """
        The project must compile correctly for the test to pass.

        ${gradleInvocationResult.buildError}
      """.trimIndent()
      )
    }

    Assert.assertTrue(
      "The project must compile correctly for the test to pass",
      projectRule.invokeTasks("compileDebugSources").isBuildSuccessful
    )
  }

  @After
  fun tearDown() {
    RenderService.setForTesting(projectRule.project, null)
  }

  /** Checks the rendering of the default `@Preview` in the Compose template. */
  @Test
  fun testParametrizedPreviews() = runBlocking {
    val project = projectRule.project

    val parametrizedPreviews =
      VfsUtil.findRelativeFile(
        SimpleComposeAppPaths.APP_PARAMETRIZED_PREVIEWS.path,
        ProjectRootManager.getInstance(project).contentRoots[0]
      )!!

    run {
      val elements =
        PreviewElementTemplateInstanceProvider(
            StaticPreviewProvider(
              AnnotationFilePreviewElementFinder.findPreviewMethods(project, parametrizedPreviews)
                .filter { it.displaySettings.name == "TestWithProvider" }
            )
          )
          .previewElements()
      assertEquals(3, elements.count())

      elements.forEach {
        assertTrue(
          renderPreviewElementForResult(projectRule.androidFacet(":app"), it)
            .get()
            ?.renderResult
            ?.isSuccess
            ?: false
        )
      }
    }

    run {
      val elements =
        PreviewElementTemplateInstanceProvider(
            StaticPreviewProvider(
              AnnotationFilePreviewElementFinder.findPreviewMethods(project, parametrizedPreviews)
                .filter { it.displaySettings.name == "TestWithProviderInExpression" }
            )
          )
          .previewElements()
      assertEquals(3, elements.count())

      elements.forEach {
        assertTrue(
          renderPreviewElementForResult(projectRule.androidFacet(":app"), it)
            .get()
            ?.renderResult
            ?.isSuccess
            ?: false
        )
      }
    }

    // Test LoremIpsum default provider
    run {
      val elements =
        PreviewElementTemplateInstanceProvider(
            StaticPreviewProvider(
              AnnotationFilePreviewElementFinder.findPreviewMethods(project, parametrizedPreviews)
                .filter { it.displaySettings.name == "TestLorem" }
            )
          )
          .previewElements()
      assertEquals(1, elements.count())

      elements.forEach {
        assertTrue(
          renderPreviewElementForResult(projectRule.androidFacet(":app"), it)
            .get()
            ?.renderResult
            ?.isSuccess
            ?: false
        )
      }
    }

    // Test handling provider that throws an exception
    run {
      val elements =
        PreviewElementTemplateInstanceProvider(
            StaticPreviewProvider(
              AnnotationFilePreviewElementFinder.findPreviewMethods(project, parametrizedPreviews)
                .filter { it.displaySettings.name == "TestFailingProvider" }
            )
          )
          .previewElements()
      assertEquals(1, elements.count())

      elements.forEach {
        // Check that we create a SingleComposePreviewElementInstance that fails to render because
        // we'll try to render a composable
        // pointing to the fake method used to handle failures to load the PreviewParameterProvider.
        assertEquals(
          "google.simpleapplication.FailingProvider.$FAKE_PREVIEW_PARAMETER_PROVIDER_METHOD",
          it.composableMethodFqn
        )
        assertTrue(it is SingleComposePreviewElementInstance)
        assertNull(renderPreviewElementForResult(projectRule.androidFacet(":app"), it).get())
      }
    }
  }
}
