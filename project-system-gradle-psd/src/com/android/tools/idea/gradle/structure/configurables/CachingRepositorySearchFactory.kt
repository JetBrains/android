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
package com.android.tools.idea.gradle.repositories.search

import com.android.annotations.concurrency.GuardedBy
import com.google.common.util.concurrent.ListenableFuture

class CachingRepositorySearchFactory : RepositorySearchFactory {
  private val lock = Any()

  @GuardedBy("lock")
  private val artifactRepositorySearchServices = mutableMapOf<ArtifactRepositorySearchService, ArtifactRepositorySearchService>()

  override fun create(repositories: Collection<ArtifactRepositorySearchService>): ArtifactRepositorySearchService =
    ArtifactRepositorySearch(
      synchronized(lock) {
        repositories
          .map { artifactRepositorySearchServices.getOrPut(it) { CachingArtifactRepositorySearch(it) } }
      }
    )

  private class CachingArtifactRepositorySearch(
    private val artifactRepositorySearch: ArtifactRepositorySearchService
  ) : ArtifactRepositorySearchService {
    private val lock = Any()

    @GuardedBy("lock")
    private val requestCache = mutableMapOf<SearchRequest, ListenableFuture<SearchResult>>()

    override fun search(request: SearchRequest): ListenableFuture<SearchResult> =
      synchronized(lock) {
        requestCache[request]?.takeUnless { it.isCancelled }
        ?: artifactRepositorySearch.search(request).also { requestCache[request] = it }
      }
  }
}
