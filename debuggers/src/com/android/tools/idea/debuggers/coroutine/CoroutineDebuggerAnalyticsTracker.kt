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
package com.android.tools.idea.debuggers.coroutine

import com.android.tools.analytics.UsageTracker
//import com.android.tools.analytics.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.CoroutineDebuggerEvent
import com.intellij.openapi.project.Project

interface CoroutineDebuggerAnalyticsTracker {
  companion object {
    fun getInstance(project: Project): CoroutineDebuggerAnalyticsTracker {
      return project.getService(CoroutineDebuggerAnalyticsTracker::class.java)
    }
  }

  fun trackLaunchEvent(isDisabledInSettings: Boolean)
}

class CoroutineDebuggerAnalyticsTrackerImpl(val project: Project) : CoroutineDebuggerAnalyticsTracker {
  override fun trackLaunchEvent(isDisabledInSettings: Boolean) {
    val coroutineDebuggerEventBuilder = CoroutineDebuggerEvent.newBuilder()
      .setType(CoroutineDebuggerEvent.Type.LAUNCH_EVENT)
      .setDisabledInSettings(isDisabledInSettings)

    track(coroutineDebuggerEventBuilder)
  }

  private fun track(coroutineDebuggerEvent: CoroutineDebuggerEvent.Builder) {
    //val studioEvent: AndroidStudioEvent.Builder = AndroidStudioEvent.newBuilder()
    //  .setKind(AndroidStudioEvent.EventKind.COROUTINE_DEBUGGER)
    //  .setCoroutineDebuggerEvent(coroutineDebuggerEvent)
    //
    //studioEvent.withProjectId(project)
    //UsageTracker.log(studioEvent)
  }
}