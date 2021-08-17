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
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JobEntryTest {

  @Test
  fun jobFinished() {
    val jobScheduled = BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder().apply {
      taskId = 1
      stacktrace = "SCHEDULED"
      jobScheduled = BackgroundTaskInspectorProtocol.JobScheduled.newBuilder().apply {
        job = BackgroundTaskInspectorProtocol.JobInfo.newBuilder().apply {
          jobId = 222
          serviceName = "SERVICE"
          extras = "EXTRA_WORK_SPEC_ID=12345&"
        }.build()
        result = BackgroundTaskInspectorProtocol.JobScheduled.Result.RESULT_SUCCESS
      }.build()
    }.build()

    val jobStarted = BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder().apply {
      taskId = 1
      jobStarted = BackgroundTaskInspectorProtocol.JobStarted.newBuilder().apply {
        params = BackgroundTaskInspectorProtocol.JobParameters.newBuilder().apply {
          jobId = 222
        }.build()
        workOngoing = false
      }.build()
    }.build()

    val jobFinished = BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder().apply {
      taskId = 1
      stacktrace = "FINISHED"
      jobFinished = BackgroundTaskInspectorProtocol.JobFinished.newBuilder().apply {
        params = BackgroundTaskInspectorProtocol.JobParameters.newBuilder().apply {
          jobId = 222
        }.build()
        needsReschedule = false
      }.build()
    }.build()

    val jobEntry = JobEntry("1")

    jobEntry.consumeAndAssertJob(jobScheduled) {
      assertThat(startTimeMs).isEqualTo(123)
      assertThat(callstacks).containsExactly("SCHEDULED")
      assertThat(jobInfo).isEqualTo(jobScheduled.jobScheduled.job)
      assertThat(targetWorkId).isEqualTo("12345")
    }

    jobEntry.consumeAndAssertJob(jobStarted)

    jobEntry.consumeAndAssertJob(jobFinished) {
      assertThat(callstacks).containsExactly("SCHEDULED", "FINISHED")
    }
  }

  @Test
  fun jobStopped() {
    val jobScheduled = BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder().apply {
      taskId = 1
      stacktrace = "SCHEDULED"
      jobScheduled = BackgroundTaskInspectorProtocol.JobScheduled.newBuilder().apply {
        job = BackgroundTaskInspectorProtocol.JobInfo.newBuilder().apply {
          jobId = 222
          serviceName = "SERVICE"
          extras = "EXTRA_WORK_SPEC_ID=12345&"
        }.build()
        result = BackgroundTaskInspectorProtocol.JobScheduled.Result.RESULT_SUCCESS
      }.build()
    }.build()

    val jobStarted = BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder().apply {
      taskId = 1
      jobStarted = BackgroundTaskInspectorProtocol.JobStarted.newBuilder().apply {
        params = BackgroundTaskInspectorProtocol.JobParameters.newBuilder().apply {
          jobId = 222
        }.build()
        workOngoing = false
      }.build()
    }.build()

    val jobStopped = BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder().apply {
      taskId = 1
      jobStopped = BackgroundTaskInspectorProtocol.JobStopped.newBuilder().apply {
        params = BackgroundTaskInspectorProtocol.JobParameters.newBuilder().apply {
          jobId = 222
        }.build()
        reschedule = false
      }.build()
    }.build()

    val jobEntry = JobEntry("1")

    jobEntry.consumeAndAssertJob(jobScheduled) {
      assertThat(startTimeMs).isEqualTo(123)
      assertThat(callstacks).containsExactly("SCHEDULED")
      assertThat(jobInfo).isEqualTo(jobScheduled.jobScheduled.job)
      assertThat(targetWorkId).isEqualTo("12345")
    }


    jobEntry.consumeAndAssertJob(jobStarted)

    jobEntry.consumeAndAssertJob(jobStopped) {
      assertThat(callstacks).containsExactly("SCHEDULED")
    }
  }
}

private fun JobEntry.consumeAndAssertJob(
  event: BackgroundTaskInspectorProtocol.BackgroundTaskEvent,
  assertion: JobEntry.() -> Unit = { }
) {
  consumeAndAssert(event) {
    assertThat(latestEvent!!.backgroundTaskEvent).isEqualTo(event)
    assertThat(status).isEqualTo(
      when (event.metadataCase) {
        BackgroundTaskInspectorProtocol.BackgroundTaskEvent.MetadataCase.JOB_SCHEDULED -> JobEntry.State.SCHEDULED
        BackgroundTaskInspectorProtocol.BackgroundTaskEvent.MetadataCase.JOB_STARTED -> JobEntry.State.STARTED
        BackgroundTaskInspectorProtocol.BackgroundTaskEvent.MetadataCase.JOB_STOPPED -> JobEntry.State.STOPPED
        BackgroundTaskInspectorProtocol.BackgroundTaskEvent.MetadataCase.JOB_FINISHED -> JobEntry.State.FINISHED
        else -> JobEntry.State.UNSPECIFIED
      }.name
    )
    assertion()
  }

}