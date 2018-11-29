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

import com.android.annotations.VisibleForTesting
import com.android.tools.idea.common.analytics.DesignerUsageTrackerManager
import com.android.tools.idea.common.model.NlModel
import com.google.wireless.android.sdk.stats.NavEditorEvent

/**
 * To log a nav editor event, do
 * [NavUsageTracker.getInstance(surface).createEvent(type).withWhateverInfo(...).log()].
 */
interface NavUsageTracker {
  fun createEvent(type: NavEditorEvent.NavEditorEventType) = NavLogEvent(type, this)

  /** Used internally, do not call directly. Use NavLogEvent.log() */
  fun logEvent(event: NavEditorEvent)

  val model: NlModel?

  companion object {
    private val NOP_TRACKER = NavNopTracker()
    @VisibleForTesting
    val MANAGER: DesignerUsageTrackerManager<NavUsageTracker, NlModel> = DesignerUsageTrackerManager(::NavUsageTrackerImpl, NOP_TRACKER)

    fun getInstance(model: NlModel?) = MANAGER.getInstance(model)
  }
}
