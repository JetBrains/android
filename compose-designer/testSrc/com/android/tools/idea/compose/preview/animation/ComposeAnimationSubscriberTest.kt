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
import com.android.test.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.compose.preview.animation.TestUtils.createComposeAnimation
import com.android.tools.rendering.classloading.NopClassLocator
import com.android.tools.rendering.classloading.PreviewAnimationClockMethodTransform
import com.android.tools.rendering.classloading.loaders.AsmTransformingLoader
import com.android.tools.rendering.classloading.loaders.ClassLoaderLoader
import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import com.android.tools.rendering.classloading.toClassTransform
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.uipreview.createUrlClassLoader
import org.junit.Assert.*
import org.junit.Test

class ComposeAnimationSubscriberTest : InspectorTests() {
  @Test
  fun subscribeAndUnsubscribe() = runBlocking {
    ComposeAnimationSubscriber.setHandler(animationPreview)

    val animation = createComposeAnimation()
    assertTrue(ComposeAnimationSubscriber.hasNoAnimationsForTests())

    ComposeAnimationSubscriber.onAnimationSubscribed(TestClock(), animation)
    assertFalse(ComposeAnimationSubscriber.hasNoAnimationsForTests())

    val otherAnimation = createComposeAnimation()
    ComposeAnimationSubscriber.onAnimationUnsubscribed(otherAnimation)
    assertFalse(ComposeAnimationSubscriber.hasNoAnimationsForTests())

    ComposeAnimationSubscriber.onAnimationUnsubscribed(animation)
    assertTrue(ComposeAnimationSubscriber.hasNoAnimationsForTests())
  }

  @Test
  fun setNullHandlerClearsSubscriptions() = runBlocking {
    ComposeAnimationSubscriber.setHandler(animationPreview)

    ComposeAnimationSubscriber.onAnimationSubscribed(TestClock(), createComposeAnimation())
    assertFalse(ComposeAnimationSubscriber.hasNoAnimationsForTests())

    ComposeAnimationSubscriber.setHandler(null)
    assertTrue(ComposeAnimationSubscriber.hasNoAnimationsForTests())
  }

  @Test
  fun subscriptionNewClockClearsPreviousClockAnimations() = runBlocking {
    ComposeAnimationSubscriber.setHandler(animationPreview)

    assertNull(animationPreview.tabbedPane.parent)
    assertEquals(0, animationPreview.animations.size)

    val clock = TestClock()
    ComposeAnimationSubscriber.onAnimationSubscribed(clock, createComposeAnimation())
    assertNotNull(animationPreview.tabbedPane.parent)
    assertEquals(1, animationPreview.animations.size)

    val anotherClock = TestClock()
    ComposeAnimationSubscriber.onAnimationSubscribed(anotherClock, createComposeAnimation())
    println("Animations tab: ${animationPreview.animations}")
    assertEquals(1, animationPreview.animations.size)

    ComposeAnimationSubscriber.onAnimationSubscribed(anotherClock, createComposeAnimation())
    assertEquals(2, animationPreview.animations.size)
  }

  @Test
  @Throws(IOException::class, ClassNotFoundException::class)
  fun classLoaderRedirectsSubscriptionToAnimationManager() {
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
    assertFalse(ComposeAnimationSubscriber.hasNoAnimationsForTests())

    val notifyUnsubscribe =
      previewAnimationClock.getDeclaredMethod("notifyUnsubscribe", ComposeAnimation::class.java)
    notifyUnsubscribe.invoke(previewAnimationClock.newInstance(), animation)
    assertTrue(ComposeAnimationSubscriber.hasNoAnimationsForTests())
  }
}
