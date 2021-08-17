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

class WakeLockEntryTest {

  @Test
  fun wakeLock() {
    val wakeLockAcquiredEvent = BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder().apply {
      taskId = 1
      stacktrace = "ACQUIRED"
      wakeLockAcquired = BackgroundTaskInspectorProtocol.WakeLockAcquired.newBuilder().apply {
        level = BackgroundTaskInspectorProtocol.WakeLockAcquired.Level.PARTIAL_WAKE_LOCK
        tag = "TAG1"
        addFlags(BackgroundTaskInspectorProtocol.WakeLockAcquired.CreationFlag.ACQUIRE_CAUSES_WAKEUP)
      }.build()
    }.build()

    val wakeLockReleasedEvent = BackgroundTaskInspectorProtocol.BackgroundTaskEvent.newBuilder().apply {
      taskId = 1
      stacktrace = "RELEASED"
      wakeLockReleased = BackgroundTaskInspectorProtocol.WakeLockReleased.newBuilder().apply {
        addFlags(BackgroundTaskInspectorProtocol.WakeLockReleased.ReleaseFlag.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        isHeld = false
      }.build()
    }.build()

    val entry = WakeLockEntry("1")

    entry.consumeAndAssert(wakeLockAcquiredEvent) {
      assertThat(status).isEqualTo("ACQUIRED")
      assertThat(callstacks).containsExactly("ACQUIRED")
    }

    entry.consumeAndAssert(wakeLockReleasedEvent) {
      assertThat(status).isEqualTo("RELEASED")
      assertThat(callstacks).containsExactly("ACQUIRED", "RELEASED")
    }
  }
}