/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.project

import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SyncTimestampTest {
  // We can't use a light test fixture since we need the project component to reset.
  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  @After
  fun tearDown() {
    Clock.reset()
  }

  private fun sync(result: SyncResult, time: Long) {
    Clock.setTime(time)
    projectRule.project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(result)
  }

  @Test
  fun getLastSyncTimestamp_negativeIfNeverSynced() {
    assertThat(projectRule.project.getLastSyncTimestamp()).isLessThan(0L)
  }

  @Test
  fun getLastSyncTimestamp_matchesClock() {
    sync(SyncResult.SUCCESS, 0L)
    assertThat(projectRule.project.getLastSyncTimestamp()).isEqualTo(0L)
    sync(SyncResult.FAILURE, 1L)
    assertThat(projectRule.project.getLastSyncTimestamp()).isEqualTo(1L)
  }

  @Test
  fun getLastSyncTimestamp_ignoresCanceledSync() {
    sync(SyncResult.CANCELLED, 0L)
    assertThat(projectRule.project.getLastSyncTimestamp()).isLessThan(0L)
    sync(SyncResult.SUCCESS, 1L)
    sync(SyncResult.CANCELLED, 2L)
    assertThat(projectRule.project.getLastSyncTimestamp()).isEqualTo(1L)
  }
}