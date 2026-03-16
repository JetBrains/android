/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.compose.gradle.uicheck

import com.android.tools.idea.compose.ComposeGradleProjectRule
import com.android.tools.idea.compose.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.createNlModelForCompose
import com.android.tools.idea.compose.renderer.renderPreviewElementForResult
import com.android.tools.idea.testing.virtualFile
import com.android.tools.idea.uibuilder.scene.accessibilityBasedHierarchyParser
import com.android.tools.idea.uibuilder.visual.visuallint.toVisualLintConfiguration
import com.android.tools.idea.uibuilder.visual.visuallint.toVisualLintRenderResult
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.android.tools.visuallint.analyzers.SystemUiAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SystemUiAnalyzerComposeTest {
  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  @Test
  fun testSystemUiOverlap() {
    val facet = projectRule.androidFacet(":app")
    val visualLintPreviewFile = facet.virtualFile("src/main/java/google/simpleapplication/VisualLintPreview.kt")
    val renderResult =
      renderPreviewElementForResult(
          facet,
          visualLintPreviewFile,
          SingleComposePreviewElementInstance.forTesting(
            "google.simpleapplication.VisualLintPreviewKt.SystemUiOverlapPreview",
            showDecorations = true,
          ),
          customViewInfoParser = accessibilityBasedHierarchyParser,
        )
        .get()
    val file = renderResult.lightVirtualFile
    val nlModel = createNlModelForCompose(projectRule.fixture.testRootDisposable, facet, file)
    val issues =
      SystemUiAnalyzer.findIssues(
        renderResult = renderResult.result!!.toVisualLintRenderResult(),
        configuration = nlModel.configuration.toVisualLintConfiguration(),
      )
    assertEquals(1, issues.size)
    assertEquals("TextView is covered by System UI", issues[0].message)
  }

  @Test
  fun testNoSystemUiOverlap() {
    val facet = projectRule.androidFacet(":app")
    val visualLintPreviewFile = facet.virtualFile("src/main/java/google/simpleapplication/VisualLintPreview.kt")
    val renderResult =
      renderPreviewElementForResult(
          facet,
          visualLintPreviewFile,
          SingleComposePreviewElementInstance.forTesting(
            "google.simpleapplication.VisualLintPreviewKt.NoSystemUiOverlapPreview",
            showDecorations = true,
          ),
          customViewInfoParser = accessibilityBasedHierarchyParser,
        )
        .get()
    val file = renderResult.lightVirtualFile
    val nlModel = createNlModelForCompose(projectRule.fixture.testRootDisposable, facet, file)
    val issues =
      SystemUiAnalyzer.findIssues(
        renderResult = renderResult.result!!.toVisualLintRenderResult(),
        configuration = nlModel.configuration.toVisualLintConfiguration(),
      )
    assertEquals(0, issues.size)
  }
}
