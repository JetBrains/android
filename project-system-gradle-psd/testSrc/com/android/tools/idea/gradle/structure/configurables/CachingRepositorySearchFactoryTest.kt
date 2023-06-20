/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables

import com.android.ide.common.gradle.Version
import com.android.tools.idea.gradle.repositories.search.CachingRepositorySearchFactory
import com.android.tools.idea.gradle.repositories.search.ArtifactRepositorySearchService
import com.android.tools.idea.gradle.repositories.search.FoundArtifact
import com.android.tools.idea.gradle.repositories.search.SearchQuery
import com.android.tools.idea.gradle.repositories.search.SearchRequest
import com.android.tools.idea.gradle.repositories.search.SearchResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test


class CachingRepositorySearchFactoryTest {

  private class TestRepository(val id: String, val onSearch: () -> Unit) : ArtifactRepositorySearchService {
    override fun search(request: SearchRequest): ListenableFuture<SearchResult> {
      onSearch()
      return Futures.immediateFuture(SearchResult(listOf(FoundArtifact(id, "group", "name", Version.parse("1.0")))))
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false
      other as TestRepository
      return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
  }

  @Test
  fun testCreate() {
    val factory = CachingRepositorySearchFactory()

    var a1Searched = 0
    var a2Searched = 0
    var b2Searched = 0

    val repoA1 = TestRepository("A") { a1Searched++ }
    val repoA2 = TestRepository("A") { a2Searched++ }
    val repoB2 = TestRepository("B") { b2Searched++ }

    val module1Repos = factory.create(listOf(repoA1))
    val module2Repos = factory.create(listOf(repoA2, repoB2))

    module1Repos.search(SearchRequest(SearchQuery("group", "name"), 10, 0))
    module2Repos.search(SearchRequest(SearchQuery("group", "name"), 10, 0))

    assertThat(a1Searched, equalTo(1))
    assertThat(a2Searched, equalTo(0))
    assertThat(b2Searched, equalTo(1))
  }
}