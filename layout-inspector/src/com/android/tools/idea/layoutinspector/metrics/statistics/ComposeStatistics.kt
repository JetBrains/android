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
import com.android.tools.idea.layoutinspector.ui.HIGHLIGHT_COLOR_BLUE
import com.android.tools.idea.layoutinspector.ui.HIGHLIGHT_COLOR_GREEN
import com.android.tools.idea.layoutinspector.ui.HIGHLIGHT_COLOR_ORANGE
import com.android.tools.idea.layoutinspector.ui.HIGHLIGHT_COLOR_PURPLE
import com.android.tools.idea.layoutinspector.ui.HIGHLIGHT_COLOR_RED
import com.android.tools.idea.layoutinspector.ui.HIGHLIGHT_COLOR_YELLOW
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorCompose

/**
 * Accumulator of live mode statistics for compose related events
 */
class ComposeStatistics {
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
   * The current state of Recomposition counts.
   */
  var showRecompositions = false

  /**
   * The currently selected recomposition highlight color.
   */
  var recompositionHighlightColor = HIGHLIGHT_COLOR_RED

  /**
   * Number of frames received with recomposition counts ON.
   */
  private var framesWithRecompositionCountsOn = 0

  /**
   * Number of frames received with recomposition counts ON and specific color selected.
   */
  private var framesWithRecompositionColorRed = 0
  private var framesWithRecompositionColorBlue = 0
  private var framesWithRecompositionColorGreen = 0
  private var framesWithRecompositionColorYellow = 0
  private var framesWithRecompositionColorPurple = 0
  private var framesWithRecompositionColorOrange = 0

  /**
   * Start a new session by resetting all counters.
   */
  fun start() {
    showRecompositions = false
    recompositionHighlightColor = HIGHLIGHT_COLOR_RED
    imageClicks = 0
    componentTreeClicks = 0
    goToSourceFromPropertyValueClicks = 0
    framesWithRecompositionCountsOn = 0
    framesWithRecompositionColorRed = 0
    framesWithRecompositionColorBlue = 0
    framesWithRecompositionColorGreen = 0
    framesWithRecompositionColorYellow = 0
    framesWithRecompositionColorPurple = 0
    framesWithRecompositionColorOrange = 0
  }

  /**
   * Save the session data recorded since [start].
   */
  fun save(dataSupplier: () -> DynamicLayoutInspectorCompose.Builder) {
    if (imageClicks > 0 || componentTreeClicks > 0 || goToSourceFromPropertyValueClicks > 0 || !maxRecompositions.isEmpty ||
        resetRecompositionCountsClicks > 0 || framesWithRecompositionCountsOn > 0) {
      dataSupplier().let {
        it.kotlinReflectionAvailable = true // unused
        it.imageClicks = imageClicks
        it.componentTreeClicks = componentTreeClicks
        it.goToSourceFromPropertyValueClicks = goToSourceFromPropertyValueClicks
        it.maxRecompositionCount = maxRecompositions.count
        it.maxRecompositionSkips = maxRecompositions.skips
        it.maxRecompositionHighlight = maxRecompositions.highlightCount
        it.recompositionResetClicks = resetRecompositionCountsClicks
        it.framesWithRecompositionCountsOn = framesWithRecompositionCountsOn
        it.framesWithRecompositionColorRed = framesWithRecompositionColorRed
        it.framesWithRecompositionColorBlue = framesWithRecompositionColorBlue
        it.framesWithRecompositionColorGreen = framesWithRecompositionColorGreen
        it.framesWithRecompositionColorYellow = framesWithRecompositionColorYellow
        it.framesWithRecompositionColorPurple = framesWithRecompositionColorPurple
        it.framesWithRecompositionColorOrange = framesWithRecompositionColorOrange
      }
    }
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

  fun frameReceived() {
    if (!showRecompositions) {
      return
    }
    framesWithRecompositionCountsOn++
    when (recompositionHighlightColor) {
      HIGHLIGHT_COLOR_RED -> framesWithRecompositionColorRed++
      HIGHLIGHT_COLOR_BLUE -> framesWithRecompositionColorBlue++
      HIGHLIGHT_COLOR_GREEN -> framesWithRecompositionColorGreen++
      HIGHLIGHT_COLOR_YELLOW -> framesWithRecompositionColorYellow++
      HIGHLIGHT_COLOR_PURPLE -> framesWithRecompositionColorPurple++
      HIGHLIGHT_COLOR_ORANGE -> framesWithRecompositionColorOrange++
    }
  }
}
