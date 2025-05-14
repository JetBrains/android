/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.settingssync

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.BACKUP_AND_SYNC_EVENT
import com.google.wireless.android.sdk.stats.BackupAndSyncEvent
import com.intellij.settingsSync.core.SettingsSyncEvents
import com.intellij.settingsSync.core.SettingsSyncLocalSettings
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test

class SyncEventsMetricsTest {
  @get:Rule val projectRule = ProjectRule()

  private val tracker =
    TestUsageTracker(VirtualTimeScheduler()).also { UsageTracker.setWriterForTest(it) }

  @After
  fun tearDown() {
    tracker.close()
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun `log provider`() {
    // Prepare
    SettingsSyncLocalSettings.getInstance().userId = "test-user"
    SettingsSyncLocalSettings.getInstance().providerCode = PROVIDER_CODE_GOOGLE

    runBlocking { SyncEventsMetrics.Initializer().execute(projectRule.project) }

    // Action
    SettingsSyncEvents.getInstance().fireEnabledStateChanged(true)

    // Assert
    val event = tracker.usages.map { it.studioEvent }.single { it.kind == BACKUP_AND_SYNC_EVENT }
    assertThat(event.backupAndSyncEvent.providerInUse).isEqualTo(BackupAndSyncEvent.Provider.GOOGLE)
  }
}
