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

class AlarmEntryTest {

  @Test
  fun alarmCancelled() {
    val alarmEntry = AlarmEntry("1")
    val setEvent = BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder().apply {
      taskId = 1
      stacktrace = "SET"
      alarmSet = BackgroundTaskInspectorProtocol.AlarmSet.newBuilder().apply {
        type = BackgroundTaskInspectorProtocol.AlarmSet.Type.RTC
        triggerMs = 2L
        listener = BackgroundTaskInspectorProtocol.AlarmListener.newBuilder().apply {
          tag = "TAG1"
        }.build()
      }.build()
    }.build()

    val cancelledEvent = BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder().apply {
      taskId = 1
      stacktrace = "CANCELLED"
      alarmCancelled = BackgroundTaskInspectorProtocol.AlarmCancelled.getDefaultInstance()
    }.build()

    alarmEntry.consumeAndAssert(setEvent) {
      this as AlarmEntry
      assertThat(status).isEqualTo("SET")
      assertThat(alarmSet).isEqualTo(setEvent.alarmSet)
      assertThat(startTimeMs).isEqualTo(2)
      assertThat(callstacks).containsExactly("SET")
      assertThat(tags).containsExactly("TAG1")
    }

    alarmEntry.consumeAndAssert(cancelledEvent) {
      assertThat(status).isEqualTo("CANCELLED")
      assertThat(callstacks).containsExactly("SET", "CANCELLED")
    }
  }

  @Test
  fun alarmFired() {
    val alarmEntry = AlarmEntry("1")
    val setEvent = BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder().apply {
      taskId = 1
      stacktrace = "SET"
      alarmSet = BackgroundTaskInspectorProtocol.AlarmSet.newBuilder().apply {
        type = BackgroundTaskInspectorProtocol.AlarmSet.Type.RTC
        triggerMs = 2L
        listener = BackgroundTaskInspectorProtocol.AlarmListener.newBuilder().apply {
          tag = "TAG1"
        }.build()
      }.build()
    }.build()

    val firedEvent = BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder().apply {
      taskId = 1
      alarmFired = BackgroundTaskInspectorProtocol.AlarmFired.getDefaultInstance()
    }.build()

    alarmEntry.consumeAndAssert(setEvent) {
      this as AlarmEntry
      assertThat(status).isEqualTo("SET")
      assertThat(alarmSet).isEqualTo(setEvent.alarmSet)
      assertThat(startTimeMs).isEqualTo(2)
      assertThat(callstacks).containsExactly("SET")
      assertThat(tags).containsExactly("TAG1")
      assertThat(isValid).isTrue()
    }

    alarmEntry.consumeAndAssert(firedEvent) {
      assertThat(status).isEqualTo("FIRED")
      assertThat(callstacks).containsExactly("SET")
      assertThat(isValid).isTrue()
    }
  }

  @Test
  fun missingAlarmSet() {
    val alarmEntry = AlarmEntry("1")

    val firedEvent = BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder().apply {
      taskId = 1
      alarmFired = BackgroundTaskInspectorProtocol.AlarmFired.getDefaultInstance()
    }.build()

    alarmEntry.consumeAndAssert(firedEvent) {
      assertThat(status).isEqualTo("FIRED")
      assertThat(isValid).isFalse()
    }
  }
}
