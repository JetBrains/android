/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.insights.model.event

import com.android.tools.idea.insights.model.stacktrace.StacktraceGroup
import com.android.tools.idea.insights.model.vcs.AppVcsInfo

/** Representation of an App crash or logged error, having been processed by Crashlytics. */
data class Event(
  // ID of the event
  val name: String = "",

  // Event metadata
  val eventData: EventData = EventData(),

  // Describes the crash or non-fatal error / exception, and potentially the
  // state of the other threads in the process at time of the Event.
  val stacktraceGroup: StacktraceGroup = StacktraceGroup(),
  val appVcsInfo: AppVcsInfo = AppVcsInfo.NONE,
  val customKeys: List<CustomKey> = emptyList(),
  val logs: List<Log> = emptyList(),
) {
  companion object {
    val EMPTY = Event()
  }

  val eventId = name.split("/").last()

  fun isStackTraceEmpty() =
    stacktraceGroup.exceptions.isEmpty() || stacktraceGroup.exceptions.joinToString("").isEmpty()
}
