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
package com.android.tools.idea.compose.preview.navigation

import com.android.ide.common.blame.Message
import com.android.testutils.TestUtils
import com.android.tools.idea.compose.preview.PreviewElement
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.renderer.renderPreviewElementForResult
import com.android.tools.idea.rendering.NoSecurityManagerRenderService
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.intellij.openapi.application.ReadAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class PreviewNavigationTest {
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
  @Ignore("b/151091941")
  @Test
  fun testComposableNavigation() {
    val project = projectRule.project

    renderPreviewElementForResult(projectRule.androidFacet(":app"),
                                  PreviewElement.forTesting("google.simpleapplication.MainActivityKt.TwoElementsPreview"))
      .thenAccept { renderResult ->
        val rootView = renderResult!!.rootViews.single()!!
        ReadAction.run<Throwable> {
          // Find the boundaries for the root element. This will cover the whole layout
          val bounds = parseViewInfo(rootView).map { it.bounds }.first()

          // Check clicking outside of the boundaries
          assertTrue(findComponentHits(project, rootView, -30, -30).isEmpty())
          assertNull(findNavigatableComponentHit(project, rootView, -30, -30))
          assertTrue(findComponentHits(project, rootView, -1, 0).isEmpty())
          assertTrue(findComponentHits(project, rootView, bounds.right * 2, 10).isEmpty())
          assertTrue(findComponentHits(project, rootView, 10, bounds.bottom * 2).isEmpty())

          // Click the Text("Hello 2") by clicking (0, 0)
          // The hits will be, in that other: Text > Column > MaterialTheme
          assertEquals("""
            MainActivity.kt:47
            MainActivity.kt:46
            MainActivity.kt:45
          """.trimIndent(), findComponentHits(project, rootView, 0, 0)
            .filter { it.fileName == "MainActivity.kt" }
            .joinToString("\n") { "${it.fileName}:${it.lineNumber}" })

          // Click the Button by clicking (0, bounds.bottom)
          // The hits will be, in that other: Button > Column > MaterialTheme
          assertEquals("""
            MainActivity.kt:48
            MainActivity.kt:46
            MainActivity.kt:45
          """.trimIndent(), findComponentHits(project, rootView, 0, bounds.bottom)
            .filter { it.fileName == "MainActivity.kt" }
            .joinToString("\n") { "${it.fileName}:${it.lineNumber}" })
        }
      }.join()
  }
}