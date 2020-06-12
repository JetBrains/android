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
package com.android.tools.idea.compose.preview.animation

import com.android.testutils.TestUtils
import com.android.tools.idea.rendering.classloading.PreviewAnimationClockMethodTransform
import com.android.tools.idea.rendering.classloading.RenderClassLoader
import com.google.common.collect.ImmutableList
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.URL

class ComposePreviewAnimationManagerTest {

  @After
  fun tearDown() {
    ComposePreviewAnimationManager.closeCurrentInspector()
  }

  @Test
  fun subscribeAndUnsubscribe() {
    val animation = Any()
    assertTrue(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    ComposePreviewAnimationManager.onAnimationSubscribed(null, animation)
    assertFalse(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    val otherAnimation = Any()
    ComposePreviewAnimationManager.onAnimationUnsubscribed(otherAnimation)
    assertFalse(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    ComposePreviewAnimationManager.onAnimationUnsubscribed(animation)
    assertTrue(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())
  }

  @Test
  fun closingInspectorClearsSubscriptions() {
    ComposePreviewAnimationManager.onAnimationSubscribed(null, Any())
    assertFalse(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    ComposePreviewAnimationManager.closeCurrentInspector()
    assertTrue(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())
  }

  @Test
  @Throws(IOException::class, ClassNotFoundException::class)
  fun classLoaderRedirectsSubscriptionToAnimationManager() {
    class PreviewAnimationClockClassLoader : RenderClassLoader(this.javaClass.classLoader, { PreviewAnimationClockMethodTransform(it) }) {
      override fun getExternalJars(): List<URL> {
        val basePath = TestUtils.getWorkspaceFile("tools/adt/idea/compose-designer/testData/classloader").path
        val jarSource = File(basePath, "composeanimation.jar")
        return ImmutableList.of(jarSource.toURI().toURL())
      }

      fun loadPreviewAnimationClock(): Class<*> {
        return loadClassFromNonProjectDependency("androidx.ui.tooling.preview.animation.PreviewAnimationClock")
      }
    }

    val previewAnimationClockClassLoader = PreviewAnimationClockClassLoader()
    val previewAnimationClock = previewAnimationClockClassLoader.loadPreviewAnimationClock()
    val notifySubscribe = previewAnimationClock.getDeclaredMethod("notifySubscribe", Any::class.java)
    val animation = Any()
    notifySubscribe.invoke(previewAnimationClock.newInstance(), animation)
    assertFalse(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    val notifyUnsubscribe = previewAnimationClock.getDeclaredMethod("notifyUnsubscribe", Any::class.java)
    notifyUnsubscribe.invoke(previewAnimationClock.newInstance(), animation)
    assertTrue(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())
  }
}