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
package com.android.tools.idea.compose.gradle.renderer

import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.gradle.navigation.PreviewNavigationTest
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.navigation.ComposeViewInfo
import com.android.tools.idea.compose.preview.navigation.parseViewInfo
import com.android.tools.idea.compose.preview.navigation.remapInline
import com.android.tools.idea.compose.preview.renderer.renderPreviewElementForResult
import com.android.tools.idea.compose.preview.util.SinglePreviewElementInstance
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class ViewInfoParserTest {
  private val LOG = Logger.getInstance(PreviewNavigationTest::class.java)

  @get:Rule
  val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  /**
   * Checks the rendering of the default `@Preview` in the Compose template.
   */
  @Test
  fun testDefaultPreviewRendering() {
    val facet = projectRule.androidFacet(":app")
    val module = facet.module

    renderPreviewElementForResult(facet,
                                                                                                     SinglePreviewElementInstance.forTesting(
                                                                                                       "google.simpleapplication.MainActivityKt.TwoElementsPreview"))
      .thenAccept { renderResult ->
        ImageIO.write(renderResult?.renderedImage?.copy ?: return@thenAccept, "png", File("/tmp/out.png"))

        val viewInfos = ReadAction.compute<List<ComposeViewInfo>, Throwable> {
          parseViewInfo(rootViewInfo = renderResult.rootViews.single(),
                        lineNumberMapper = remapInline(module),
                        logger = LOG)
        }.flatMap { it.allChildren() }

        assertNotNull(viewInfos.find {
          it.sourceLocation.fileName == "MainActivity.kt" &&
          it.sourceLocation.packageHash == 34180119 &&
          it.sourceLocation.lineNumber == 46
        })
      }.join()
  }
}