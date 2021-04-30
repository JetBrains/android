/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.stats

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.FileUsage
import com.google.wireless.android.sdk.stats.VfsRefresh
import com.intellij.internal.statistic.eventLog.EmptyEventLogFilesProvider
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.StatisticsEventLogger
import java.util.concurrent.CompletableFuture

object AndroidStudioEventLogger : StatisticsEventLogger {
  override fun cleanup() {}
  override fun getActiveLogFile(): Nothing? = null
  override fun getLogFilesProvider() = EmptyEventLogFilesProvider
  override fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<Void> {
    when (group.id) {
      "file.types.usage" -> logFileUsage(data)
      "vfs" -> logVfsEvent(eventId, data)
    }
    return CompletableFuture.completedFuture(null)
  }

  override fun rollOver() {}

  private fun logFileUsage(data: Map<String, Any>) {
    UsageTracker.log(AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.FILE_USAGE
      fileUsage = FileUsage.newBuilder().apply {
        (data["file_path"] as? String)?.let { filePath = it }
        (data["file_type"] as? String)?.let { fileType = it }
        (data["plugin_type"] as? String)?.let { pluginType = it }
      }.build()
    })
  }

  private fun logVfsEvent(eventId: String, data: Map<String, Any>) {
    if (!eventId.equals("refreshed")) { // eventId as declared in com.intellij.openapi.vfs.newvfs.RefreshProgress
      return
    }

    (data["duration_ms"] as? Long)?.let { durationMs ->
      UsageTracker.log(AndroidStudioEvent.newBuilder()
                         .setKind(AndroidStudioEvent.EventKind.VFS_REFRESH)
                         .setVfsRefresh(VfsRefresh.newBuilder().setDurationMs(durationMs)))
    }
  }
}