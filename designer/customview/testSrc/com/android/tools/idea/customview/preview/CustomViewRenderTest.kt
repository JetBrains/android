/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.customview.preview

import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.rendering.StudioRenderService
import com.android.tools.idea.rendering.createNoSecurityRenderService
import com.android.tools.idea.rendering.createRenderTaskFuture
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.guessProjectDir
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CustomViewRenderTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule("tools/adt/idea/designer/customview/testData")

  @Before
  fun setUp() {
    RenderTestUtil.beforeRenderTestCase()
    StudioRenderService.setForTesting(projectRule.project, createNoSecurityRenderService())
  }

  @After
  fun tearDown() {
    ApplicationManager.getApplication().invokeAndWait {
      RenderTestUtil.afterRenderTestCase()
    }
    StudioRenderService.setForTesting(projectRule.project, null)
  }

  @Test
  fun testCustomViewWithLocalBroadcastManager_rendersAndDisposes() {
    projectRule.load("projects/SimpleCustomView")

    projectRule.invokeTasks("compileDebugSources").apply {
      buildError?.printStackTrace()
      Assert.assertTrue("The project must compile correctly for the test to pass", isBuildSuccessful)
    }

    val virtualFile =
      projectRule.fixture.project.guessProjectDir()!!
        .findFileByRelativePath("app/src/main/java/com/example/myapplication/BroadcastManagerCustomView.java")!!
    val fileContent = getXmlLayout("com.example.myapplication.BroadcastManagerCustomView", shrinkWidth = false, shrinkHeight = false)
    val customPreviewXml = CustomViewLightVirtualFile("custom_preview.xml", fileContent) { virtualFile }

    val renderTask = createRenderTaskFuture(projectRule.androidFacet(":app"), customPreviewXml, true).get()
    val renderResult = renderTask.render().get()
    val image = renderResult!!.renderedImage

    Assert.assertTrue(
      "Valid result image is expected to be bigger than 10x10. It's ${image.width}x${image.height}",
      image.width > 10 && image.height > 10)
    Assert.assertNotNull(image.copy)

    val classLoader = renderResult.rootViews.first().viewObject.javaClass.classLoader
    val broadcastManager = classLoader.loadClass("androidx.localbroadcastmanager.content.LocalBroadcastManager")
    val instanceField = broadcastManager.getDeclaredField("mInstance").apply { isAccessible = true }

    Assert.assertNotNull(instanceField.get(null))

    renderTask.dispose().get()

    Assert.assertNull(instanceField.get(null))
  }
}