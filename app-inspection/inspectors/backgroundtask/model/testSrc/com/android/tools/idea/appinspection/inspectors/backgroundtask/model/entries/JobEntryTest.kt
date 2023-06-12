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
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JobEntryTest {

  @Test
  fun jobFinished() {
    val jobScheduled =
      BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder()
        .apply {
          taskId = 1
          stacktrace = "SCHEDULED"
          jobScheduled =
            BackgroundTaskInspectorProtocol.JobScheduled.newBuilder()
              .apply {
                job =
                  BackgroundTaskInspectorProtocol.JobInfo.newBuilder()
                    .apply {
                      jobId = 222
                      serviceName = "package.SERVICE"
                      extras =
                        BackgroundTaskInspectorTestUtils.createJobInfoExtraWithWorkerId("12345")
                    }
                    .build()
                result = BackgroundTaskInspectorProtocol.JobScheduled.Result.RESULT_SUCCESS
              }
              .build()
        }
        .build()

    val jobStarted =
      BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder()
        .apply {
          taskId = 1
          jobStarted =
            BackgroundTaskInspectorProtocol.JobStarted.newBuilder()
              .apply {
                params =
                  BackgroundTaskInspectorProtocol.JobParameters.newBuilder()
                    .apply { jobId = 222 }
                    .build()
                workOngoing = false
              }
              .build()
        }
        .build()

    val jobFinished =
      BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder()
        .apply {
          taskId = 1
          stacktrace = "FINISHED"
          jobFinished =
            BackgroundTaskInspectorProtocol.JobFinished.newBuilder()
              .apply {
                params =
                  BackgroundTaskInspectorProtocol.JobParameters.newBuilder()
                    .apply { jobId = 222 }
                    .build()
                needsReschedule = false
              }
              .build()
        }
        .build()

    val jobEntry = JobEntry("1")

    jobEntry.consumeAndAssertJob(jobScheduled, 1) {
      assertThat(startTimeMs).isEqualTo(1)
      assertThat(className).isEqualTo("SERVICE")
      assertThat(callstacks).containsExactly(BackgroundTaskCallStack(1, "SCHEDULED"))
      assertThat(jobInfo).isEqualTo(jobScheduled.jobScheduled.job)
      assertThat(targetWorkId).isEqualTo("12345")
      assertThat(isValid).isTrue()
      assertThat(retries).isEqualTo(0)
    }

    jobEntry.consumeAndAssertJob(jobStarted, 2) {
      assertThat(isValid).isTrue()
      assertThat(retries).isEqualTo(0)
    }

    jobEntry.consumeAndAssertJob(jobFinished, 3) {
      assertThat(isValid).isTrue()
      assertThat(callstacks)
        .containsExactly(
          BackgroundTaskCallStack(1, "SCHEDULED"),
          BackgroundTaskCallStack(3, "FINISHED")
        )
      assertThat(retries).isEqualTo(0)
    }
  }

  @Test
  fun jobStoppedAndRetried() {
    val jobScheduled =
      BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder()
        .apply {
          taskId = 1
          stacktrace = "SCHEDULED"
          jobScheduled =
            BackgroundTaskInspectorProtocol.JobScheduled.newBuilder()
              .apply {
                job =
                  BackgroundTaskInspectorProtocol.JobInfo.newBuilder()
                    .apply {
                      jobId = 222
                      serviceName = "SERVICE"
                      extras =
                        BackgroundTaskInspectorTestUtils.createJobInfoExtraWithWorkerId("12345")
                    }
                    .build()
                result = BackgroundTaskInspectorProtocol.JobScheduled.Result.RESULT_SUCCESS
              }
              .build()
        }
        .build()

    val jobStarted =
      BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder()
        .apply {
          taskId = 1
          jobStarted =
            BackgroundTaskInspectorProtocol.JobStarted.newBuilder()
              .apply {
                params =
                  BackgroundTaskInspectorProtocol.JobParameters.newBuilder()
                    .apply { jobId = 222 }
                    .build()
                workOngoing = false
              }
              .build()
        }
        .build()

    val jobStopped =
      BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder()
        .apply {
          taskId = 1
          jobStopped =
            BackgroundTaskInspectorProtocol.JobStopped.newBuilder()
              .apply {
                params =
                  BackgroundTaskInspectorProtocol.JobParameters.newBuilder()
                    .apply { jobId = 222 }
                    .build()
                reschedule = true
              }
              .build()
        }
        .build()

    val jobEntry = JobEntry("1")

    jobEntry.consumeAndAssertJob(jobScheduled, 1) {
      assertThat(startTimeMs).isEqualTo(1)
      assertThat(callstacks).containsExactly(BackgroundTaskCallStack(1, "SCHEDULED"))
      assertThat(jobInfo).isEqualTo(jobScheduled.jobScheduled.job)
      assertThat(targetWorkId).isEqualTo("12345")
      assertThat(isValid).isTrue()
      assertThat(retries).isEqualTo(0)
    }

    jobEntry.consumeAndAssertJob(jobStarted, 2) {
      assertThat(isValid).isTrue()
      assertThat(retries).isEqualTo(0)
    }

    jobEntry.consumeAndAssertJob(jobStopped, 3) {
      assertThat(isValid).isTrue()
      assertThat(callstacks).containsExactly(BackgroundTaskCallStack(1, "SCHEDULED"))
      assertThat(retries).isEqualTo(0)
    }

    jobEntry.consumeAndAssertJob(jobScheduled, 4) {
      assertThat(isValid).isTrue()
      assertThat(retries).isEqualTo(1)
    }
  }

  @Test
  fun missingJobScheduled() {
    val jobStarted =
      BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder()
        .apply {
          taskId = 1
          jobStarted =
            BackgroundTaskInspectorProtocol.JobStarted.newBuilder()
              .apply {
                params =
                  BackgroundTaskInspectorProtocol.JobParameters.newBuilder()
                    .apply { jobId = 222 }
                    .build()
                workOngoing = false
              }
              .build()
        }
        .build()

    val jobFinished =
      BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder()
        .apply {
          taskId = 1
          stacktrace = "FINISHED"
          jobFinished =
            BackgroundTaskInspectorProtocol.JobFinished.newBuilder()
              .apply {
                params =
                  BackgroundTaskInspectorProtocol.JobParameters.newBuilder()
                    .apply { jobId = 222 }
                    .build()
                needsReschedule = false
              }
              .build()
        }
        .build()

    val jobEntry = JobEntry("1")
    jobEntry.consumeAndAssertJob(jobStarted, 1) {
      assertThat(isValid).isFalse()
      assertThat(retries).isEqualTo(0)
    }

    jobEntry.consumeAndAssertJob(jobFinished, 2) {
      assertThat(callstacks).containsExactly(BackgroundTaskCallStack(2, "FINISHED"))
      assertThat(isValid).isFalse()
      assertThat(retries).isEqualTo(0)
    }
  }
}

private fun JobEntry.consumeAndAssertJob(
  event: BackgroundTaskInspectorProtocol.BackgroundTaskEvent,
  timestamp: Long = 123,
  assertion: JobEntry.() -> Unit = {}
) {
  consumeAndAssert(event, timestamp) {
    assertThat(latestEvent!!.backgroundTaskEvent).isEqualTo(event)
    assertThat(status)
      .isEqualTo(
        when (event.metadataCase) {
          BackgroundTaskInspectorProtocol.BackgroundTaskEvent.MetadataCase.JOB_SCHEDULED ->
            JobEntry.State.SCHEDULED
          BackgroundTaskInspectorProtocol.BackgroundTaskEvent.MetadataCase.JOB_STARTED ->
            JobEntry.State.STARTED
          BackgroundTaskInspectorProtocol.BackgroundTaskEvent.MetadataCase.JOB_STOPPED ->
            JobEntry.State.STOPPED
          BackgroundTaskInspectorProtocol.BackgroundTaskEvent.MetadataCase.JOB_FINISHED ->
            JobEntry.State.FINISHED
          else -> JobEntry.State.UNSPECIFIED
        }.name
      )
    assertion()
  }
}
