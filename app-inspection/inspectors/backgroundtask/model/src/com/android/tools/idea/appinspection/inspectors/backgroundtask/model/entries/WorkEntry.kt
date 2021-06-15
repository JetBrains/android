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

/**
 * An entry with all information of a Work Task.
 */
class WorkEntry(override val id: String) : BackgroundTaskEntry {

  private var _className = ""
  private var _status = WorkInfo.State.UNSPECIFIED
  private var _startTime = -1L
  private var _isValid = true

  override val isValid get() = _isValid

  override val className get() = _className

  override val status get() = _status.name

  override val startTimeMs get() = _startTime

  override fun consume(event: Any) {
    when ((event as Event).oneOfCase) {
      Event.OneOfCase.WORK_ADDED -> {
        _className = event.workAdded.work.workerClassName.substringAfterLast('.')
        _status = event.workAdded.work.state
        _startTime = event.workAdded.work.scheduleRequestedAt
      }
      Event.OneOfCase.WORK_UPDATED -> {
        when (event.workUpdated.oneOfCase!!) {
          WorkUpdatedEvent.OneOfCase.STATE -> _status = event.workUpdated.state
          WorkUpdatedEvent.OneOfCase.SCHEDULE_REQUESTED_AT -> _startTime = event.workUpdated.scheduleRequestedAt
        }
      }
      Event.OneOfCase.WORK_REMOVED -> {
        _isValid = false
      }
    }
  }
}
