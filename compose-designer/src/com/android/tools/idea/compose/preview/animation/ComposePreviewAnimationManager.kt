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

import androidx.compose.animation.tooling.ComposeAnimation
import androidx.compose.animation.tooling.ComposeAnimationType
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.animation.ComposePreviewAnimationManager.onAnimationSubscribed
import com.android.tools.idea.compose.preview.animation.ComposePreviewAnimationManager.onAnimationUnsubscribed
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil

private val LOG = Logger.getInstance(ComposePreviewAnimationManager::class.java)

/**
 * Responsible for opening the [AnimationInspectorPanel] and managing its state. `PreviewAnimationClockMethodTransform` intercepts calls to
 * `subscribe` and `unsubscribe` calls on `ui-tooling` and redirects them to [onAnimationSubscribed] and [onAnimationUnsubscribed],
 * respectively. These methods will then update the [AnimationInspectorPanel] content accordingly, adding tabs for newly subscribed
 * animations and closing tabs corresponding to unsubscribed animations.
 */
object ComposePreviewAnimationManager {
  @get:VisibleForTesting
  var currentInspector: AnimationInspectorPanel? = null
    private set

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
  fun onAnimationSubscribed(clock: Any?, animation: ComposeAnimation) {
    currentInspector?.takeIf { it.animationClock?.clock != clock }?.let {
      it.animationClock = clock?.let { clock ->
        AnimationClock(clock)
      }
    }
    if (subscribedAnimations.add(animation)) {
      UIUtil.invokeLaterIfNeeded {
        currentInspector?.addTab(animation)
        if (animation.type == ComposeAnimationType.TRANSITION_ANIMATION) {
          currentInspector?.updateTransitionStates(animation, animation.states)
        }
      }
    }
  }

  /**
   * Removes the animation from the subscribed list and removes the corresponding tab in the [AnimationInspectorPanel].
   */
  fun onAnimationUnsubscribed(animation: ComposeAnimation) {
    if (subscribedAnimations.remove(animation)) {
      UIUtil.invokeLaterIfNeeded {
        currentInspector?.removeTab(animation)
      }
    }
    if (subscribedAnimations.isEmpty()) {
      // No more animations. Set the clock to null, so the panel can update accordingly.
      currentInspector?.let { it.animationClock = null }
    }
  }

  /**
   * Whether the animation inspector is open.
   */
  fun isInspectorOpen() = currentInspector != null

  /**
   * Invalidates the current animation inspector, so it doesn't display animations out-of-date.
   */
  fun invalidate() {
    currentInspector?.let { UIUtil.invokeLaterIfNeeded { it.invalidatePanel() } }
  }
}

@Suppress("unused") // Called via reflection from PreviewAnimationClockMethodTransform
fun animationSubscribed(clock: Any?, animation: Any?) {
  if (LOG.isDebugEnabled) {
    LOG.debug("Animation subscribed: $animation")
  }
  (animation as? ComposeAnimation)?.let { onAnimationSubscribed(clock, it) }
}

@Suppress("unused") // Called via reflection from PreviewAnimationClockMethodTransform
fun animationUnsubscribed(animation: Any?) {
  if (LOG.isDebugEnabled) {
    LOG.debug("Animation unsubscribed: $animation")
  }
  if (animation == null) return
  (animation as? ComposeAnimation)?.let { onAnimationUnsubscribed(it) }
}
