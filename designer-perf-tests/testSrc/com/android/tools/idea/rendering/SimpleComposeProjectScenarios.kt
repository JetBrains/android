/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.android.ide.common.rendering.api.RenderSession
import com.android.tools.idea.compose.preview.navigation.parseViewInfo
import com.android.tools.idea.compose.preview.renderer.createRenderTaskFuture
import com.android.tools.idea.compose.preview.renderer.renderPreviewElementForResult
import com.android.tools.idea.compose.preview.util.SingleComposePreviewElementInstance
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiDocumentManager
import org.junit.Assert
import java.util.concurrent.TimeUnit

class SimpleComposeProjectScenarios {
  companion object {
    fun baselineCompileScenario(projectRule: AndroidGradleProjectRule) {
      ApplicationManager.getApplication().invokeAndWait {
        WriteAction.run<Throwable> {
          projectRule.fixture.type("A")
          PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
        }
      }
      // Modify the file to make sure a change is done
      Assert.assertTrue(projectRule.invokeTasks("compileDebugKotlin").isBuildSuccessful)
    }

    fun baselineRenderScenario(projectRule: AndroidGradleProjectRule): RenderResult {
      val renderResult = renderPreviewElementForResult(projectRule.androidFacet(":app"),
                                                       SingleComposePreviewElementInstance.forTesting(
                                                         "google.simpleapplication.MainActivityKt.DefaultPreview"),
                                                       true).get()
      val image = renderResult!!.renderedImage
      Assert.assertTrue(
        "Valid result image is expected to be bigger than 10x10. It's ${image.width}x${image.height}",
        image.width > 10 && image.height > 10)
      Assert.assertNotNull(image.copy)

      return renderResult
    }

    fun complexRenderScenario(projectRule: AndroidGradleProjectRule): RenderResult {
      val renderResult = renderPreviewElementForResult(projectRule.androidFacet(":app"),
                                                       SingleComposePreviewElementInstance.forTesting(
                                                         "google.simpleapplication.ComplexPreviewKt.ComplexPreview"),
                                                       true).get()
      val image = renderResult!!.renderedImage
      Assert.assertTrue(
        "Valid result image is expected to be bigger than 10x10. It's ${image.width}x${image.height}",
        image.width > 10 && image.height > 10)
      Assert.assertNotNull(image.copy)

      return renderResult
    }

    fun complexRenderScenarioWithBoundsCalculation(projectRule: AndroidGradleProjectRule): RenderResult =
      complexRenderScenario(projectRule).also {
        it.rootViews.forEach { viewInfo ->  parseViewInfo(viewInfo, logger = Logger.getInstance(SimpleComposeProjectScenarios::class.java)) }
      }

    fun interactiveRenderScenario(projectRule: AndroidGradleProjectRule): ExtendedRenderResult {
      val renderTaskFuture = createRenderTaskFuture(projectRule.androidFacet(":app"),
                                                    SingleComposePreviewElementInstance.forTesting(
                                                      "google.simpleapplication.ComplexPreviewKt.ComplexPreview"),
                                                    privateClassLoader = true,
                                                    classesToPreload = LayoutlibSceneManager.INTERACTIVE_CLASSES_TO_PRELOAD.toList())

      // Pseudo interactive
      val frameNanos = 16000000L
      val clickX = 30
      val clickY = 30

      val renderTask = renderTaskFuture.get(1, TimeUnit.MINUTES)
      try {
        val renderResult = renderTask.render().get(1, TimeUnit.MINUTES)
        val firstRenderPixel = renderResult.renderedImage.getPixel(clickX, clickY)
        // Not black and not white
        Assert.assertNotEquals(firstRenderPixel or 0xFFFFFF, 0)
        Assert.assertNotEquals(firstRenderPixel, 0xFFFFFFFF)

        val firstExecutionResult = renderTask.executeCallbacks(0).get(5, TimeUnit.SECONDS)
        val firstTouchEventResult = renderTask.triggerTouchEvent(RenderSession.TouchEventType.PRESS, clickX, clickY, 1000).get(5, TimeUnit.SECONDS)

        renderTask.render().get(5, TimeUnit.SECONDS)
        val postTouchEventResult = renderTask.executeCallbacks(frameNanos).get(5, TimeUnit.SECONDS)
        renderTask.render().get(5, TimeUnit.SECONDS)
        renderTask.executeCallbacks(2 * frameNanos).get(5, TimeUnit.SECONDS)

        renderTask.triggerTouchEvent(RenderSession.TouchEventType.RELEASE, clickX, clickY, 2 * frameNanos + 1000).get(5, TimeUnit.SECONDS)

        renderTask.render().get(5, TimeUnit.SECONDS)
        renderTask.executeCallbacks(3 * frameNanos).get(5, TimeUnit.SECONDS)

        val finalRenderResult = renderTask.render().get(5, TimeUnit.SECONDS)
        val clickPixel = finalRenderResult.renderedImage.getPixel(clickX, clickY)
        // Not the same as in the initial render (there is a ripple)
        Assert.assertNotEquals(clickPixel, firstRenderPixel)
        // Not black and not white
        Assert.assertNotEquals(clickPixel or 0xFFFFFF, 0)
        Assert.assertNotEquals(clickPixel, 0xFFFFFFFF)

        return ExtendedRenderResult.create(renderResult, firstExecutionResult, firstTouchEventResult, postTouchEventResult)
      }
      finally {
        renderTaskFuture.get(5, TimeUnit.SECONDS).dispose().get(5, TimeUnit.SECONDS)
      }
    }
  }
}