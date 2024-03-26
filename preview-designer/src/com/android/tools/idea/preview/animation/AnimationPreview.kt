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
package com.android.tools.idea.preview.animation

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import javax.swing.JPanel

/**
 * Minimum duration for the timeline. For transitions as snaps duration is 0. Minimum timeline
 * duration allows to interact with timeline even for 0-duration animations.
 */
const val MINIMUM_TIMELINE_DURATION_MS = 1000L

/**
 * Provides tools for in-depth inspection of animations within your application.
 *
 * User can inspect animation by using: [timeline]:An interactive timeline view allowing users to
 * scrub through the animation's progress, jump to specific points, and visualize the duration of
 * individual animations. [playbackControls]: Intuitive controls for playing, pausing, and
 * controlling the speed of animation playback.
 *
 * T is subclass of AnimationManager AnimationPreview can work with.
 */
abstract class AnimationPreview<T : AnimationManager>(
  private val sceneManagerProvider: () -> LayoutlibSceneManager?,
  private val tracker: AnimationTracker,
) : Disposable {

  protected val scope = AndroidCoroutineScope(this)

  /**
   * List of [AnimationManager], so all animation cards that are displayed in inspector. Please keep
   * it immutable to avoid [ConcurrentModificationException] as multiple threads can access and
   * modify [animations] at the same time.
   */
  @VisibleForTesting
  var animations: List<T> = emptyList()
    private set

  protected fun addAnimation(animation: T) {
    synchronized(animations) { animations = animations.plus(animation) }
  }

  protected fun removeAnimation(animation: T) {
    synchronized(animations) {
      animations = animations.filterNot { it == animation }
      if (selectedAnimation == animation) {
        selectedAnimation = null
      }
    }
  }

  /** Holds the currently selected animation (for focused inspection on a single tab).* */
  protected var selectedAnimation: SupportedAnimationManager? = null
    private set

  protected fun selectedAnimation(animation: SupportedAnimationManager?) {
    selectedAnimation = animation
  }

  override fun dispose() {}

  protected val animationPreviewPanel =
    JPanel(TabularLayout("*", "*,30px")).apply { name = "Animation Preview" }

  val component = TooltipLayeredPane(animationPreviewPanel)
}
