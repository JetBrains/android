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

import androidx.work.inspection.WorkManagerInspectorProtocol
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EventWrapper
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkEntryTest {

  @Test
  fun workStatusUpdates() {
    val workAddedEvent =
      WorkManagerInspectorProtocol.Event.newBuilder()
        .apply {
          workAdded =
            WorkManagerInspectorProtocol.WorkAddedEvent.newBuilder()
              .apply {
                work =
                  WorkManagerInspectorProtocol.WorkInfo.newBuilder()
                    .apply {
                      id = "1"
                      state = WorkManagerInspectorProtocol.WorkInfo.State.ENQUEUED
                      workerClassName = "worker"
                      scheduleRequestedAt = 123
                    }
                    .build()
              }
              .build()
        }
        .build()

    val workUpdatedEvent =
      WorkManagerInspectorProtocol.Event.newBuilder()
        .apply {
          workUpdated =
            WorkManagerInspectorProtocol.WorkUpdatedEvent.newBuilder()
              .apply {
                id = "1"
                state = WorkManagerInspectorProtocol.WorkInfo.State.RUNNING
              }
              .build()
        }
        .build()

    val workRetriesUpdatedEvent =
      WorkManagerInspectorProtocol.Event.newBuilder()
        .apply {
          workUpdated =
            WorkManagerInspectorProtocol.WorkUpdatedEvent.newBuilder()
              .apply {
                id = "1"
                runAttemptCount = 2
              }
              .build()
        }
        .build()

    val workSucceededEvent =
      WorkManagerInspectorProtocol.Event.newBuilder()
        .apply {
          workUpdated =
            WorkManagerInspectorProtocol.WorkUpdatedEvent.newBuilder()
              .apply {
                id = "1"
                state = WorkManagerInspectorProtocol.WorkInfo.State.SUCCEEDED
              }
              .build()
        }
        .build()

    val workRemovedEvent =
      WorkManagerInspectorProtocol.Event.newBuilder()
        .apply {
          workRemoved =
            WorkManagerInspectorProtocol.WorkRemovedEvent.newBuilder().apply { id = "1" }.build()
        }
        .build()

    val entry = WorkEntry("1")
    entry.consumeAndAssert(workAddedEvent)
    assertThat(entry.isValid).isTrue()
    assertThat(entry.status).isEqualTo("ENQUEUED")
    assertThat(entry.isValid).isTrue()
    entry.consumeAndAssert(workUpdatedEvent)
    assertThat(entry.isValid).isTrue()
    assertThat(entry.status).isEqualTo("RUNNING")
    assertThat(entry.retries).isEqualTo(0)
    entry.consumeAndAssert(workRetriesUpdatedEvent)
    assertThat(entry.retries).isEqualTo(1)
    entry.consumeAndAssert(workSucceededEvent)
    assertThat(entry.isValid).isTrue()
    assertThat(entry.status).isEqualTo("SUCCEEDED")
    entry.consumeAndAssert(workRemovedEvent)
    assertThat(entry.isValid).isFalse()
  }

  @Test
  fun missingWorkAdded() {
    val workUpdatedEvent =
      WorkManagerInspectorProtocol.Event.newBuilder()
        .apply {
          workUpdated =
            WorkManagerInspectorProtocol.WorkUpdatedEvent.newBuilder()
              .apply {
                id = "1"
                state = WorkManagerInspectorProtocol.WorkInfo.State.RUNNING
              }
              .build()
        }
        .build()

    val entry = WorkEntry("1")
    entry.consume(EventWrapper(workUpdatedEvent))
    assertThat(entry.status).isEqualTo("RUNNING")
    assertThat(entry.isValid).isFalse()
  }
}

private fun BackgroundTaskEntry.consumeAndAssert(event: WorkManagerInspectorProtocol.Event) {
  val wrapper = EventWrapper(event)
  consume(wrapper)
  assertThat(startTimeMs).isEqualTo(123)
  assertThat(className).isEqualTo("worker")
}
