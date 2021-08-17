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
package com.android.tools.idea.gradle.repositories.search

import com.google.wireless.android.sdk.stats.PSDEvent
import java.time.Duration
import java.util.concurrent.Future

data class SearchResult(
  val artifacts: List<FoundArtifact>,
  val errors: List<Exception>,
  val stats: SearchResultStats = SearchResultStats.EMPTY
) {
  constructor(artifacts: List<FoundArtifact>) : this(artifacts, listOf(), SearchResultStats.EMPTY)
  constructor (e: Exception) : this(listOf(), listOf(e), SearchResultStats.EMPTY)

  val artifactCoordinates: List<String> get() = artifacts.flatMap { it.coordinates }
}

data class SearchResultRepoStats(val duration: Duration) {
  fun combineWith(other: SearchResultRepoStats) = SearchResultRepoStats(this.duration + other.duration)

  companion object {
    val EMPTY = SearchResultRepoStats(Duration.ZERO)
  }
}

data class SearchResultStats(val stats: Map<PSDEvent.PSDRepositoryUsage.PSDRepository, SearchResultRepoStats>) {
  companion object {
    fun duration(repo: PSDEvent.PSDRepositoryUsage.PSDRepository, duration: Duration) =
      SearchResultStats(mapOf(repo to SearchResultRepoStats(duration)))
    val EMPTY = SearchResultStats(mapOf())
  }
}

fun Collection<SearchResult>.combine() = SearchResult(
  flatMap { it.artifacts }
    .groupBy { it.groupId to it.name }
    .map {(key, artifacts) ->
      val (groupId, name) = key
      FoundArtifact(
        artifacts.flatMap { it.repositoryNames }.toSet(),
        groupId,
        name,
        artifacts.flatMap { it.unsortedVersions }.toSet()
      )
    },
  flatMap { it.errors },
  map { it.stats }.combine()
)

fun Collection<SearchResultStats>.combine(): SearchResultStats =
  SearchResultStats(
    this
      .flatMap { it.stats.entries }
      .groupBy({ it.key }, { it.value })
      .mapValues { (_, v) ->
        v.fold(SearchResultRepoStats.EMPTY) { acc, it -> acc.combineWith(it) }
      })


fun Future<SearchResult>.getResultSafely(): SearchResult? =
  takeUnless { isCancelled }
    .let {
      try {
        get()!!
      }
      catch (e: Exception) {
        SearchResult(e)
      }
    }
