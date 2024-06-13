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

import com.android.ide.common.rendering.api.RenderSession
import com.android.testutils.ImageDiffUtil.assertImageSimilar
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.testing.virtualFile
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.android.tools.rendering.classloading.ModuleClassLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.KEY_LOCATION_STANDARD
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JPanel

class SingleComposePreviewElementRendererTest {
  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  /** Checks that trying to render an non-existent preview returns a null image */
  @Test
  fun testInvalidPreview() {
    val facet = projectRule.androidFacet(":app")
    val mainActivityFile =
      facet.virtualFile("src/main/java/google/simpleapplication/MainActivity.kt")
    assertNull(
      renderPreviewElement(
          facet,
          mainActivityFile,
          SingleComposePreviewElementInstance.forTesting(
            "google.simpleapplication.MainActivityKt.InvalidPreview"
          ),
        )
        .get()
    )
  }

  /** Checks the rendering of the default `@Preview` in the Compose template. */
  @Test
  fun testDefaultPreviewRendering() {
    val facet = projectRule.androidFacet(":app")
    val mainActivityFile =
      facet.virtualFile("src/main/java/google/simpleapplication/MainActivity.kt")
    val defaultRender =
      renderPreviewElement(
          facet,
          mainActivityFile,
          SingleComposePreviewElementInstance.forTesting(
            "google.simpleapplication.MainActivityKt.DefaultPreview"
          ),
        )
        .get()!!
    assertImageSimilar(
      Paths.get(
        "${projectRule.fixture.testDataPath}/${SIMPLE_COMPOSE_PROJECT_PATH}/defaultRender.png"
      ),
      defaultRender,
      0.1,
      1,
    )
  }

  /** Checks the rendering of the default `@Preview` in the Compose template with a background */
  @Test
  fun testDefaultPreviewRenderingWithBackground() {
    val facet = projectRule.androidFacet(":app")
    val mainActivityFile =
      facet.virtualFile("src/main/java/google/simpleapplication/MainActivity.kt")
    val defaultRenderWithBackground =
      renderPreviewElement(
          facet,
          mainActivityFile,
          SingleComposePreviewElementInstance.forTesting(
            "google.simpleapplication.MainActivityKt.DefaultPreview",
            showBackground = true,
            backgroundColor = "#F00",
          ),
        )
        .get()!!
    assertImageSimilar(
      Paths.get(
        "${projectRule.fixture.testDataPath}/${SIMPLE_COMPOSE_PROJECT_PATH}/defaultRender-withBackground.png"
      ),
      defaultRenderWithBackground,
      0.1,
      1,
    )
  }

  /**
   * Checks that the [RenderTask#dispose] releases the `WindowRecomposer#animationScale` that could
   * potentially cause leaks.
   *
   * Regression test for b/179195773, b/244234828 and b/247681348.
   */
  @Test
  fun testDisposeOfComposeLeaks() {
    val facet = projectRule.androidFacet(":app")
    val mainActivityFile =
      facet.virtualFile("src/main/java/google/simpleapplication/MainActivity.kt")
    val renderTaskFuture =
      createRenderTaskFuture(
        facet,
        mainActivityFile,
        SingleComposePreviewElementInstance.forTesting(
          "google.simpleapplication.MainActivityKt.DefaultPreview",
          showBackground = true,
          backgroundColor = "#F00",
        ),
        false,
      )
    val renderTask = renderTaskFuture.future.get()!!
    val result = renderTask.render().get()
    val classLoader =
      result!!.rootViews.first().viewObject.javaClass.classLoader as ModuleClassLoader

    // Ensure that the classes we will check were loaded by the test first. If not, it could be the
    // class has been renamed
    // or the test is not triggering the leak again.
    val leakCheckClasses =
      listOf(
        "androidx.compose.ui.platform.WindowRecomposer_androidKt",
        "androidx.compose.runtime.snapshots.SnapshotKt",
        "androidx.compose.ui.platform.AndroidUiDispatcher",
        "androidx.compose.ui.platform.AndroidUiDispatcher\$Companion",
        "_layoutlib_._internal_.kotlin.coroutines.CombinedContext",
      )
    leakCheckClasses.forEach { assertTrue("Test did not load $it", classLoader.hasLoadedClass(it)) }

    // Check the WindowRecomposer animationScale is empty
    val windowRecomposer =
      classLoader.loadClass("androidx.compose.ui.platform.WindowRecomposer_androidKt")
    val animationScaleField =
      windowRecomposer.getDeclaredField("animationScale").apply { isAccessible = true }

    val fontRequestWorker = classLoader.loadClass("androidx.core.provider.FontRequestWorker")
    val pendingRepliesField =
      fontRequestWorker.getDeclaredField("PENDING_REPLIES").apply { isAccessible = true }
    val pendingReplies = pendingRepliesField.get(fontRequestWorker)
    val size = pendingReplies::class.java.getMethod("size").invoke(pendingReplies) as Int
    assertEquals("FontRequestWorker.PENDING_REPLIES size must be 0 after render", 0, size)

    assertTrue((animationScaleField.get(windowRecomposer) as Map<*, *>).isNotEmpty())

    val snapshotKt = classLoader.loadClass("androidx.compose.runtime.snapshots.SnapshotKt")
    val applyObserversField =
      snapshotKt.getDeclaredField("applyObservers").apply { isAccessible = true }
    val applyObservers = applyObserversField.get(null) as List<*>

    assertTrue(applyObservers.isNotEmpty())

    val globalWriteObserversField =
      snapshotKt.getDeclaredField("globalWriteObservers").apply { isAccessible = true }
    val globalWriteObservers = globalWriteObserversField.get(null) as List<*>

    assertTrue(globalWriteObservers.isNotEmpty())

    val uiDispatcher = classLoader.loadClass("androidx.compose.ui.platform.AndroidUiDispatcher")
    val uiDispatcherCompanion =
      classLoader.loadClass("androidx.compose.ui.platform.AndroidUiDispatcher\$Companion")
    val uiDispatcherCompanionField = uiDispatcher.getDeclaredField("Companion")
    val uiDispatcherCompanionObj = uiDispatcherCompanionField[null]
    val getMainMethod =
      uiDispatcherCompanion.getDeclaredMethod("getMain").apply { isAccessible = true }
    val mainObj = getMainMethod.invoke(uiDispatcherCompanionObj)
    val combinedContext =
      classLoader.loadClass("_layoutlib_._internal_.kotlin.coroutines.CombinedContext")
    val elementField = combinedContext.getDeclaredField("element").apply { isAccessible = true }
    val uiDispatcherObj = elementField[mainObj]
    val toRunTrampolinedField =
      uiDispatcher.getDeclaredField("toRunTrampolined").apply { isAccessible = true }
    val toRunTrampolined = toRunTrampolinedField[uiDispatcherObj] as MutableCollection<*>

    assertTrue(toRunTrampolined.isNotEmpty())

    renderTask.dispose().get()
    assertTrue(
      "animationScale should have been cleared",
      (animationScaleField.get(windowRecomposer) as Map<*, *>).isEmpty(),
    )
    assertTrue("applyObservers should have been cleared", applyObservers.isEmpty())
    assertTrue("globalWriteObservers should have been cleared", globalWriteObservers.isEmpty())
    assertTrue("toRunTrampolined should have been cleared", toRunTrampolined.isEmpty())
  }

