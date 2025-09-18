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
import com.google.idea.blaze.base.logging.AiEvent
import com.google.idea.blaze.base.logging.Command
import com.google.idea.blaze.base.logging.EventLoggingService
import com.google.idea.blaze.base.logging.GenericEvent
import com.google.idea.blaze.base.logging.HighlightStats
import com.google.idea.blaze.base.logging.LoggedEvent
import com.google.idea.blaze.base.logging.SyncStats
import com.google.idea.blaze.base.logging.QuerySyncActionStats
import com.google.idea.blaze.base.logging.QuerySyncAutoConversionStats
import com.google.idea.testing.ServiceHelper
import com.intellij.openapi.Disposable

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

  override fun log(loggedEvent: LoggedEvent) {
    when(loggedEvent) {
      is SyncStats -> syncStats.add(loggedEvent)
      is QuerySyncActionStats -> querySyncStats.add(loggedEvent)
      is QuerySyncAutoConversionStats -> querySyncAutoConversionStats.add(loggedEvent)
      is AiEvent -> aiEvents.add(loggedEvent)
      is HighlightStats -> Unit
      is Command -> Unit
      is GenericEvent -> Unit
    }
  }
}
