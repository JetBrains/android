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

import com.android.ide.common.gradle.Version
import com.google.common.annotations.VisibleForTesting
import com.google.gson.JsonParser
import com.google.wireless.android.sdk.stats.PSDEvent.PSDRepositoryUsage.PSDRepository.PROJECT_STRUCTURE_DIALOG_REPOSITORY_JCENTER
import com.intellij.util.io.HttpRequests
import java.io.Reader

object JCenterRepository : ArtifactRepository(PROJECT_STRUCTURE_DIALOG_REPOSITORY_JCENTER) {
  override val name: String = "JCenter"
  override val isRemote: Boolean = true

  @Throws(Exception::class)
  override fun doSearch(request: SearchRequest): SearchResult =
    HttpRequests.request(createRequestUrl(request)).accept("application/json").connect { parse(it.reader) }

  @VisibleForTesting
  fun parse(response: Reader): SearchResult {
    /*
      Sample response:
      [
        {
          "name": "com.atlassian.guava:guava",
          "repo": "jcenter",
          "owner": "bintray",
          "desc": null,
          "system_ids": [
            "com.atlassian.guava:guava"
          ],
          "versions": [
            "15.0"
          ],
          "latest_version": "15.0"
        },
        {
          "name": "com.atlassian.bundles:guava",
          "repo": "jcenter",
          "owner": "bintray",
          "desc": null,
          "system_ids": [
            "com.atlassian.bundles:guava"
          ],
          "versions": [
            "8.1",
            "8.0",
            "1.0-actually-8.1"
          ],
          "latest_version": "8.1"
        },
        {
          "name": "io.janusproject.guava:guava",
          "repo": "jcenter",
          "owner": "bintray",
          "desc": null,
          "system_ids": [
            "io.janusproject.guava:guava"
          ],
          "versions": [
            "19.0.0",
            "17.0.2",
            "17.0"
          ],
          "latest_version": "19.0.0"
        },
        {
          "name": "com.google.guava:guava",
          "repo": "jcenter",
          "owner": "bintray",
          "desc": "Guava is a suite of core and expanded libraries that include\n    utility classes, google's collections, ...",
          "system_ids": [
            "com.google.guava:guava"
          ],
          "versions": [
            "19.0",
            "19.0-rc3",
            "19.0-rc2",
            "19.0-rc1",
            "18.0",
            "18.0-rc2",
            "18.0-rc1",
            "11.0.2-atlassian-02",
            "17.0",
            "17.0-rc2",
            "17.0-rc1",
            "16.0.1",
            "16.0",
            "16.0-rc1",
            "15.0",
            "15.0-rc1",
            "14.0.1",
            "14.0",
            "14.0-rc3",
            "14.0-rc2",
            "14.0-rc1",
            "13.0.1",
            "13.0",
            "13.0-final",
            "13.0-rc2",
            "13.0-rc1",
            "12.0.1",
            "12.0",
            "12.0-rc2",
            "12.0-rc1",
            "11.0.2-atlassian-01",
            "11.0.2",
            "11.0.1",
            "11.0",
            "11.0-rc1",
            "10.0.1",
            "10.0",
            "10.0-rc3",
            "10.0-rc2",
            "10.0-rc1",
            "r09",
            "r08",
            "r07",
            "r06",
            "r05",
            "r03"
          ],
          "latest_version": "19.0"
        }
      ]
     */

    val parser = JsonParser()
    val array = parser.parse(response).asJsonArray
    val errors = mutableListOf<Exception>()

    val artifacts = array.flatMap { result ->
      val root = result.asJsonObject
      try {
        val versions = root.getAsJsonArray("versions")
        val systemIds = root.getAsJsonArray("system_ids")
        val availableVersions = versions.mapNotNull { Version.parse(it.asString) }.toSet()

        systemIds.mapNotNull { name ->
          name
            .asString
            .split(':')
            .takeIf { it.size == 2 }
            ?.let { FoundArtifact(JCenterRepository.name, it[0], it[1], availableVersions) }
        }
      }
      catch (ex: Exception) {
        errors.add(ex)
        listOf<FoundArtifact>()
      }
    }

    return SearchResult(artifacts, errors, SearchResultStats.EMPTY)
  }

  @VisibleForTesting
  fun createRequestUrl(request: SearchRequest): String = buildString {
    append("https://api.bintray.com/search/packages/maven?")
    val groupId = request.query.groupId
    if (!groupId.isNullOrEmpty()) {
      append("g=")
      append(groupId)
      append("&")
    }
    val artifactName = request.query.artifactName
    if (!artifactName.isNullOrEmpty()) {
      append("a=")
      append(artifactName)
      append("&")
    }
    append("subject=bintray&repo=jcenter")
  }
}
