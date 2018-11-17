/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.analytics

import com.android.tools.idea.common.surface.DesignSurface
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.NavEditorEvent
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.function.Consumer

class NavUsageTrackerImpl(
  private val executor: Executor,
  override val surface: DesignSurface?,
  private val eventLogger: Consumer<AndroidStudioEvent.Builder>
) : NavUsageTracker {

  override fun logEvent(event: NavEditorEvent) {
    try {
      executor.execute {
        val studioEvent = AndroidStudioEvent.newBuilder()
          .setKind(AndroidStudioEvent.EventKind.NAV_EDITOR_EVENT)
          .setNavEditorEvent(event)

        eventLogger.accept(studioEvent)
      }
    }
    catch (e: RejectedExecutionException) {
      // We are hitting the throttling limit
    }
  }
}