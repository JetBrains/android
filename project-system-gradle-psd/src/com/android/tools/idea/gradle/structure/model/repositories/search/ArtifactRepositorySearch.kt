/*
 * Copyright (C) 2016 The Android Open Source Project
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
@file:Suppress("UnstableApiUsage")

package com.android.tools.idea.gradle.repositories.search

import com.android.tools.analytics.UsageTracker
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.ide.PooledThreadExecutor
import java.util.concurrent.Callable

class ArtifactRepositorySearch(private val repositories: Collection<ArtifactRepositorySearchService>) : ArtifactRepositorySearchService {

  override fun search(request: SearchRequest): ListenableFuture<SearchResult> {
    val futures = repositories.map { it.search(request) }
    return Futures
      .whenAllComplete(futures)
      .call(
        Callable { futures.mapNotNull { it.getResultSafely() }.combine().also { logSearchStats(it.stats) } },
        PooledThreadExecutor.INSTANCE)
  }
}

private fun logSearchStats(stats: SearchResultStats) {
  ApplicationManager.getApplication().executeOnPooledThread {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_REPOSITORIES_SEARCH)
        .setPsdEvent(
          PSDEvent.newBuilder()
            .addAllRepositoriesSearched(
              stats.stats.map { (repository, stats) ->
                PSDEvent.PSDRepositoryUsage.newBuilder()
                  .setRepository(repository)
                  .setDurationMs(stats.duration.toMillis())
                  .build()
              }
            )
        )
    )
  }
}
