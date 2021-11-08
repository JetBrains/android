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
package com.android.tools.idea.adblib.ddmlibcompatibility

import com.android.adblib.SyncProgress
import com.android.ddmlib.SyncService

/**
 * Implementation of `adblib` [SyncProgress] that forwards to a `ddmlib` [SyncService.ISyncProgressMonitor] implementation
 */
class SyncProgressToISyncProgressMonitor(private val monitor: SyncService.ISyncProgressMonitor,
                                         private val totalWork: Long = 0) : SyncProgress {
  private var previousByteCount = 0L

  override suspend fun transferStarted(remotePath: String) {
    if (monitor.isCanceled) {
      cancelCoroutine("Sync transfer cancelled")
    }
    monitor.start(totalWork.toInt())
  }

  override suspend fun transferProgress(remotePath: String, totalBytesSoFar: Long) {
    if (monitor.isCanceled) {
      cancelCoroutine("Sync transfer cancelled")
    }
    monitor.advance((totalBytesSoFar - previousByteCount).toInt())
    previousByteCount = totalBytesSoFar
  }

  override suspend fun transferDone(remotePath: String, totalBytes: Long) {
    if (monitor.isCanceled) {
      cancelCoroutine("Sync transfer cancelled")
    }
    monitor.stop()
  }
}
