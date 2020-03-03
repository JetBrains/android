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

import com.android.ide.common.blame.Message
import com.android.testutils.TestUtils
import com.android.tools.idea.compose.preview.PreviewElement
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.navigation.ComposeViewInfo
import com.android.tools.idea.compose.preview.navigation.parseViewInfo
import com.android.tools.idea.compose.preview.navigation.remapInline
import com.android.tools.idea.rendering.NoSecurityManagerRenderService
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.intellij.openapi.application.ReadAction
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class ViewInfoParserTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Before
  fun setUp() {
    RenderService.shutdownRenderExecutor(5)
    RenderService.initializeRenderExecutor()
    RenderService.setForTesting(projectRule.project, NoSecurityManagerRenderService(projectRule.project))
    projectRule.fixture.testDataPath = TestUtils.getWorkspaceFile("tools/adt/idea/compose-designer/testData").path
    projectRule.load(SIMPLE_COMPOSE_PROJECT_PATH)
    projectRule.requestSyncAndWait()
    val gradleInvocationResult = projectRule.invokeTasks("compileDebugSources")

    if (!gradleInvocationResult.isBuildSuccessful) {
      fail("""
        The project must compile correctly for the test to pass.

        Compiler errors:
        ${gradleInvocationResult.getCompilerMessages(Message.Kind.ERROR).joinToString("\n\n") { it.rawMessage }}


        ${gradleInvocationResult.buildError}
      """.trimIndent())
    }

    assertTrue("The project must compile correctly for the test to pass", projectRule.invokeTasks("compileDebugSources").isBuildSuccessful)
  }

  @After
  fun tearDown() {
    RenderService.setForTesting(projectRule.project, null)
  }

  /**
   * Checks the rendering of the default `@Preview` in the Compose template.
   */
  @Test
  fun testDefaultPreviewRendering() {
    val project = projectRule.project

    renderPreviewElementForResult(projectRule.androidFacet(":app"),
                                  PreviewElement.forTesting("google.simpleapplication.MainActivityKt.TwoElementsPreview"))
      .thenAccept { renderResult ->
        ImageIO.write(renderResult?.renderedImage?.copy ?: return@thenAccept, "png", File("/tmp/out.png"))

        val viewInfos = ReadAction.compute<List<ComposeViewInfo>, Throwable> {
          parseViewInfo(renderResult.rootViews.single(),
                        lineNumberMapper = remapInline(project))
        }.flatMap { it.allChildren() }

        assertNotNull(viewInfos.find {
          it.sourceLocation.fileName == "MainActivity.kt" &&
          it.sourceLocation.className == "google.simpleapplication.MainActivityKt\$TwoElementsPreview\$1" &&
          it.sourceLocation.lineNumber == 46
        })
      }.join()
  }
}