/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.logging

import com.google.idea.blaze.base.logging.utils.HighlightStats
import com.google.idea.blaze.base.logging.utils.SyncStats
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStats
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncAutoConversionStats
import com.google.idea.blaze.ext.Logentry.AiEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.time.Duration

/**
 * Forwards event logs to any [EventLogger]s available. This indirection exists so that
 * [EventLogger] can have a minimal API surface.
 */
abstract class EventLoggingService {

  abstract fun log(project: Project, syncStats: SyncStats)

  abstract fun log(project: Project, querySyncStats: QuerySyncActionStats)

  abstract fun log(project: Project, querySyncAutoConversionStats: QuerySyncAutoConversionStats)

  abstract fun log(aiEvent: AiEvent)

  abstract fun logCommand(caller: Any?, command: Command)

  @JvmOverloads
  open fun logEvent(
      caller: Any?,
      eventType: String,
      keyValues: Map<String, String> = emptyMap(),
      durationInNanos: Long? = null
  ) {}

  @JvmOverloads
  open fun logEvent(
      project: Project,
      caller: Any?,
      eventType: String,
      keyValues: Map<String, String> = emptyMap(),
      durationInNanos: Long? = null
  ) {}

  abstract fun logHighlightStats(project: Project, highlightStats: HighlightStats)

  /** Information about an external command that was launched from the IDE. */
  data class Command(
      val executable: String,
      val arguments: List<String>,
      val subcommandName: String?,
      val workingDirectory: String?,
      val exitCode: Int,
      val duration: Duration
  )

  companion object {
    @JvmStatic
    fun getInstance(): EventLoggingService {
      // This method is intentionally written with an explicit if/return
      // statement. Using a more idiomatic single-expression function or
      // an elvis operator was found to cause a subtle Kotlin compiler bug
      // where the return type was erased to `Any` for Java callers.
      val service = ApplicationManager.getApplication().getService(EventLoggingService::class.java)
      if (service != null) {
        return service
      }
      return NoopEventLoggingService()
    }
  }
}
