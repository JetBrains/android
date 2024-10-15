/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.backup.BackupResult
import com.android.backup.BackupType
import com.android.backup.ErrorCode
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.backup.BackupManager.Source
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.BACKUP_USAGE
import com.google.wireless.android.sdk.stats.BackupUsageEvent
import com.google.wireless.android.sdk.stats.BackupUsageEvent.BackupEvent
import com.google.wireless.android.sdk.stats.BackupUsageEvent.RestoreEvent

/** A convenience container for usage tracing methods. */
internal object BackupUsageTracker {
  fun logBackup(type: BackupType, source: Source, result: BackupResult) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(BACKUP_USAGE)
        .setBackupUsageEvent(
          BackupUsageEvent.newBuilder()
            .setBackup(
              BackupEvent.newBuilder()
                .setTypeString(type.name)
                .setSourceString(source.name)
                .setResultString(result.getErrorCode())
            )
        )
    )
  }

  fun logRestore(source: Source, result: BackupResult) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(BACKUP_USAGE)
        .setBackupUsageEvent(
          BackupUsageEvent.newBuilder()
            .setRestore(
              RestoreEvent.newBuilder()
                .setSourceString(source.name)
                .setResultString(result.getErrorCode())
            )
        )
    )
  }
}

private fun BackupResult.getErrorCode() =
  when (this) {
    is BackupResult.Error -> this.errorCode.name
    BackupResult.Success -> ErrorCode.SUCCESS.name
  }
