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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries

import backgroundtask.inspection.BackgroundTaskInspectorProtocol.BackgroundTaskEvent.MetadataCase
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EventWrapper
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.getId

/** An entry with necessary information for a background task to show in the tree table. */
interface BackgroundTaskEntry {
  /** A unique id across different background tasks. */
  val id: String
  val isValid: Boolean
  val className: String
  val status: String
  val startTimeMs: Long
  val tags: List<String>
  val callstacks: List<BackgroundTaskCallStack>
  val retries: Int

  /** Updates entry information with [event]. */
  fun consume(eventWrapper: EventWrapper)
}

data class BackgroundTaskCallStack(val triggerTime: Long, val stack: String)

fun createBackgroundTaskEntry(event: EventWrapper): BackgroundTaskEntry =
  when (event.case) {
    EventWrapper.Case.WORK -> WorkEntry(event.workEvent.getId())
    EventWrapper.Case.BACKGROUND_TASK -> {
      val backgroundTaskEvent = event.backgroundTaskEvent
      val id = backgroundTaskEvent.getId().toString()
      when (backgroundTaskEvent.backgroundTaskEvent.metadataCase) {
        MetadataCase.JOB_SCHEDULED,
        MetadataCase.JOB_STARTED,
        MetadataCase.JOB_STOPPED,
        MetadataCase.JOB_FINISHED -> JobEntry(id)
        MetadataCase.ALARM_SET,
        MetadataCase.ALARM_CANCELLED,
        MetadataCase.ALARM_FIRED -> AlarmEntry(id)
        MetadataCase.WAKE_LOCK_ACQUIRED,
        MetadataCase.WAKE_LOCK_RELEASED -> WakeLockEntry(id)
        else -> throw RuntimeException()
      }
    }
  }
