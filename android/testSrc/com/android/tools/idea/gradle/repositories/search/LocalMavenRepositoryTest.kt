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

import com.android.ide.common.gradle.Version
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.util.PathUtil
import org.hamcrest.CoreMatchers.equalTo
import org.jetbrains.android.AndroidTestBase
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class LocalMavenRepositoryTest {

  private val testVersionSet = setOf(
    Version.parse("0.6.1"),
    Version.parse("1.0"),
    Version.parse("0.6"),
    Version.parse("0.9.1"),
    Version.parse("0.6.1-alpha"),
    Version.parse("1.0-alpha"),
    Version.parse("0.5")
  )

  private val LIB1 = FoundArtifact(repositoryName = "Test", groupId = "com.example.libs", name = "lib1", unsortedVersions = testVersionSet)
  private val LIB2 = FoundArtifact(repositoryName = "Test", groupId = "com.example.libs", name = "lib2", unsortedVersions = testVersionSet)
  private val JLIB3 = FoundArtifact(repositoryName = "Test", groupId = "com.example.jlib", name = "lib3", unsortedVersions = testVersionSet)
  private val JLIB4 = FoundArtifact(repositoryName = "Test", groupId = "com.example.jlib", name = "lib4",
                                    unsortedVersions = testVersionSet + Version.parse("1.1-alpha"))

  private lateinit var repositoryDir: File
  private lateinit var repository: ArtifactRepository

  @Before
  fun setUp() {
    repositoryDir = File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectPaths.PSD_SAMPLE_REPO))
    repository = LocalMavenRepository(repositoryDir.absoluteFile, "Test")
  }

  @Test
  fun searchByName() {
    assertThat(
      repository
        .search(SearchRequest(SearchQuery(null, "lib1"), 50, 0))
        .get(10, TimeUnit.SECONDS)
        .clearStats(),
      equalTo(SearchResult(artifacts = listOf(LIB1))))
  }

  @Test
  fun searchByGroupId() {
    assertThat(
      repository
        .search(SearchRequest(SearchQuery("com.example.jlib", ""), 50, 0))
        .get(10, TimeUnit.SECONDS)
        .clearStats(),
      equalTo(SearchResult(artifacts = listOf(JLIB3, JLIB4))))
  }

  @Test
  fun searchByNameWildcard() {
    assertThat(
      repository
        .search(SearchRequest(SearchQuery("", "lib*"), 50, 0))
        .get(10, TimeUnit.SECONDS)
        .clearStats(),
      equalTo(SearchResult(artifacts = listOf(JLIB3, JLIB4, LIB1, LIB2))))
  }

  @Test
  fun searchByGroupIdWildcard() {
    assertThat(
      repository
        .search(SearchRequest(SearchQuery("com.example.j*", null), 50, 0))
        .get(10, TimeUnit.SECONDS)
        .clearStats(),
      equalTo(SearchResult(artifacts = listOf(JLIB3, JLIB4))))
  }

  @Test
  fun searchByExactMatch() {
    assertThat(
      repository
        .search(SearchRequest(SearchQuery("com.example.libs", "lib2"), 50, 0))
        .get(10, TimeUnit.SECONDS)
        .clearStats(),
      equalTo(SearchResult(artifacts = listOf(LIB2))))
  }

  @Test
  fun searchByWildcard() {
    assertThat(
      repository
        .search(SearchRequest(SearchQuery("com.example.lib*", "lib*"), 50, 0))
        .get(10, TimeUnit.SECONDS)
        .clearStats(),
      equalTo(SearchResult(artifacts = listOf(LIB1, LIB2))))
  }
}

private fun SearchResult.clearStats() = copy(stats = SearchResultStats.EMPTY)