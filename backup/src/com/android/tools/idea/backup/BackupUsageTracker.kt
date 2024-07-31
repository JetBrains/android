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
import com.android.backup.ErrorCode
import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.BACKUP_USAGE
import com.google.wireless.android.sdk.stats.BackupUsageEvent
import com.google.wireless.android.sdk.stats.BackupUsageEvent.BackupEvent
import com.google.wireless.android.sdk.stats.BackupUsageEvent.BackupEvent.Type
import com.google.wireless.android.sdk.stats.BackupUsageEvent.RestoreEvent

/** A convenience container for usage tracing methods. */
internal object BackupUsageTracker {
  fun logBackup(type: Type, source: BackupManager.Source, result: BackupResult) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(BACKUP_USAGE)
        .setBackupUsageEvent(
          BackupUsageEvent.newBuilder()
            .setBackup(
              BackupEvent.newBuilder()
                .setType(type)
                .setSource(source.toProto())
                .setResult(result.toProto())
            )
        )
    )
  }

  fun logRestore(source: BackupManager.Source, result: BackupResult) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(BACKUP_USAGE)
        .setBackupUsageEvent(
          BackupUsageEvent.newBuilder()
            .setRestore(
              RestoreEvent.newBuilder().setSource(source.toProto()).setResult(result.toProto())
            )
        )
    )
  }
}

private fun BackupManager.Source.toProto() =
  when (this) {
    BackupManager.Source.DEVICE_EXPLORER -> BackupUsageEvent.Source.DEVICE_EXPLORER
    BackupManager.Source.RUN_MENU -> BackupUsageEvent.Source.RUN_MENU
    BackupManager.Source.PROJECT_VIEW -> BackupUsageEvent.Source.PROJECT_VIEW
  }

private fun BackupResult.toProto() =
  when (this) {
    is BackupResult.Error -> this.errorCode.toProto()
    BackupResult.Success -> BackupUsageEvent.Result.SUCCESS
  }

private fun ErrorCode.toProto() =
  when (this) {
    ErrorCode.CANNOT_ENABLE_BMGR -> BackupUsageEvent.Result.CANNOT_ENABLE_BMGR
    ErrorCode.TRANSPORT_NOT_SELECTED -> BackupUsageEvent.Result.TRANSPORT_NOT_SELECTED
    ErrorCode.TRANSPORT_INIT_FAILED -> BackupUsageEvent.Result.TRANSPORT_INIT_FAILED
    ErrorCode.GMSCORE_NOT_FOUND -> BackupUsageEvent.Result.GMSCORE_NOT_FOUND
    ErrorCode.GMSCORE_IS_TOO_OLD -> BackupUsageEvent.Result.GMSCORE_IS_TOO_OLD
    ErrorCode.BACKUP_FAILED -> BackupUsageEvent.Result.BACKUP_FAILED
    ErrorCode.RESTORE_FAILED -> BackupUsageEvent.Result.RESTORE_FAILED
    ErrorCode.INVALID_BACKUP_FILE -> BackupUsageEvent.Result.INVALID_BACKUP_FILE
    ErrorCode.UNEXPECTED_ERROR -> BackupUsageEvent.Result.UNEXPECTED_ERROR
  }
