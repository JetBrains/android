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
package com.android.tools.idea.gradle.structure.model.repositories.search

import com.android.ide.common.repository.GoogleMavenRepository
import com.android.tools.idea.templates.IdeGoogleMavenRepository
import com.intellij.util.text.nullize

object GoogleRepository : GoogleRepositoryBase(IdeGoogleMavenRepository)

open class GoogleRepositoryBase(val repository: GoogleMavenRepository) : ArtifactRepository() {
  override val name: String = "Google"
  override val isRemote: Boolean = true

  override fun doSearch(request: SearchRequest): SearchResult {
    fun String?.toFilterPredicate() = this?.let { Regex(replace("*", ".*")) }?.let { { probe: String -> it.matches(probe) } } ?: { true }
    val groupFilter = request.groupId.toFilterPredicate()
    val artifactFilter = request.artifactName.nullize(nullizeSpaces = true).toFilterPredicate()

    return SearchResult(
      repository
        .getGroups()
        .filter(groupFilter)
        .sorted()
        .flatMap { groupId ->
          repository
            .getArtifacts(groupId)
            .filter(artifactFilter)
            .sorted()
            .map { artifactId ->
              val versions = repository.getVersions(groupId, artifactId)
              FoundArtifact(name, groupId, artifactId, versions)
            }
            .toList()
        }.toList())
  }
}
