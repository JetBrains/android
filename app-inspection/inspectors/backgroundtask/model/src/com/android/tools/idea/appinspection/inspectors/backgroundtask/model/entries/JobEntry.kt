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

import backgroundtask.inspection.BackgroundTaskInspectorProtocol.BackgroundTaskEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EventWrapper
import java.util.concurrent.TimeUnit

/**
 * An entry with all information of a Job Task.
 *
 * @param id a unique identifier across background entries other than work.
 */
class JobEntry(override val id: String) : BackgroundTaskEntry {
  enum class State {
    SCHEDULED,
    STARTED,
    STOPPED,
    FINISHED,
    UNSPECIFIED
  }

  private var _className = ""
  private var _status = State.UNSPECIFIED
  private var _startTime = -1L
  private var _isValid = true

  override val isValid get() = _isValid

  override val className get() = _className

  override val status get() = _status.name

  override val startTimeMs get() = _startTime

  override fun consume(eventWrapper: EventWrapper) {
    val backgroundTaskEvent = eventWrapper.backgroundTaskEvent.backgroundTaskEvent
    val timestamp = eventWrapper.backgroundTaskEvent.timestamp
    when (backgroundTaskEvent.metadataCase) {
      BackgroundTaskEvent.MetadataCase.JOB_SCHEDULED -> {
        _className = "Job $id"
        _status = State.SCHEDULED
        _startTime = TimeUnit.NANOSECONDS.toMillis(timestamp)
      }
      BackgroundTaskEvent.MetadataCase.JOB_STARTED -> {
        _status = State.STARTED
      }
      BackgroundTaskEvent.MetadataCase.JOB_STOPPED -> {
        _status = State.STOPPED
      }
      BackgroundTaskEvent.MetadataCase.JOB_FINISHED -> {
        _status = State.FINISHED
      }
    }
  }
}
