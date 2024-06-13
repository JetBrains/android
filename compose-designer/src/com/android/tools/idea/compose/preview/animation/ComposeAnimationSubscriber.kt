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
import com.android.annotations.TestOnly
import com.android.annotations.concurrency.GuardedBy
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job

/**
 * Responsible for animation subscription events and passing them to [ComposeAnimationHandler].
 * `PreviewAnimationClockMethodTransform` intercepts calls to `subscribe` and `unsubscribe` calls on
 * `ui-tooling` and redirects them to [onAnimationSubscribed] and [onAnimationUnsubscribed],
 * respectively.
 */
object ComposeAnimationSubscriber {
  private val LOG = Logger.getInstance(ComposeAnimationSubscriber::class.java)
  private var animationHandler: ComposeAnimationHandler? = null

  fun setHandler(handler: ComposeAnimationHandler?) {
    animationHandler = handler
  }

  @GuardedBy("subscribedAnimationsLock")
  private val subscribedAnimations = mutableSetOf<ComposeAnimation>()
  private val subscribedAnimationsLock = Any()

  private val onSubscribedUnsubscribedExecutor =
    if (ApplicationManager.getApplication().isUnitTestMode) MoreExecutors.directExecutor()
    else
      AppExecutorUtil.createBoundedApplicationPoolExecutor(
        "Animation Subscribe/Unsubscribe Callback Handler",
        1,
      )

  /**
   * Sets the panel clock, adds the animation to the subscribed list, and creates the corresponding
   * tab in the [ComposeAnimationPreview].
   */
  fun onAnimationSubscribed(clock: Any?, animation: ComposeAnimation): Job {
    if (clock == null) return CompletableDeferred(Unit)
    val handler = animationHandler ?: return CompletableDeferred(Unit)
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
        synchronized(subscribedAnimationsLock) { subscribedAnimations.toSet() }
          .forEach { animationToUnsubscribe -> onAnimationUnsubscribed(animationToUnsubscribe) }
        // After unsubscribing the old animations, update the clock
        handler.animationClock = AnimationClock(clock)
      }
    }

    if (synchronized(subscribedAnimationsLock) { subscribedAnimations.add(animation) }) {
      return handler.addAnimation(animation)
    }
    return CompletableDeferred(Unit)
  }

  /**
   * Removes the animation from the subscribed list and removes the corresponding tab in the
   * [ComposeAnimationPreview].
   */
  fun onAnimationUnsubscribed(animation: ComposeAnimation): Job {
    if (synchronized(subscribedAnimationsLock) { subscribedAnimations.remove(animation) }) {
      return animationHandler?.removeAnimation(animation) ?: CompletableDeferred(Unit)
    }
    return CompletableDeferred(Unit)
  }

  /** Removes all the subscribed animations. */
  fun removeAllAnimations() {
    synchronized(subscribedAnimationsLock) { subscribedAnimations.clear() }
  }

  @TestOnly fun hasNoAnimationsForTests() = subscribedAnimations.isEmpty()

  @JvmStatic
  @Suppress("unused") // Called via reflection from PreviewAnimationClockMethodTransform
  fun animationSubscribed(clock: Any?, animation: Any?) {
    if (LOG.isDebugEnabled) {
      LOG.debug("Animation subscribed: $animation")
    }
    onSubscribedUnsubscribedExecutor.execute {
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
    onSubscribedUnsubscribedExecutor.execute {
      (animation as? ComposeAnimation)?.let { onAnimationUnsubscribed(it) }
    }
  }
}