  @Test
  fun testDefaultPreviewRenderingWithDifferentLocale() {
    val facet = projectRule.androidFacet(":app")
    val mainActivityFile =
      facet.virtualFile("src/main/java/google/simpleapplication/MainActivity.kt")
    val defaultRenderWithLocale =
      renderPreviewElement(
          facet,
          mainActivityFile,
          SingleComposePreviewElementInstance.forTesting(
            "google.simpleapplication.MainActivityKt.DefaultPreview",
            configuration =
              PreviewConfiguration.cleanAndGet(null, null, null, null, "en-rUS", null, null, null),
          ),
        )
        .get()!!
    assertImageSimilar(
      Paths.get(
        "${projectRule.fixture.testDataPath}/${SIMPLE_COMPOSE_PROJECT_PATH}/defaultRender-withEnUsLocale.png"
      ),
      defaultRenderWithLocale,
      0.1,
      1,
    )
  }

  /**
   * Check that rendering a Preview with unsigned types does not throw an exception. And also that
   * limit values for signed and unsigned integers are correctly handled and rendered Regression
   * test for b/204986515
   */
  @Test
  fun testPreviewWithUnsignedTypes() {
    val facet = projectRule.androidFacet(":app")
    val mainActivityFile =
      facet.virtualFile("src/main/java/google/simpleapplication/MainActivity.kt")
    val withUnsignedTypesRender =
      renderPreviewElement(
          facet,
          mainActivityFile,
          SingleComposePreviewElementInstance.forTesting(
            "google.simpleapplication.OtherPreviewsKt.PreviewWithUnsignedTypes",
            showBackground = true,
          ),
        )
        .get()!!
    assertImageSimilar(
      Paths.get(
        "${projectRule.fixture.testDataPath}/${SIMPLE_COMPOSE_PROJECT_PATH}/withUnsignedTypesRender.png"
      ),
      withUnsignedTypesRender,
      0.1,
      1,
    )
  }

  /**
   * Checks the rendering that rendering an empty preview does not throw an exception. Regression
   * test for b/144722608.
   */
  @Test
  fun testEmptyRender() {
    val facet = projectRule.androidFacet(":app")
    val mainActivityFile =
      facet.virtualFile("src/main/java/google/simpleapplication/MainActivity.kt")
    val defaultRender =
      renderPreviewElementForResult(
          facet,
          mainActivityFile,
          SingleComposePreviewElementInstance.forTesting(
            "google.simpleapplication.OtherPreviewsKt.EmptyPreview"
          ),
        )
        .future
        .get()!!

    assertEquals(0, defaultRender.renderedImage.width)
    assertEquals(0, defaultRender.renderedImage.height)
  }

