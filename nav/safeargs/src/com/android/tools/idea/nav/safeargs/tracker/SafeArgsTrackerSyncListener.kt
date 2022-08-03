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
package com.android.tools.idea.nav.safeargs.tracker

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.google.wireless.android.sdk.stats.NavSafeArgsEvent
import com.intellij.openapi.project.Project

class SafeArgsTrackerSyncListener(project: Project) : ProjectSystemSyncManager.SyncResultListener {
  private val safeArgsTracer = SafeArgsTracker.getInstance(project)

  private fun handleTrackingAnalytics() {
    if (StudioFlags.NAV_SAFE_ARGS_SUPPORT.get()) {
      safeArgsTracer.trackProjectStats(NavSafeArgsEvent.EventContext.SYNC_EVENT_CONTEXT)
    }
  }

  override fun syncEnded(result: ProjectSystemSyncManager.SyncResult) {
    handleTrackingAnalytics()
  }
}
