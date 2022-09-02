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
import com.android.tools.idea.compose.preview.animation.AnimationPreview
import com.android.tools.idea.compose.preview.animation.Card
import com.android.tools.idea.compose.preview.animation.ComposeUnit
import com.android.tools.idea.compose.preview.animation.Transition
import com.android.tools.idea.compose.preview.animation.timeline.ElementState
import com.android.tools.idea.compose.preview.animation.timeline.PositionProxy
import com.android.tools.idea.compose.preview.animation.timeline.TimelineElement
import javax.swing.JComponent
import kotlin.math.max

/**
 * [AnimationManager] is handling the state of one subscribed [animation]. Each [animation] is
 * represented by one row in coordination panel. Supported animation types could also be opened in a
 * new tab. All supported managers are part of [AnimationPreview].
 */
abstract class AnimationManager(val animation: ComposeAnimation, val tabTitle: String) {
  /** State of the [TimelineElement] for the [animation]. */
  abstract val elementState: ElementState

  /** [Card] for the current animation in the coordination panel. */
  abstract val card: Card

  /** Callback when [currentTransition] has been changed. */
  var currentTransitionCallback: (Transition) -> Unit = {}
  /** Animation [Transition]. Could be empty for unsupported or not yet loaded transitions. */
  var currentTransition = Transition()
    protected set(value) {
      field = value
      // If transition has changed, reset it offset.
      elementState.valueOffset = 0
      currentTransitionCallback(value)
    }

  /** Callback when [selectedProperties] has been changed. */
  var selectedPropertiesCallback: (List<ComposeUnit.TimelineUnit>) -> Unit = {}
  /**
   * Currently selected properties in the timeline. Updated everytime the slider has moved or the
   * state of animation has changed. Could be empty if transition is not loaded or not supported.
   */
  var selectedProperties = listOf<ComposeUnit.TimelineUnit>()
    protected set(value) {
      field = value
      selectedPropertiesCallback(value)
    }

  /** [TimelineElement] for the current transition displayed in the timeline. */
  abstract fun createTimelineElement(
    parent: JComponent,
    minY: Int,
    positionProxy: PositionProxy
  ): TimelineElement

  /** Called everytime if [selectedProperties] should be updated. */
  abstract fun loadProperties()

  /** Initial setup for this animation before adding it to the panel. */
  abstract fun setup(callback: () -> Unit)

  /** Maximum ms for [currentTransition] required in the timeline. */
  val timelineMaximumMs: Int?
    get() = currentTransition.endMillis?.let { max(it + elementState.valueOffset, it) }
}
