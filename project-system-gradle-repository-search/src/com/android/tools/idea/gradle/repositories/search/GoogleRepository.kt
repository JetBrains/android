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
package com.android.tools.idea.gradle.repositories.search

import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepositoryBase
import com.google.wireless.android.sdk.stats.PSDEvent.PSDRepositoryUsage.PSDRepository.PROJECT_STRUCTURE_DIALOG_REPOSITORY_GOOGLE
import com.intellij.util.text.nullize
import java.util.concurrent.CompletableFuture

object GoogleRepository : GoogleRepositoryBase(IdeGoogleMavenRepository)

open class GoogleRepositoryBase(val repository: IdeGoogleMavenRepositoryBase) : ArtifactRepository(PROJECT_STRUCTURE_DIALOG_REPOSITORY_GOOGLE) {
  override val name: String = "Google"
  override val isRemote: Boolean = true
  override fun doSearch(request: SearchRequest): SearchResult {
    fun String?.toFilterPredicate() =
      nullize(true)?.let { Regex(it.replace("*", ".*")) }?.let { { probe: String -> it.matches(probe) } } ?: { true }
    return when (request.query) {
      is GroupArtifactQuery -> {
        val groupFilter = request.query.groupId.toFilterPredicate()
        val artifactFilter = request.query.artifactName.toFilterPredicate()
        val groups = repository.getGroups().filter(groupFilter).sorted()
        SearchResult(
          getArtifacts(groups) { _, id -> artifactFilter(id) }
        )
      }

      is ModuleQuery -> {
        val moduleFilter = request.query.module.toFilterPredicate()
        val groups = repository.getGroups().sorted()
        SearchResult(
          getArtifacts(groups) { groupId, id -> moduleFilter("$groupId:$id") }
        )
      }
    }
  }

  private fun getArtifacts(groups: List<String>, artifactFilter: (String, String) -> Boolean): List<FoundArtifact> {
    val groupsToArtifacts = repository.getArtifactsForAll(groups)
    val futureArtifacts = groupsToArtifacts.values.toTypedArray()
    CompletableFuture.allOf(*futureArtifacts).get() // load all artifacts repo into index in parallel

    return groupsToArtifacts.map{ (groupId, futureArtifacts) ->
      futureArtifacts.get().filter { artifactFilter(groupId, it) }.sorted().map{ id ->
        val versions = repository.getVersions(groupId,id)
        FoundArtifact(name, groupId, id, versions)
      }
    }.flatten().toList()
  }

}
