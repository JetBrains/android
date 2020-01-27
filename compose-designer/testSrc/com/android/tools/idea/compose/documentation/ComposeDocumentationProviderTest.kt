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
package com.android.tools.idea.compose.documentation

import com.android.testutils.TestUtils
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.NoSecurityManagerRenderService
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.Executors

class ComposeDocumentationProviderTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Before
  fun setUp() {
    StudioFlags.COMPOSE_EDITOR_SUPPORT.override(true)
    StudioFlags.COMPOSE_RENDER_SAMPLE_IN_DOCUMENTATION.override(true)

    RenderService.shutdownRenderExecutor(5)
    RenderService.initializeRenderExecutor()
    RenderService.setForTesting(projectRule.project, NoSecurityManagerRenderService(projectRule.project))
    projectRule.fixture.testDataPath = TestUtils.getWorkspaceFile("tools/adt/idea/compose-designer/testData").path
    projectRule.load(SIMPLE_COMPOSE_PROJECT_PATH)
    projectRule.requestSyncAndWait()

    assertTrue("The project must compile correctly for the test to pass", projectRule.invokeTasks("compileDebugSources").isBuildSuccessful)
  }

  @After
  fun tearDown() {
    RenderService.setForTesting(projectRule.project, null)
    StudioFlags.COMPOSE_RENDER_SAMPLE_IN_DOCUMENTATION.clearOverride()
    StudioFlags.COMPOSE_EDITOR_SUPPORT.clearOverride()
  }

  @Test
  fun testBasicDoc() {
    val project = projectRule.project
    val activityFile = VfsUtil.findRelativeFile("app/src/main/java/google/simpleapplication/MainActivity.kt",
                                                ProjectRootManager.getInstance(project).contentRoots[0])!!

    val executor = Executors.newSingleThreadExecutor()
    val composeDocProvider = ComposeDocumentationProvider()
    val previewMethod = ReadAction.compute<KtNamedFunction, Throwable> {
      val ktFile = PsiManager.getInstance(project).findFile(activityFile) as KtFile
      ktFile.declarations
        .filterIsInstance<KtNamedFunction>()
        .single { it.isTopLevel && it.name == "Greeting" }
    }

    val generatedDoc = composeDocProvider.generateDocAsync(previewMethod, null).get()

    // Check that we've correctly generated the preview tag
    assertTrue(generatedDoc.contains("<img src='file://DefaultPreview' alt='preview:DefaultPreview' width='\\d+' height='\\d+'>".toRegex()))

    val previewImage = composeDocProvider.getLocalImageForElement(previewMethod, "DefaultPreview") as BufferedImage
    ImageDiffUtil.assertImageSimilar(File("${projectRule.fixture.testDataPath}/${SIMPLE_COMPOSE_PROJECT_PATH}/defaultRender.png"),
                                     previewImage,
                                     0.0)
  }
}