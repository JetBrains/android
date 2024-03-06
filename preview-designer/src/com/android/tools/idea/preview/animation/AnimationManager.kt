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

import com.android.tools.idea.preview.animation.timeline.ElementState
import com.android.tools.idea.preview.animation.timeline.PositionProxy
import com.android.tools.idea.preview.animation.timeline.TimelineElement
import javax.swing.JComponent
import kotlinx.coroutines.flow.StateFlow

interface AnimationManager {
  val tabTitle: String

  /** State of the [TimelineElement] for the [animation]. */
  val elementState: StateFlow<ElementState>

  /** [Card] for the current animation in the coordination panel. */
  val card: Card

  /** Maximum ms required in the timeline. */
  val timelineMaximumMs: Int

  /** [TimelineElement] for the current transition displayed in the timeline. */
  fun createTimelineElement(
    parent: JComponent,
    minY: Int,
    positionProxy: PositionProxy,
  ): TimelineElement

  /** Initial setup for this animation before adding it to the panel. */
  suspend fun setup()

  /** Clean up steps for animation before removing it from the panel. */
  suspend fun destroy()
}
