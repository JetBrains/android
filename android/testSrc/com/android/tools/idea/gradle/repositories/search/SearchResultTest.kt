/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.PSDEvent.PSDRepositoryUsage.PSDRepository
import org.junit.Test
import java.time.Duration

class SearchResultTest {

  @Test
  fun getArtifactCoordinates() {
    // TODO(xof): this test might pass after Version conversion, but I don't know if it's doing what is intended: I think a FoundArtifact
    //  is a concrete version from a repository, rather than a version specifier given by a user.  On the other hand, "1.0.2.+" is a legal
    //  version (if hard to specify, because the external syntax would be interpreted by Gradle as a prefix match) so it's possible that
    //  a repository could return it.
    val artifactA = FoundArtifact("test", "group", "artifactA", Version.parse("1.0.2.+"))
    val artifactB = FoundArtifact("test", "group", "artifactB", Version.parse("2.0.2.+"))
    val artifactC = FoundArtifact("test", "group", "artifactB", Version.parse("3.0.2.+"))
    val searchResult = SearchResult(listOf(artifactA, artifactB, artifactC))
    assertThat(searchResult.artifactCoordinates)
      .containsExactly("group:artifactA:1.0.2.+", "group:artifactB:2.0.2.+", "group:artifactB:3.0.2.+")
  }

  @Test
  fun combineSearchResults() {
    val error1 = RuntimeException("e1")
    val error2 = RuntimeException("e2")
    val artifactA = FoundArtifact("repo1", "group", "artifactA", Version.parse("1.0.2.+"))
    val searchResult1 = SearchResult(listOf(artifactA))
    val artifactB1 = FoundArtifact("repo2", "group", "artifactB", Version.parse("2.0.2.+"))
    val artifactB2 = FoundArtifact("repo2", "group", "artifactB", Version.parse("3.0.2.+"))
    val searchResult2 = SearchResult(listOf(artifactB1, artifactB2), listOf(error1))
    val artifactB3 = FoundArtifact("repo3", "group", "artifactB", Version.parse("4.1"))
    val searchResult3 = SearchResult(listOf(artifactB3), listOf(error2))

    val combined = listOf(searchResult1, searchResult2, searchResult3).combine()

    assertThat(combined).isEqualTo(
      SearchResult(
        listOf(
          FoundArtifact("repo1", "group", "artifactA", Version.parse("1.0.2.+")),
          FoundArtifact(setOf("repo2", "repo3"),
                        "group",
                        "artifactB",
                        setOf(Version.parse("2.0.2.+"), Version.parse("3.0.2.+"), Version.parse("4.1")))
        ),
        listOf(
          error1, error2
        )))
  }

  @Test
  fun combineSearchResultStats() {
    fun Int.toRepoStats() = SearchResultRepoStats(Duration.ofMillis(this.toLong()))
    val searchResult1 =
      SearchResultStats(mapOf(
        PSDRepository.PROJECT_STRUCTURE_DIALOG_REPOSITORY_GOOGLE to 100.toRepoStats(),
        PSDRepository.PROJECT_STRUCTURE_DIALOG_REPOSITORY_JCENTER to 300.toRepoStats()
      ))
    val searchResult2 =
      SearchResultStats(mapOf(
        PSDRepository.PROJECT_STRUCTURE_DIALOG_REPOSITORY_GOOGLE to 100.toRepoStats(),
        PSDRepository.PROJECT_STRUCTURE_DIALOG_REPOSITORY_LOCAL to 10.toRepoStats()
      ))

    val combined = listOf(searchResult1, searchResult2).combine()

    assertThat(combined).isEqualTo(
      SearchResultStats(
        mapOf(
          PSDRepository.PROJECT_STRUCTURE_DIALOG_REPOSITORY_GOOGLE to 200.toRepoStats(),
          PSDRepository.PROJECT_STRUCTURE_DIALOG_REPOSITORY_JCENTER to 300.toRepoStats(),
          PSDRepository.PROJECT_STRUCTURE_DIALOG_REPOSITORY_LOCAL to 10.toRepoStats()
        )))
  }
}
