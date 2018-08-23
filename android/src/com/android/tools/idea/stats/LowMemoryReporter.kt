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
package com.android.tools.idea.stats

import com.android.tools.analytics.CommonMetricsData
import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.util.LowMemoryWatcher

class LowMemoryReporter : ApplicationComponent {
  private var lowMemoryWatcher: LowMemoryWatcher? = null

  override fun initComponent() {
    lowMemoryWatcher = LowMemoryWatcher.register {
      // According to https://docs.oracle.com/javase/7/docs/api/java/lang/management/MemoryNotificationInfo.html#MEMORY_THRESHOLD_EXCEEDED,
      // the notification is emitted once when the threshold is exceeded, and is not emitted again until the VM goes below the threshold.
      // So we don't have to explicitly rate limit our reports.
      UsageTracker.log(AndroidStudioEvent.newBuilder()
                         .setKind(AndroidStudioEvent.EventKind.STUDIO_LOW_MEMORY_EVENT)
                         .setJavaProcessStats(CommonMetricsData.javaProcessStats))
    }
  }

  override fun disposeComponent() {
    lowMemoryWatcher?.stop()
    lowMemoryWatcher = null
  }
}