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

import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorSystemNode

/**
 * Accumulator of system view toggle (between hidden and visible system views) statistics
 */
class SystemViewToggleStatistics {
  /**
   * How many clicks to select a View node while system nodes are hidden did the user perform
   */
  private var hiddenSystemViewClicks = 0

  /**
   * How many clicks to select a View node while system nodes are visible did the user perform
   */
  private var visibleSystemViewClicks = 0

  /**
   * The system nodes are currently hidden.
   */
  var hideSystemNodes = false

  /**
   * Start a new session by resetting all counters.
   */
  fun start() {
    hiddenSystemViewClicks = 0
    visibleSystemViewClicks = 0
  }

  /**
   * Save the session data recorded since [start].
   */
  fun save(dataSupplier: () -> DynamicLayoutInspectorSystemNode.Builder) {
    if (hiddenSystemViewClicks > 0 || visibleSystemViewClicks > 0) {
      dataSupplier().let {
        it.clicksWithHiddenSystemViews = hiddenSystemViewClicks
        it.clicksWithVisibleSystemViews = visibleSystemViewClicks
      }
    }
  }

  /**
   * Log that a component was selected.
   */
  fun selectionMade() {
    if (hideSystemNodes) hiddenSystemViewClicks++ else visibleSystemViewClicks++
  }
}
