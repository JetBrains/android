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

import java.util.concurrent.Future

data class SearchResult(val artifacts: List<FoundArtifact>, val errors: List<Exception>) {
  constructor(artifacts: List<FoundArtifact>): this(artifacts, listOf())
  constructor (e: Exception) : this(listOf(), listOf(e))

  val artifactCoordinates: List<String> get() = artifacts.flatMap { it.coordinates }
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
  flatMap { it.errors }
)

fun Future<SearchResult>.getResultSafely() =
  try {
    get()!!
  }
  catch (e: Exception) {
    SearchResult(e)
  }