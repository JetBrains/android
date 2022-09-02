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

import com.android.flags.junit.SetFlagRule
import com.android.testutils.ImageDiffUtil.assertImageSimilar
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.renderer.createRenderTaskFuture
import com.android.tools.idea.compose.preview.renderer.renderPreviewElement
import com.android.tools.idea.compose.preview.util.PreviewConfiguration
import com.android.tools.idea.compose.preview.util.SingleComposePreviewElementInstance
import com.android.tools.idea.flags.StudioFlags
import java.nio.file.Paths
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SingleComposePreviewElementRendererTest {
  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  @get:Rule val resetFastPreviewFlag = SetFlagRule(StudioFlags.COMPOSE_FAST_PREVIEW, false)

  /** Checks that trying to render an non-existent preview returns a null image */
  @Test
  fun testInvalidPreview() {
    assertNull(
      renderPreviewElement(
          projectRule.androidFacet(":app"),
          SingleComposePreviewElementInstance.forTesting(
            "google.simpleapplication.MainActivityKt.InvalidPreview"
          )
        )
        .get()
    )
  }

  /** Checks the rendering of the default `@Preview` in the Compose template. */
  @Test
  fun testDefaultPreviewRendering() {
    val defaultRender =
      renderPreviewElement(
          projectRule.androidFacet(":app"),
          SingleComposePreviewElementInstance.forTesting(
            "google.simpleapplication.MainActivityKt.DefaultPreview"
          )
        )
        .get()!!
    assertImageSimilar(
      Paths.get(
        "${projectRule.fixture.testDataPath}/${SIMPLE_COMPOSE_PROJECT_PATH}/defaultRender.png"
      ),
      defaultRender,
      0.1,
      1
    )
  }

  /** Checks the rendering of the default `@Preview` in the Compose template with a background */
  @Test
  fun testDefaultPreviewRenderingWithBackground() {
    val defaultRenderWithBackground =
      renderPreviewElement(
          projectRule.androidFacet(":app"),
          SingleComposePreviewElementInstance.forTesting(
            "google.simpleapplication.MainActivityKt.DefaultPreview",
            showBackground = true,
            backgroundColor = "#F00"
          )
        )
        .get()!!
    assertImageSimilar(
      Paths.get(
        "${projectRule.fixture.testDataPath}/${SIMPLE_COMPOSE_PROJECT_PATH}/defaultRender-withBackground.png"
      ),
      defaultRenderWithBackground,
      0.1,
      1
    )
  }

  /**
   * Checks that the [RenderTask#dispose] releases the `WindowRecomposer#animationScale` that could
   * potentially cause leaks.
   *
   * Regression test for b/244234828.
   */
  @Test
  fun testDisposeOfAnimationScale() {
    val renderTaskFuture =
      createRenderTaskFuture(
        projectRule.androidFacet(":app"),
        SingleComposePreviewElementInstance.forTesting(
          "google.simpleapplication.MainActivityKt.DefaultPreview",
          showBackground = true,
          backgroundColor = "#F00"
        ),
        false
      )
    val renderTask = renderTaskFuture.get()!!
    val result = renderTask.render().get()
    val classLoader = result!!.rootViews.first().viewObject.javaClass.classLoader
    // Check the WindowRecomposer animationScale is empty
    val windowRecomposer =
      classLoader.loadClass("androidx.compose.ui.platform.WindowRecomposer_androidKt")
    val animationScaleField =
      windowRecomposer.getDeclaredField("animationScale").apply { isAccessible = true }

    assertTrue((animationScaleField.get(windowRecomposer) as Map<*, *>).isNotEmpty())
    renderTask.dispose().get()
    assertTrue(
      "animationScale should have been cleared",
      (animationScaleField.get(windowRecomposer) as Map<*, *>).isEmpty()
    )
  }

  @Test
  fun testDefaultPreviewRenderingWithDifferentLocale() {
    val defaultRenderWithLocale =
      renderPreviewElement(
          projectRule.androidFacet(":app"),
          SingleComposePreviewElementInstance.forTesting(
            "google.simpleapplication.MainActivityKt.DefaultPreview",
            configuration =
              PreviewConfiguration.cleanAndGet(null, null, null, null, "en-rUS", null, null, null)
          )
        )
        .get()!!
    assertImageSimilar(
      Paths.get(
        "${projectRule.fixture.testDataPath}/${SIMPLE_COMPOSE_PROJECT_PATH}/defaultRender-withEnUsLocale.png"
      ),
      defaultRenderWithLocale,
      0.1,
      1
    )
  }

  /**
   * Check that rendering a Preview with unsigned types does not throw an exception. And also that
   * limit values for signed and unsigned integers are correctly handled and rendered Regression
   * test for b/204986515
   */
  @Test
  fun testPreviewWithUnsignedTypes() {
    val withUnsignedTypesRender =
      renderPreviewElement(
          projectRule.androidFacet(":app"),
          SingleComposePreviewElementInstance.forTesting(
            "google.simpleapplication.OtherPreviewsKt.PreviewWithUnsignedTypes",
            showBackground = true,
          )
        )
        .get()!!
    assertImageSimilar(
      Paths.get(
        "${projectRule.fixture.testDataPath}/${SIMPLE_COMPOSE_PROJECT_PATH}/withUnsignedTypesRender.png"
      ),
      withUnsignedTypesRender,
      0.1,
      1
    )
  }

  /**
   * Checks the rendering that rendering an empty preview does not throw an exception. Regression
   * test for b/144722608.
   */
  @Test
  fun testEmptyRender() {
    val defaultRender =
      renderPreviewElement(
          projectRule.androidFacet(":app"),
          SingleComposePreviewElementInstance.forTesting(
            "google.simpleapplication.OtherPreviewsKt.EmptyPreview"
          )
        )
        .get()!!

    assertTrue(defaultRender.width > 0 && defaultRender.height > 0)
  }
}
