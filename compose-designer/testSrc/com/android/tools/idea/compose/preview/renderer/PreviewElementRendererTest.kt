/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.renderer

import com.android.testutils.TestUtils
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.idea.compose.preview.PreviewConfiguration
import com.android.tools.idea.compose.preview.PreviewElement
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

private const val PROJECT_RELATIVE_PATH = "projects/SimpleComposeApplication"
/** Configuration equivalent to defining a `@Preview` annotation with no parameters */
private val nullConfiguration = PreviewConfiguration.cleanAndGet(null, null, null, null, null)

private fun previewFromMethodName(fqn: String): PreviewElement =
  PreviewElement("", fqn, null, null, nullConfiguration)

class SinglePreviewElementRendererTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Before
  fun setUp() {
    RenderService.shutdownRenderExecutor(5)
    RenderService.initializeRenderExecutor()
    RenderService.setForTesting(projectRule.project, MyRenderService(projectRule.project))
    projectRule.fixture.testDataPath = TestUtils.getWorkspaceFile("tools/adt/idea/compose-designer/testData").path
    projectRule.load(PROJECT_RELATIVE_PATH)
    projectRule.requestSyncAndWait()

    assertTrue("The project must compile correctly for the test to pass", projectRule.invokeTasks("compileDebugSources").isBuildSuccessful)
  }

  @After
  fun tearDown() {
    RenderService.setForTesting(projectRule.project, null)
  }

  /**
   * Checks that trying to render an non-existent preview returns a null image
   */
  @Test
  fun testInvalidPreview() {
    assertNull(renderPreviewElement(projectRule.androidFacet,
                                    previewFromMethodName("google.simpleapplication.MainActivityKt.InvalidPreview")).get())
  }

  /**
   * Checks the rendering of the default `@Preview` in the Compose template.
   */
  @Test
  fun testDefaultPreviewRendering() {
    val defaultRender = renderPreviewElement(projectRule.androidFacet,
                                             previewFromMethodName("google.simpleapplication.MainActivityKt.DefaultPreview")).get()
    ImageDiffUtil.assertImageSimilar(File("${projectRule.fixture.testDataPath}/${PROJECT_RELATIVE_PATH}/defaultRender.png"),
                                     defaultRender!!,
                                     0.0)
  }

  // Disable security manager during tests (for bazel)
  private class MyRenderService(project: Project) : RenderService(project) {
    override fun taskBuilder(facet: AndroidFacet, configuration: Configuration): RenderService.RenderTaskBuilder {
      return super.taskBuilder(facet, configuration)
        .disableSecurityManager()
    }
  }
}