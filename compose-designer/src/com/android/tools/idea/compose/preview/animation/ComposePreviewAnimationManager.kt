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

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.animation.ComposePreviewAnimationManager.onAnimationSubscribed
import com.android.tools.idea.compose.preview.animation.ComposePreviewAnimationManager.onAnimationUnsubscribed
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer

private val LOG = Logger.getInstance(ComposePreviewAnimationManager::class.java)

/**
 * Responsible for opening the [AnimationInspectorPanel] and managing its state. `PreviewAnimationClockMethodTransform` intercepts calls to
 * `subscribe` and `unsubscribe` calls on `ui-tooling` and redirects them to [onAnimationSubscribed] and [onAnimationUnsubscribed],
 * respectively. These methods will then update the [AnimationInspectorPanel] content accordingly, adding tabs for newly subscribed
 * animations and closing tabs corresponding to unsubscribed animations.
 */
object ComposePreviewAnimationManager {
  private var currentInspector: AnimationInspectorPanel? = null

  @get:VisibleForTesting
  val subscribedAnimations = mutableSetOf<Any>()

  fun createAnimationInspectorPanel(surface: DesignSurface, parent: Disposable): AnimationInspectorPanel {
    val animationInspectorPanel = AnimationInspectorPanel(surface)
    Disposer.register(parent, animationInspectorPanel)
    currentInspector = animationInspectorPanel
    return animationInspectorPanel
  }

  fun closeCurrentInspector() {
    currentInspector?.let { Disposer.dispose(it) }
    currentInspector = null
    subscribedAnimations.clear()
  }

  /**
   * Sets the panel clock, adds the animation to the subscribed list, and creates the corresponding tab in the [AnimationInspectorPanel].
   */
  fun onAnimationSubscribed(clock: Any?, animation: Any) {
    currentInspector?.takeIf { it.clock != clock }?.let { it.clock = clock }
    if (subscribedAnimations.add(animation)) {
      // TODO(b/157895086): add animation as a tab in the animation inspector
    }
  }

  /**
   * Removes the animation from the subscribed list and removes the corresponding tab in the [AnimationInspectorPanel].
   */
  fun onAnimationUnsubscribed(animation: Any) {
    subscribedAnimations.remove(animation)
    if (subscribedAnimations.remove(animation)) {
      // TODO(b/157895086): remove animation tab in the animation inspector
    }
    if (subscribedAnimations.isEmpty()) {
      // No more animations. Set the clock to null, so the panel can update accordingly.
      currentInspector?.let { it.clock = null }
    }
  }
}

@Suppress("unused") // Called via reflection from PreviewAnimationClockMethodTransform
fun animationSubscribed(clock: Any?, animation: Any?) {
  if (LOG.isDebugEnabled) {
    LOG.debug("Animation subscribed: $animation")
  }
  if (animation == null) return

  onAnimationSubscribed(clock, animation)
}

@Suppress("unused") // Called via reflection from PreviewAnimationClockMethodTransform
fun animationUnsubscribed(animation: Any?) {
  if (LOG.isDebugEnabled) {
    LOG.debug("Animation unsubscribed: $animation")
  }
  if (animation == null) return

  onAnimationUnsubscribed(animation)
}
