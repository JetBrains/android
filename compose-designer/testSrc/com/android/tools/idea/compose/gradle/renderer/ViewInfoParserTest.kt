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
import com.android.tools.idea.compose.preview.ComposeViewInfo
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.compose.preview.navigation.remapInline
import com.android.tools.idea.compose.preview.parseViewInfo
import com.android.tools.idea.compose.preview.renderer.renderPreviewElementForResult
import com.android.tools.idea.compose.preview.util.SingleComposePreviewElementInstance
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Rule
import org.junit.Test

class ViewInfoParserTest {
  private val LOG = Logger.getInstance(PreviewNavigationTest::class.java)

  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  private val project: Project
    get() = projectRule.project

  /** Checks the rendering of the default `@Preview` in the Compose template. */
  @Test
  fun testDefaultPreviewRendering() {
    val facet = projectRule.androidFacet(":app")
    val module = facet.module
    val previewStartLine = runReadAction {
      val file =
        VfsUtil.findRelativeFile(
          SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path,
          ProjectRootManager.getInstance(project).contentRoots[0]
        )!!
      val ktFile = PsiManager.getInstance(project).findFile(file) as KtFile
      ktFile
          .declarations
          .filterIsInstance<KtNamedFunction>()
          .single { it.isTopLevel && it.name == "TwoElementsPreview" }
          .nameIdentifier!!
        .getLineNumber() + 1 // Starts at 0, as opposed to 1 for ViewInfo
    }

    renderPreviewElementForResult(
        facet,
        SingleComposePreviewElementInstance.forTesting(
          "google.simpleapplication.MainActivityKt.TwoElementsPreview"
        )
      )
      .thenAccept { renderResult ->
        checkNotNull(renderResult)
        ImageIO.write(renderResult.renderedImage.copy, "png", File("/tmp/out.png"))

        val viewInfos =
          ReadAction.compute<List<ComposeViewInfo>, Throwable> {
            parseViewInfo(
              rootViewInfo = renderResult.rootViews.single(),
              lineNumberMapper = remapInline(module),
              logger = LOG
            )
          }
            .flatMap { it.allChildren() }

        val previewViewInfos =
          viewInfos.filter {
            it.sourceLocation.fileName == "MainActivity.kt" &&
              it.sourceLocation.packageHash == 34180119
          }

        var expectedLineNumber = previewStartLine + 1
        assertEquals(5, previewViewInfos.size)
        assertEquals(expectedLineNumber++, previewViewInfos[0].sourceLocation.lineNumber)
        assertEquals(expectedLineNumber++, previewViewInfos[1].sourceLocation.lineNumber)
        assertEquals(expectedLineNumber++, previewViewInfos[2].sourceLocation.lineNumber)
        assertEquals(expectedLineNumber++, previewViewInfos[3].sourceLocation.lineNumber)
        assertEquals(expectedLineNumber, previewViewInfos[4].sourceLocation.lineNumber)
      }
      .join()
  }
}
