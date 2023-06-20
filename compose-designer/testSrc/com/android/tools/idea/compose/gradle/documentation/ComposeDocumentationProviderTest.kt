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
package com.android.tools.idea.compose.gradle.documentation

import com.android.testutils.ImageDiffUtil
import com.android.tools.idea.compose.documentation.ComposeDocumentationProvider
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import java.awt.image.BufferedImage
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ComposeDocumentationProviderTest {
  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  @Before
  fun setUp() {
    StudioFlags.COMPOSE_RENDER_SAMPLE_IN_DOCUMENTATION.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_RENDER_SAMPLE_IN_DOCUMENTATION.clearOverride()
  }

  @Test
  fun testBasicDoc() {
    val project = projectRule.project
    val activityFile =
      VfsUtil.findRelativeFile(
        SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path,
        ProjectRootManager.getInstance(project).contentRoots[0]
      )!!

    val composeDocProvider = ComposeDocumentationProvider()
    val previewMethod =
      ReadAction.compute<KtNamedFunction, Throwable> {
        val ktFile = PsiManager.getInstance(project).findFile(activityFile) as KtFile
        ktFile.declarations.filterIsInstance<KtNamedFunction>().single {
          it.isTopLevel && it.name == "Greeting"
        }
      }

    val generatedDoc = composeDocProvider.generateDocAsync(previewMethod, null).get()!!

    // Check that we've correctly generated the preview tag
    assertTrue(
      generatedDoc.contains(
        "<div class='content'><img src='file://DefaultPreview' alt='preview:DefaultPreview' width='\\d+' height='\\d+'></div>".toRegex()
      )
    )

    val previewImage =
      composeDocProvider.getLocalImageForElementAsync(previewMethod).get(5, TimeUnit.SECONDS)
        as BufferedImage
    ImageDiffUtil.assertImageSimilar(
      Paths.get(
        "${projectRule.fixture.testDataPath}/${SIMPLE_COMPOSE_PROJECT_PATH}/defaultDocRender.png"
      ),
      previewImage,
      0.1,
      1
    )
  }
}
