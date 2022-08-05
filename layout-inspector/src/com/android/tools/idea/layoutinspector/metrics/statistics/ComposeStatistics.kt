/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.metrics.statistics

import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.RecompositionData
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorCompose

/**
 * Accumulator of live mode statistics for compose related events
 */
class ComposeStatistics {
  /**
   * True if the reflection library was available for a compose application
   */
  var reflectionLibraryAvailable = true

  /**
   * How many clicks on a ComposeNode from the image did the user perform
   */
  private var imageClicks = 0

  /**
   * How many clicks on a ComposeNode from the component tree did the user perform
   */
  private var componentTreeClicks = 0

  /**
   * How many clicks on a goto source link from a property value
   */
  private var goToSourceFromPropertyValueClicks = 0

  /**
   * The max recomposition numbers seen in the session.
   */
  private val maxRecompositions = RecompositionData(0, 0)

  /**
   * How many times were the recomposition counts explicitly reset.
   */
  private var resetRecompositionCountsClicks = 0

  /**
   * Start a new session by resetting all counters.
   */
  fun start() {
    reflectionLibraryAvailable = true
    imageClicks = 0
    componentTreeClicks = 0
    goToSourceFromPropertyValueClicks = 0
  }

  /**
   * Save the session data recorded since [start].
   */
  fun save(data: DynamicLayoutInspectorCompose.Builder) {
    data.kotlinReflectionAvailable = reflectionLibraryAvailable
    data.imageClicks = imageClicks
    data.componentTreeClicks = componentTreeClicks
    data.goToSourceFromPropertyValueClicks = goToSourceFromPropertyValueClicks
    data.maxRecompositionCount = maxRecompositions.count
    data.maxRecompositionSkips = maxRecompositions.skips
    data.maxRecompositionHighlight = maxRecompositions.highlightCount
    data.recompositionResetClicks = resetRecompositionCountsClicks
  }

  /**
   * Log that a component was selected from the image.
   */
  fun selectionMadeFromImage(view: ViewNode?) {
    if (view is ComposeViewNode) {
      imageClicks++
    }
  }

  /**
   * Log that a component was selected from the component tree.
   */
  fun selectionMadeFromComponentTree(view: ViewNode?) {
    if (view is ComposeViewNode) {
      componentTreeClicks++
    }
  }

  /**
   * Log that a property value link was used to navigate to source for a compose node.
   */
  fun gotoSourceFromPropertyValue(view: ViewNode?) {
    if (view is ComposeViewNode) {
      goToSourceFromPropertyValueClicks++
    }
  }

  /**
   * Log the max recomposition counts seen in the session.
   */
  fun updateRecompositionStats(recompositions: RecompositionData, maxHighlight: Float) {
    maxRecompositions.maxOf(recompositions)
    maxRecompositions.highlightCount = maxOf(maxRecompositions.highlightCount, maxHighlight)
  }

  /**
   * Log that the recomposition counts were explicitly reset.
   */
  fun resetRecompositionCountsClick() {
    resetRecompositionCountsClicks++
  }
}
