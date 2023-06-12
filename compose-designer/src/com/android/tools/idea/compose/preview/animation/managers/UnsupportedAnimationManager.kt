/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation.managers

import androidx.compose.animation.tooling.ComposeAnimation
import com.android.tools.idea.compose.preview.animation.LabelCard
import com.android.tools.idea.compose.preview.animation.timeline.ElementState
import com.android.tools.idea.compose.preview.animation.timeline.PositionProxy
import com.android.tools.idea.compose.preview.animation.timeline.TimelineElement
import com.android.tools.idea.compose.preview.animation.timeline.UnsupportedLabel
import javax.swing.JComponent

/** [AnimationManager] for unsupported animations. */
class UnsupportedAnimationManager(animation: ComposeAnimation, title: String) :
  AnimationManager(animation, title) {

  /**
   * State of animation, shared between single animation tab and coordination panel. All callbacks
   * are empty for [UnsupportedAnimationManager].
   */
  override val elementState = ElementState(title)
  override val card = LabelCard(elementState)
  override fun loadProperties() {}

  override fun setup(callback: () -> Unit) {
    // UnsupportedManager doesn't require any additional setup, just call callback.
    callback.invoke()
  }

  override fun createTimelineElement(
    parent: JComponent,
    minY: Int,
    positionProxy: PositionProxy
  ): TimelineElement {
    return UnsupportedLabel(parent, elementState, minY, positionProxy)
  }
}
