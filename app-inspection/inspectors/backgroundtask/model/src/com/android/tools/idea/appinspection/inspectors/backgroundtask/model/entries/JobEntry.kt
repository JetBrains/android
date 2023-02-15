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

import backgroundtask.inspection.BackgroundTaskInspectorProtocol
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.BackgroundTaskEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EventWrapper

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
  private var _isValid = false
  private var _retries = 0

  private var isRescheduled = false

  var targetWorkId: String? = null

  override val isValid: Boolean
    get() = _isValid

  override val className
    get() = _className

  override val status
    get() = _status.name

  override val startTimeMs
    get() = _startTime

  override val tags = listOf<String>()
  override val callstacks = mutableListOf<BackgroundTaskCallStack>()
  override val retries: Int
    get() = _retries

  var jobInfo: BackgroundTaskInspectorProtocol.JobInfo? = null
    private set

  var latestEvent: BackgroundTaskInspectorProtocol.Event? = null

  override fun consume(eventWrapper: EventWrapper) {
    latestEvent = eventWrapper.backgroundTaskEvent
    val backgroundTaskEvent = eventWrapper.backgroundTaskEvent.backgroundTaskEvent
    val timestamp = eventWrapper.backgroundTaskEvent.timestamp
    when (backgroundTaskEvent.metadataCase) {
      BackgroundTaskEvent.MetadataCase.JOB_SCHEDULED -> {
        _isValid = true
        _status = State.SCHEDULED
        _startTime = timestamp
        jobInfo = backgroundTaskEvent.jobScheduled.job

        if (isRescheduled) {
          _retries++
          isRescheduled = false
        }

        val serviceName = jobInfo!!.serviceName.substringAfterLast('.')
        _className = serviceName.ifEmpty { "Job $id" }
        // Find target work id from extras.
        jobInfo?.extras?.let { extras ->
          val workIdSuffix = extras.substringAfter("EXTRA_WORK_SPEC_ID=", "")
          if (workIdSuffix.isNotEmpty()) {
            val endIndex =
              workIdSuffix.indexOfFirst { it != '-' && !it.isDigit() && !it.isLetter() }
            if (endIndex != -1) {
              targetWorkId = workIdSuffix.substring(0, endIndex)
            }
          }
        }
        callstacks.clear()
        callstacks.add(BackgroundTaskCallStack(timestamp, backgroundTaskEvent.stacktrace))
      }
      BackgroundTaskEvent.MetadataCase.JOB_STARTED -> {
        _status = State.STARTED
      }
      BackgroundTaskEvent.MetadataCase.JOB_STOPPED -> {
        _status = State.STOPPED
        isRescheduled = backgroundTaskEvent.jobStopped.reschedule
      }
      BackgroundTaskEvent.MetadataCase.JOB_FINISHED -> {
        _status = State.FINISHED
        callstacks.add(BackgroundTaskCallStack(timestamp, backgroundTaskEvent.stacktrace))
        isRescheduled = backgroundTaskEvent.jobFinished.needsReschedule
      }
    }
  }
}
