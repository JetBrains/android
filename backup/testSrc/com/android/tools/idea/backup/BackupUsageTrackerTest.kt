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
package com.android.tools.idea.backup

import com.android.backup.BackupException
import com.android.backup.BackupResult.Error
import com.android.backup.BackupType.CLOUD
import com.android.backup.BmgrException
import com.android.backup.ErrorCode.BACKUP_FAILED
import com.android.backup.ErrorCode.BMGR_ERROR_BACKUP
import com.android.backup.ErrorCode.UNEXPECTED_ERROR
import com.android.commands.bmgr.outputparser.BmgrError
import com.android.tools.analytics.LoggedUsage
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.backup.BackupManager.Source.PROJECT_VIEW
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RuleChain
import org.junit.Rule
import org.junit.Test

class BackupUsageTrackerTest {
  private val usageTrackerRule = UsageTrackerRule()

  @get:Rule val rule = RuleChain(usageTrackerRule)

  @Test
  fun error_errorCode() {
    BackupUsageTracker.logBackup(CLOUD, PROJECT_VIEW, Error(BACKUP_FAILED, RuntimeException()))

    assertThat(usageTrackerRule.usages.map { it.backupResult() }).containsExactly("BACKUP_FAILED")
  }

  @Test
  fun error_bmgrError() {
    BackupUsageTracker.logBackup(
      CLOUD,
      PROJECT_VIEW,
      Error(
        BMGR_ERROR_BACKUP,
        BackupException(
          BMGR_ERROR_BACKUP,
          "error",
          BmgrException("command", "out", listOf(BmgrError("e1", "m1"), BmgrError("e2", "m2"))),
        ),
      ),
    )

    assertThat(usageTrackerRule.usages.map { it.backupResult() }).containsExactly("e1 e2")
  }

  @Test
  fun error_unexpectedTopLevel() {
    BackupUsageTracker.logBackup(CLOUD, PROJECT_VIEW, Error(UNEXPECTED_ERROR, RuntimeException()))

    assertThat(usageTrackerRule.usages.map { it.backupResult() })
      .containsExactly("RuntimeException")
  }

  @Test
  fun error_unexpectedWithCause() {
    BackupUsageTracker.logBackup(
      CLOUD,
      PROJECT_VIEW,
      Error(UNEXPECTED_ERROR, BackupException(UNEXPECTED_ERROR, "error", RuntimeException())),
    )

    assertThat(usageTrackerRule.usages.map { it.backupResult() })
      .containsExactly("RuntimeException")
  }

  @Test
  fun error_unexpectedWithoutCause() {
    BackupUsageTracker.logBackup(
      CLOUD,
      PROJECT_VIEW,
      Error(UNEXPECTED_ERROR, BackupException(UNEXPECTED_ERROR, "error")),
    )

    assertThat(usageTrackerRule.usages.map { it.backupResult() })
      .containsExactly("UNEXPECTED_ERROR")
  }
}

private fun LoggedUsage.backupResult() = studioEvent.backupUsageEvent.backup.resultString
