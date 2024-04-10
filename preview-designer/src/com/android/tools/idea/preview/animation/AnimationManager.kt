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

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.preview.animation.timeline.ElementState
import com.android.tools.idea.preview.animation.timeline.PositionProxy
import com.android.tools.idea.preview.animation.timeline.TimelineElement
import com.android.tools.idea.preview.animation.timeline.UnsupportedLabel
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.flow.MutableStateFlow

interface AnimationManager {
  val tabTitle: String

  /** [Card] for the current animation in the coordination panel. */
  val card: Card

  /** Maximum ms required in the timeline. */
  val timelineMaximumMs: Int

  /** [TimelineElement] for the current transition displayed in the timeline. */
  fun createTimelineElement(
    parent: JComponent,
    minY: Int,
    forIndividualTab: Boolean,
    positionProxy: PositionProxy,
  ): TimelineElement

  /** Initial setup for this animation before adding it to the panel. */
  suspend fun setup()

  /** Clean up steps for animation before removing it from the panel. */
  suspend fun destroy()
}

/*
 * Supported animation types could be opened in a new tab. Its card could be frozen or expended.
 */
interface SupportedAnimationManager : AnimationManager {
  /** State of the [TimelineElement] for the animation. */
  val elementState: MutableStateFlow<ElementState>

  /** Tab that shows animation individually */
  val tabComponent: JPanel

  /**
   * Adds [timeline] to this tab's layout. The timeline is shared across all tabs, and a Swing
   * component can't be added as a child of multiple components simultaneously. Therefore, this
   * method needs to be called everytime we change tabs.
   */
  @UiThread fun addTimeline(timeline: TimelinePanel)
}

/** Manager for animations we can detect, but can't manipulate,set time/state, etc */
abstract class UnsupportedAnimationManager(final override val tabTitle: String) : AnimationManager {
  override val card = LabelCard(tabTitle)

  override suspend fun setup() {}

  override suspend fun destroy() {}

  override val timelineMaximumMs = 0

  override fun createTimelineElement(
    parent: JComponent,
    minY: Int,
    forIndividualTab: Boolean,
    positionProxy: PositionProxy,
  ): TimelineElement {
    return UnsupportedLabel(
      parent,
      minY,
      positionProxy.minimumXPosition(),
      positionProxy.maximumXPosition(),
    )
  }
}
