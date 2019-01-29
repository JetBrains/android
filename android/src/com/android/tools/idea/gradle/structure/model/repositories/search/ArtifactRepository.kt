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
package com.android.tools.idea.gradle.structure.model.repositories.search

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import org.jetbrains.ide.PooledThreadExecutor
import java.util.concurrent.Callable

abstract class ArtifactRepository : ArtifactRepositorySearchService {
  private val executor = MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE)

  abstract val name: String
  abstract val isRemote: Boolean
  @Throws(Exception::class) protected abstract fun doSearch(request: SearchRequest): SearchResult

  override fun search(request: SearchRequest): ListenableFuture<SearchResult> =
    executor.submit(Callable {
      try {
        doSearch(request)
      }
      catch (e: Exception) {
        SearchResult(e)
      }
    })
}