  /** Checks that key events are correctly dispatched to Compose Preview. */
  @Test
  fun testKeyEvent() {
    val facet = projectRule.androidFacet(":app")
    val otherPreviewFile =
      facet.virtualFile("src/main/java/google/simpleapplication/OtherPreviews.kt")
    val renderTaskFuture =
      createRenderTaskFuture(
        facet,
        otherPreviewFile,
        SingleComposePreviewElementInstance.forTesting(
          "google.simpleapplication.OtherPreviewsKt.TextFieldPreview"
        ),
        false,
      )
    val frameNanos = 16000000L
    val renderTask = renderTaskFuture.future.get(1, TimeUnit.MINUTES)
    try {
      renderTask.render().get(1, TimeUnit.MINUTES)
      renderTask.executeCallbacks(0).get(5, TimeUnit.SECONDS)

      // Start by clicking on the text field to make it focused
      val clickX = 30
      val clickY = 30
      renderTask
        .triggerTouchEvent(RenderSession.TouchEventType.PRESS, clickX, clickY, 1000)
        .get(5, TimeUnit.SECONDS)
      renderTask
        .triggerTouchEvent(RenderSession.TouchEventType.RELEASE, clickX, clickY, 2000)
        .get(5, TimeUnit.SECONDS)

      var time = 10 * frameNanos
      // Give time for the setup of the TextField
      repeat(5) {
        renderTask.render().get(5, TimeUnit.SECONDS)
        renderTask.executeCallbacks(time).get(5, TimeUnit.SECONDS)
        time += 10 * frameNanos
      }

      // Press letter 'p'
      val event =
        KeyEvent(
          JPanel(),
          KeyEvent.KEY_PRESSED,
          time + 1000,
          0,
          KeyEvent.VK_P,
          'p',
          KEY_LOCATION_STANDARD,
        )
      renderTask.triggerKeyEvent(event, time + 1000).get(5, TimeUnit.SECONDS)

      time += 10 * frameNanos
      renderTask.render().get(5, TimeUnit.SECONDS)
      renderTask.executeCallbacks(time).get(5, TimeUnit.SECONDS)

      val renderResult = renderTask.render().get(5, TimeUnit.SECONDS)
      renderResult.renderedImage.copy?.let {
        assertImageSimilar(
          Paths.get(
            "${projectRule.fixture.testDataPath}/${SIMPLE_COMPOSE_PROJECT_PATH}/keyEventRender.png"
          ),
          it,
          0.1,
          1,
        )
      }
    } finally {
      renderTask.dispose().get(5, TimeUnit.SECONDS)
    }
  }

  /** Check that interrupting the recomposition does not leak. */
  @Test
  fun testRecomposeLeakCheck() {
    var classLoader: ModuleClassLoader? = null
    repeat(5) {
      val facet = projectRule.androidFacet(":app")
      val leakCheckFile = facet.virtualFile("src/main/java/google/simpleapplication/LeakCheck.kt")
      val renderTaskFuture =
        createRenderTaskFuture(
          facet,
          leakCheckFile,
          SingleComposePreviewElementInstance.forTesting(
            "google.simpleapplication.LeakCheckKt.WithException"
          ),
          false,
        )
      val renderTask = renderTaskFuture.future.get()!!
      val result = renderTask.render().get()
      assertFalse("The render should have failed", result.renderResult.isSuccess)
      classLoader = renderTask.classLoader as ModuleClassLoader
      renderTaskFuture.future.get().dispose().get()
    }

    // Ensure that the classes we will check were loaded by the test first. If not, it could be the
    // class has been renamed
    // or the test is not triggering the leak again.
    val leakCheckClasses =
      listOf(
        "androidx.compose.runtime.Recomposer",
        "androidx.compose.runtime.Recomposer\$Companion",
      )
    leakCheckClasses.forEach {
      assertTrue("Test did not load $it", classLoader!!.hasLoadedClass(it))
    }

    val recomposerClass = classLoader!!.loadClass("androidx.compose.runtime.Recomposer")
    val recomposerCompanion = recomposerClass.getField("Companion").get(null)
    val recomposerCompanionClass =
      classLoader!!.loadClass("androidx.compose.runtime.Recomposer\$Companion")

    // This relies on the internals of the Compose runtime. If the field is moved or renamed, this
    // will fail. This check is not critical for the test, but it is useful to verify that
    // the setHotReloadEnabled is invoked.
    @Suppress("UNCHECKED_CAST")
    val hotReloadEnabled =
      recomposerClass
        .getDeclaredField("_hotReloadEnabled")
        .also { it.isAccessible = true }
        .get(null) as AtomicReference<Boolean>
    assertTrue(hotReloadEnabled.get())

    val runningRecomposers =
      recomposerClass
        .getDeclaredField("_runningRecomposers")
        .apply { isAccessible = true }
        .get(null)
    val currentRunningSet =
      runningRecomposers::class
        .java
        .getMethod("getValue")
        .apply { isAccessible = true }
        .invoke(runningRecomposers) as Set<*>

    assertTrue(currentRunningSet.isEmpty())
  }
}
