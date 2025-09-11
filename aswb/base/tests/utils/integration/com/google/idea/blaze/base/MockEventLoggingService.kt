/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base

import com.google.common.collect.ImmutableList
import com.google.idea.blaze.base.logging.EventLoggingService
import com.google.idea.blaze.base.logging.utils.HighlightStats
import com.google.idea.blaze.base.logging.utils.SyncStats
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStats
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncAutoConversionStats
import com.google.idea.blaze.ext.Logentry.AiEvent
import com.google.idea.testing.ServiceHelper
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

/**
 * Provides a [EventLoggingService] for integration tests.
 */
class MockEventLoggingService(parentDisposable: Disposable) : EventLoggingService() {
  private val syncStats: MutableList<SyncStats> = mutableListOf()
  private val querySyncStats: MutableList<QuerySyncActionStats> = mutableListOf()
  private val querySyncAutoConversionStats: MutableList<QuerySyncAutoConversionStats> =
      mutableListOf()
  private val aiEvents: MutableList<AiEvent> = mutableListOf()

  init {
    ServiceHelper.registerApplicationService(EventLoggingService::class.java, this, parentDisposable)
  }

  fun getSyncStats(): ImmutableList<SyncStats> = ImmutableList.copyOf(syncStats)

  fun getQuerySyncStats(): ImmutableList<QuerySyncActionStats> = ImmutableList.copyOf(querySyncStats)

  fun getQuerySyncAutoConversionStats(): ImmutableList<QuerySyncAutoConversionStats> =
      ImmutableList.copyOf(querySyncAutoConversionStats)

  fun aiEvents(): ImmutableList<AiEvent> = ImmutableList.copyOf(aiEvents)

  override fun log(project: Project, stats: SyncStats) {
    syncStats.add(stats)
  }

  override fun log(project: Project, stats: QuerySyncActionStats) {
    querySyncStats.add(stats)
  }

  override fun log(project: Project, stats: QuerySyncAutoConversionStats) {
    querySyncAutoConversionStats.add(stats)
  }

  override fun log(aiEvent: AiEvent) {
    aiEvents.add(aiEvent)
  }

  override fun logCommand(caller: Any?, command: Command) = Unit

  override fun log(project: Project, highlightStats: HighlightStats) = Unit
}
