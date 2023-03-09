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
package com.android.tools.idea.insights.ui

import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.isCancellableTimeoutException
import com.intellij.util.text.DateFormatUtil
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** State class to enrich timestamp targeting different scenarios. */
enum class TimestampState {
  /** It's online, so the corresponding timestamp is meaningful and should be in use. */
  ONLINE,

  /**
   * It's offline, so we don't update the timestamp, instead the previous stored one should be in
   * use.
   */
  OFFLINE,

  /** It's loading or pending ([isCancellableTimeoutException]), no available timestamp yet. */
  UNAVAILABLE,

  /** Initial placeholder state. */
  UNINITIALIZED
}

data class Timestamp(val time: Instant?, val state: TimestampState) {
  fun toDisplayString(nowMs: Long): String {
    return when (state) {
      TimestampState.UNAVAILABLE -> TEXT_REFRESHING
      TimestampState.OFFLINE ->
        "Currently offline. Last refreshed: ${getHowLongAgo(time?.toEpochMilli(), nowMs)}"
      else -> "Last refreshed: ${getHowLongAgo(time?.toEpochMilli(), nowMs)}"
    }
  }

  private fun getHowLongAgo(timeMs: Long?, nowMs: Long): String {
    return timeMs?.let { DateFormatUtil.formatBetweenDates(it, nowMs) } ?: TEXT_NEVER
  }

  companion object {
    private const val TEXT_NEVER = "never"
    private const val TEXT_REFRESHING = "Refreshing..."
    val UNINITIALIZED = Timestamp(null, TimestampState.UNINITIALIZED)
  }
}

fun Flow<AppInsightsState>.toTimestamp(clock: Clock): Flow<Timestamp> {
  return map { state ->
    when (val issues = state.issues) {
      is LoadingState.Ready -> {
        if (state.mode.isOfflineMode()) Timestamp(null, TimestampState.OFFLINE)
        else Timestamp(issues.value.time, TimestampState.ONLINE)
      }
      is LoadingState.Failure -> {
        if (issues.isCancellableTimeoutException()) {
          // Special handling for CancellableTimeoutException (to be deprecated by offline mode)
          // from long-running fetching
          Timestamp(null, TimestampState.UNAVAILABLE)
        } else {
          // Simply return "now" when asked as it's just for display purpose.
          Timestamp(clock.instant(), TimestampState.ONLINE)
        }
      }
      is LoadingState.Loading -> Timestamp(null, TimestampState.UNAVAILABLE)
    }
  }
}
