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
import com.android.tools.idea.compose.preview.animation.timeline.UnsupportedLabel
import com.android.tools.idea.preview.animation.LabelCard
import com.android.tools.idea.preview.animation.timeline.ElementState
import com.android.tools.idea.preview.animation.timeline.PositionProxy
import com.android.tools.idea.preview.animation.timeline.TimelineElement
import javax.swing.JComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [ComposeAnimationManager] for unsupported animations. */
class UnsupportedAnimationManager(animation: ComposeAnimation, title: String) :
  ComposeAnimationManager(animation, title) {

  override val card = LabelCard(title)

  override val elementState: StateFlow<ElementState> =
    MutableStateFlow(ElementState()).asStateFlow()

  override suspend fun loadProperties() {}

  override suspend fun setup() {}

  override suspend fun destroy() {}

  override val timelineMaximumMs = 0

  override fun createTimelineElement(
    parent: JComponent,
    minY: Int,
    positionProxy: PositionProxy,
  ): TimelineElement {
    return UnsupportedLabel(parent, minY, positionProxy)
  }
}
