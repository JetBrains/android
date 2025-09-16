/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.util

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.LOGCAT
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.LOGCAT_USAGE
import com.google.wireless.android.sdk.stats.LogcatUsageEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.StackRetraceEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.Type.STACK_RETRACED
import kotlin.time.Duration

/** A convenience container for usage tracing methods. */
internal object LogcatUsageTracker {
  fun log(logcatUsageEvent: LogcatUsageEvent.Builder) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setCategory(LOGCAT)
        .setKind(LOGCAT_USAGE)
        .setLogcatUsageEvent(logcatUsageEvent)
    )
  }

  fun logRetrace(result: String) {
    log(
      LogcatUsageEvent.newBuilder()
        .setType(STACK_RETRACED)
        .setStackRetrace(StackRetraceEvent.newBuilder().setResultString(result))
    )
  }

  fun logRetraceException(e: Throwable) {
    log(
      LogcatUsageEvent.newBuilder()
        .setType(STACK_RETRACED)
        .setStackRetrace(StackRetraceEvent.newBuilder().setResultString(e.javaClass.simpleName))
    )
  }

  fun logRetrace(result: String, duration: Duration, mappingFileSize: Long, isCached: Boolean) {
    log(
      LogcatUsageEvent.newBuilder()
        .setType(STACK_RETRACED)
        .setStackRetrace(
          StackRetraceEvent.newBuilder()
            .setResultString(result)
            .setRetraceTimeMs(duration.inWholeMilliseconds)
            .setIsMappingCached(isCached)
            .setMappingFileSize(mappingFileSize)
        )
    )
  }
}
