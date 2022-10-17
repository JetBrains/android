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
package com.android.tools.idea.layoutinspector.metrics.statistics

import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorLiveMode

/**
 * Accumulator of live mode statistics for analytics.
 */
class LiveModeStatistics {
  /**
   * How many times did the user click the refresh button on the toolbar
   */
  private var refreshButtonClicks = 0

  /**
   * How many times did the user click to select a component while live updates are on
   */
  private var clicksWithLiveUpdates = 0

  /**
   * How many times did the user click to select a component while live updates are off
   */
  private var clicksWithoutLiveUpdates = 0

  /**
   * Inspector is receiving live updates
   */
  var currentModeIsLive = false

  /**
   * Start a new session by resetting all counters.
   */
  fun start() {
    currentModeIsLive = false
    refreshButtonClicks = 0
    clicksWithLiveUpdates = 0
    clicksWithoutLiveUpdates = 0
  }

  /**
   * Save the session data recorded since [start].
   */
  fun save(dataSupplier: () -> DynamicLayoutInspectorLiveMode.Builder) {
    if (refreshButtonClicks > 0 || clicksWithLiveUpdates > 0 || clicksWithoutLiveUpdates > 0) {
      dataSupplier().let {
        it.refreshButtonClicks = refreshButtonClicks
        it.clicksWithLiveUpdates = clicksWithLiveUpdates
        it.clicksWithoutLiveUpdates = clicksWithoutLiveUpdates
      }
    }
  }

  /**
   * Log that the refresh button was activated.
   */
  fun refreshButtonClicked() {
    refreshButtonClicks++
  }

  /**
   * Log that a component was selected.
   */
  fun selectionMade() {
    if (currentModeIsLive) clicksWithLiveUpdates++ else clicksWithoutLiveUpdates++
  }
}
