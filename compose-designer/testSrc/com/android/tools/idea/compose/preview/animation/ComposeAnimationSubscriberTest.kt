/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation

import androidx.compose.animation.tooling.ComposeAnimation
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.compose.preview.animation.TestUtils.createComposeAnimation
import com.android.tools.rendering.classloading.NopClassLocator
import com.android.tools.rendering.classloading.PreviewAnimationClockMethodTransform
import com.android.tools.rendering.classloading.loaders.AsmTransformingLoader
import com.android.tools.rendering.classloading.loaders.ClassLoaderLoader
import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import com.android.tools.rendering.classloading.toClassTransform
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jetbrains.android.uipreview.createUrlClassLoader
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeAnimationSubscriberTest : InspectorTests() {

  @Test
  fun subscribeAndUnsubscribe() = runTest {
    val animationPreview = createAnimationPreview(backgroundScope)

    ComposeAnimationSubscriber.setHandler(animationPreview)

    val animation = createComposeAnimation()
    advanceUntilIdle()
    assertTrue(animationPreview.hasNoAnimationsForTests())

    ComposeAnimationSubscriber.onAnimationSubscribed(TestClock(), animation)
    advanceUntilIdle()
    assertFalse(animationPreview.hasNoAnimationsForTests())

    val otherAnimation = createComposeAnimation()
    ComposeAnimationSubscriber.onAnimationUnsubscribed(otherAnimation)
    advanceUntilIdle()
    assertFalse(animationPreview.hasNoAnimationsForTests())

    ComposeAnimationSubscriber.onAnimationUnsubscribed(animation)
    advanceUntilIdle()
    assertTrue(animationPreview.hasNoAnimationsForTests())
    ComposeAnimationSubscriber.setHandler(null)
  }

  @Test
  fun setNullHandlerClearsSubscriptions() = runTest {
    val animationPreview = createAnimationPreview(backgroundScope)
    ComposeAnimationSubscriber.setHandler(animationPreview)

    ComposeAnimationSubscriber.onAnimationSubscribed(TestClock(), createComposeAnimation())
    advanceUntilIdle()
    assertFalse(animationPreview.hasNoAnimationsForTests())

    ComposeAnimationSubscriber.setHandler(null)
    advanceUntilIdle()
    assertTrue(animationPreview.hasNoAnimationsForTests())
  }

  @Test
  fun subscriptionNewClockClearsPreviousClockAnimations() = runTest {
    val animationPreview = createAnimationPreview(backgroundScope)
    ComposeAnimationSubscriber.setScopeForTests(backgroundScope)
    ComposeAnimationSubscriber.setHandler(animationPreview)
    advanceUntilIdle()
    assertNull(animationPreview.tabbedPane.parent)
    assertEquals(0, animationPreview.animations.size)

    val clock = TestClock()
    ComposeAnimationSubscriber.onAnimationSubscribed(clock, createComposeAnimation())
    advanceUntilIdle()
    assertNotNull(animationPreview.tabbedPane.parent)
    assertEquals(1, animationPreview.animations.size)

    val anotherClock = TestClock()
    ComposeAnimationSubscriber.onAnimationSubscribed(anotherClock, createComposeAnimation())
    advanceUntilIdle()
    assertEquals(1, animationPreview.animations.size)

    ComposeAnimationSubscriber.onAnimationSubscribed(anotherClock, createComposeAnimation())
    advanceUntilIdle()
    assertEquals(2, animationPreview.animations.size)
    ComposeAnimationSubscriber.setHandler(null)
    ComposeAnimationSubscriber.setScopeForTests(null)
  }

  @Test
  @Throws(IOException::class, ClassNotFoundException::class)
  fun classLoaderRedirectsSubscriptionToAnimationManager() = runTest {
    val animationPreview = createAnimationPreview(scope = backgroundScope)
    ComposeAnimationSubscriber.setScopeForTests(scope = backgroundScope)
    ComposeAnimationSubscriber.setHandler(animationPreview)
    class PreviewAnimationClockClassLoader :
      DelegatingClassLoader(
        this.javaClass.classLoader,
        AsmTransformingLoader(
          toClassTransform({ PreviewAnimationClockMethodTransform(it) }),
          ClassLoaderLoader(
            createUrlClassLoader(
              listOf(
                resolveWorkspacePath("tools/adt/idea/compose-designer/testData/classloader")
                  .resolve("composeanimation.jar")
              )
            )
          ),
          NopClassLocator,
        ),
      ) {
      fun loadPreviewAnimationClock(): Class<*> =
        loadClass("androidx.compose.ui.tooling.animation.PreviewAnimationClock")
    }

    val previewAnimationClockClassLoader = PreviewAnimationClockClassLoader()
    val previewAnimationClock = previewAnimationClockClassLoader.loadPreviewAnimationClock()
    val notifySubscribe =
      previewAnimationClock.getDeclaredMethod("notifySubscribe", ComposeAnimation::class.java)
    val animation = createComposeAnimation()
    notifySubscribe.invoke(previewAnimationClock.newInstance(), animation)
    runCurrent()
    advanceUntilIdle()
    assertFalse(animationPreview.hasNoAnimationsForTests())

    val notifyUnsubscribe =
      previewAnimationClock.getDeclaredMethod("notifyUnsubscribe", ComposeAnimation::class.java)
    notifyUnsubscribe.invoke(previewAnimationClock.newInstance(), animation)
    runCurrent()
    advanceUntilIdle()
    assertTrue(animationPreview.hasNoAnimationsForTests())
    ComposeAnimationSubscriber.setScopeForTests(null)
    ComposeAnimationSubscriber.setHandler(null)
  }
}
