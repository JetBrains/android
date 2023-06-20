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

import androidx.work.inspection.WorkManagerInspectorProtocol.Event
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkUpdatedEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EventWrapper

/**
 * An entry with all information of a Work Task.
 *
 * @param id a universally unique identifier (UUID) of 128 bit value.
 */
class WorkEntry(override val id: String) : BackgroundTaskEntry {
  private var _isValid = false

  override val isValid
    get() = _isValid

  override val className
    get() = work.workerClassName.substringAfterLast('.')

  override val status
    get() = work.state.name

  override val startTimeMs
    get() = work.scheduleRequestedAt

  override val tags
    get() = work.tagsList.toList()
  override val callstacks = emptyList<BackgroundTaskCallStack>()
  override val retries: Int
    get() = (work.runAttemptCount - 1).takeIf { it >= 0 } ?: 0

  private var work = WorkInfo.newBuilder()

  fun getWorkInfo() = work.build()!!

  override fun consume(eventWrapper: EventWrapper) {
    val event = eventWrapper.workEvent
    when (event.oneOfCase) {
      Event.OneOfCase.WORK_ADDED -> {
        work = event.workAdded.work.toBuilder()
        _isValid = true
      }
      Event.OneOfCase.WORK_UPDATED -> {
        when (event.workUpdated.oneOfCase!!) {
          WorkUpdatedEvent.OneOfCase.STATE -> work.state = event.workUpdated.state
          WorkUpdatedEvent.OneOfCase.SCHEDULE_REQUESTED_AT ->
            work.scheduleRequestedAt = event.workUpdated.scheduleRequestedAt
          WorkUpdatedEvent.OneOfCase.DATA -> work.data = event.workUpdated.data
          WorkUpdatedEvent.OneOfCase.RUN_ATTEMPT_COUNT ->
            work.runAttemptCount = event.workUpdated.runAttemptCount
          else -> throw RuntimeException()
        }
      }
      Event.OneOfCase.WORK_REMOVED -> {
        _isValid = false
      }
      else -> throw RuntimeException()
    }
  }
}
