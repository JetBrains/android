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
package com.android.tools.idea.compose.preview.analytics

import com.android.tools.idea.common.analytics.DesignerUsageTrackerManager
import com.android.tools.idea.common.surface.DesignSurface

/** Interface for usage tracking in the interactive preview. */
interface InteractivePreviewUsageTracker {
  /**
   * Logs interactive session info at the end of the session (when exiting interactive preview or
   * closing the tab), including frames per second, duration and number of user interactions.
   */
  fun logInteractiveSession(fps: Int, durationMs: Int, userInteractions: Int)

  /**
   * Logs startup time of an interactive session. A period from the time a user enables interactive
   * preview to the time the user can actually interact with the preview.
   */
  fun logStartupTime(timeMs: Int, peers: Int)

  companion object {
    private val NOP_TRACKER = InteractiveNopTracker()
    private val MANAGER =
      DesignerUsageTrackerManager<InteractivePreviewUsageTracker, DesignSurface<*>>(
        { executor, _, eventLogger ->
          InteractiveComposePreviewUsageTracker(executor, eventLogger)
        },
        NOP_TRACKER
      )

    fun getInstance(surface: DesignSurface<*>?) = MANAGER.getInstance(surface)
  }
}
