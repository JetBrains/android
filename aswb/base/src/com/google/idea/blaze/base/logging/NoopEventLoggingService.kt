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
import com.intellij.openapi.project.Project

/** An [EventLoggingService] that does nothing, used in case there isn't one registered. */
class NoopEventLoggingService : EventLoggingService() {
  override fun log(project: Project, syncStats: SyncStats) = Unit
  override fun log(project: Project, querySyncStats: QuerySyncActionStats) = Unit
  override fun log(project: Project, querySyncAutoConversionStats: QuerySyncAutoConversionStats) = Unit
  override fun log(aiEvent: AiEvent) = Unit
  override fun logCommand(caller: Any?, command: Command) = Unit
  override fun log(project: Project, highlightStats: HighlightStats) = Unit
}