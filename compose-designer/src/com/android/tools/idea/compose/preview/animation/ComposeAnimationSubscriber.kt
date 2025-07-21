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
import com.android.tools.idea.compose.preview.animation.ComposeAnimationSubscriber.onAnimationSubscribed
import com.android.tools.idea.compose.preview.animation.ComposeAnimationSubscriber.onAnimationUnsubscribed
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

/**
 * Responsible for animation subscription events and passing them to [ComposeAnimationHandler].
 * `PreviewAnimationClockMethodTransform` intercepts calls to `subscribe` and `unsubscribe` calls on
 * `ui-tooling` and redirects them to [onAnimationSubscribed] and [onAnimationUnsubscribed],
 * respectively.
 */
object ComposeAnimationSubscriber {
  private val LOG = Logger.getInstance(ComposeAnimationSubscriber::class.java)
  private var animationHandler: ComposeAnimationHandler? = null

  suspend fun setHandler(handler: ComposeAnimationHandler?) {
    animationHandler?.removeAllAnimations()?.join()
    animationHandler = handler
  }

  private val onSubscribedUnsubscribedExecutor =
    if (ApplicationManager.getApplication().isUnitTestMode) MoreExecutors.directExecutor()
    else
      AppExecutorUtil.createBoundedApplicationPoolExecutor(
        "Animation Subscribe/Unsubscribe Callback Handler",
        1,
      )

  /**
   * [CoroutineScope] to use in tests, when it's null, [onSubscribedUnsubscribedExecutor] will be
   * used instead.
   */
  private var testScope: CoroutineScope? = null

  private fun createScope(): CoroutineScope {
    return testScope?.takeIf { ApplicationManager.getApplication().isUnitTestMode }
      ?: CoroutineScope(onSubscribedUnsubscribedExecutor.asCoroutineDispatcher())
  }

  @TestOnly
  fun setScopeForTests(scope: CoroutineScope?) {
    testScope = scope
  }

  /** Sets the panel clock and creates the corresponding tab in the [ComposeAnimationPreview]. */
  suspend fun onAnimationSubscribed(clock: Any?, animation: ComposeAnimation) {
    if (clock == null) return
    val handler = animationHandler ?: return
    if (handler.animationClock == null) {
      handler.animationClock = AnimationClock(clock)
    }

    // Handle the case where the clock has changed while the inspector is open. That might happen
    // when we were still processing a
    // subscription from a previous inspector when the new inspector was open. In this case, we
    // should unsubscribe all the previously
    // subscribed animations, as they were tracked by the previous clock.
    handler.animationClock?.let {
      if (it.clock != clock) {
        // Make a copy of the list to prevent ConcurrentModificationException
        handler.removeAllAnimations().join()
        // After unsubscribing the old animations, update the clock
        handler.animationClock = AnimationClock(clock)
      }
    }
    handler.addAnimation(animation).join()
  }

  /** Removes the animation from the corresponding tab in the [ComposeAnimationPreview]. */
  suspend fun onAnimationUnsubscribed(animation: ComposeAnimation) {
    animationHandler?.removeAnimation(animation)?.join()
  }

  @TestOnly fun getHandlerForTests() = animationHandler

  @JvmStatic
  @Suppress("unused") // Called via reflection from PreviewAnimationClockMethodTransform
  fun animationSubscribed(clock: Any?, animation: Any?) {
    if (LOG.isDebugEnabled) {
      LOG.debug("Animation subscribed: $animation")
    }
    createScope().launch {
      (animation as? ComposeAnimation)?.let { onAnimationSubscribed(clock, it) }
    }
  }

  @JvmStatic
  @Suppress("unused") // Called via reflection from PreviewAnimationClockMethodTransform
  fun animationUnsubscribed(animation: Any?) {
    if (LOG.isDebugEnabled) {
      LOG.debug("Animation unsubscribed: $animation")
    }
    if (animation == null) return
    createScope().launch { (animation as? ComposeAnimation)?.let { onAnimationUnsubscribed(it) } }
  }
}
