/*
 * Copyright 2024 The Android Open Source Project
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
package com.android.tools.idea.backup

import com.android.backup.BackupType
import com.android.backup.BackupType.CLOUD
import com.android.backup.BackupType.DEVICE_TO_DEVICE
import com.google.common.truth.Truth.assertThat

import com.android.tools.idea.backup.BackupManager
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for [BackupManager]
 */
@RunWith(JUnit4::class)
class BackupManagerTest {
  /**
   * Ordinals ore used to track in studio_stats so they must not change.
   *
   * Add new values to the test as needed.
   */
  @Test
  fun source_ordinals() {
    assertThat(BackupManager.Source.DEVICE_EXPLORER.ordinal).isEqualTo(0)
    assertThat(BackupManager.Source.PROJECT_VIEW.ordinal).isEqualTo(1)
    assertThat(BackupManager.Source.RUN_CONFIG.ordinal).isEqualTo(2)
    assertThat(BackupManager.Source.BACKUP_APP_ACTION.ordinal).isEqualTo(3)
    assertThat(BackupManager.Source.BACKUP_FOREGROUND_APP_ACTION.ordinal).isEqualTo(4)
    assertThat(BackupManager.Source.entries).hasSize(5)
  }
}
