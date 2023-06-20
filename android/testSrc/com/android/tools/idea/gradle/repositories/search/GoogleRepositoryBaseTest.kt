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
import com.android.ide.common.repository.StubGoogleMavenRepository
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class GoogleRepositoryBaseTest {

  private val builtInData = mapOf(
    "master-index.xml" to """
                <?xml version='1.0' encoding='UTF-8'?>
                <metadata>
                  <com.android.support.constraint/>
                  <com.android.databinding/>
                  <com.android.support/>
                  <com.android.support.test/>
                  <com.android.support.test.janktesthelper/>
                  <com.android.support.test.uiautomator/>
                  <com.android.support.test.espresso/>
                  <android.arch.persistence.room/>
                  <android.arch.lifecycle/>
                  <android.arch.core/>
                  <com.google.android.instantapps/>
                  <com.google.android.instantapps.thirdpartycompat/>
                  <com.android.java.tools.build/>
                  <com.android.tools/>
                  <com.android.tools.layoutlib/>
                  <com.android.tools.ddms/>
                  <com.android.tools.external.com-intellij/>
                  <com.android.tools.build/>
                  <com.android.tools.analytics-library/>
                  <com.android.tools.internal.build.test/>
                  <com.android.tools.lint/>
                </metadata>
            """.trimIndent(),
    "com/android/support/group-index.xml" to """
                <?xml version='1.0' encoding='UTF-8'?>
                <com.android.support>
                  <support-compat versions="25.3.1,26.0.0-beta1"/>
                  <leanback-v17 versions="25.3.1,26.0.0-beta1"/>
                  <recommendation versions="25.3.1,26.0.0-beta1"/>
                  <support-vector-drawable versions="25.3.1,26.0.0-beta1"/>
                  <recyclerview-v7 versions="25.3.1,26.0.0-beta1"/>
                </com.android.support>
            """.trimIndent(),
    "android/arch/lifecycle/group-index.xml" to """
              <?xml version='1.0' encoding='UTF-8'?>
              <android.arch.lifecycle>
                <compiler versions="1.0.0-alpha1,1.0.0-beta1,1.0.0,1.1.0,1.1.1"/>
                <runtime versions="1.0.0-alpha1,1.0.0-beta1,1.0.0,1.1.0,1.1.1"/>
                <extensions versions="1.0.0-alpha1,1.0.0-beta1,1.0.0,1.1.0,1.1.1"/>
                <reactivestreams versions="1.0.0-alpha1,1.0.0-beta1,1.0.0,1.1.0,1.1.1"/>
                <common versions="1.0.0-alpha1,1.0.0-beta1,1.0.0,1.1.0,1.1.1"/>
                <common-java8 versions="1.0.0-alpha1,1.0.0-beta1,1.0.0,1.1.0,1.1.1"/>
                <viewmodel versions="1.0.0-alpha1,1.0.0-beta1,1.0.0,1.1.0,1.1.1"/>
                <livedata-core versions="1.0.0-alpha1,1.0.0-beta1,1.0.0,1.1.0,1.1.1"/>
                <livedata versions="1.0.0-alpha1,1.0.0-beta1,1.0.0,1.1.0,1.1.1"/>
              </android.arch.lifecycle>
      """.trimIndent()
  )

  private val testSupportVersionSet = setOf(
    Version.parse("25.3.1"),
    Version.parse("26.0.0-beta1")
  )

  private val testArchVersionSet = setOf(
    Version.parse("1.0.0-alpha1"),
    Version.parse("1.0.0-beta1"),
    Version.parse("1.0.0"),
    Version.parse("1.1.0"),
    Version.parse("1.1.1")
  )

  private val SUPPORT_COMPAT = FoundArtifact(
    repositoryName = "Google", groupId = "com.android.support", name = "support-compat", unsortedVersions = testSupportVersionSet)

  private val LEANBACK = FoundArtifact(
    repositoryName = "Google", groupId = "com.android.support", name = "leanback-v17", unsortedVersions = testSupportVersionSet)

  private val RECOMMENDATION = FoundArtifact(
    repositoryName = "Google", groupId = "com.android.support", name = "recommendation", unsortedVersions = testSupportVersionSet)

  private val SUPPORT_VECTOR_DRAWABLE = FoundArtifact(
    repositoryName = "Google", groupId = "com.android.support", name = "support-vector-drawable", unsortedVersions = testSupportVersionSet)

  private val RECYCLER_VIEW_V7 = FoundArtifact(
    repositoryName = "Google", groupId = "com.android.support", name = "recyclerview-v7", unsortedVersions = testSupportVersionSet)

  private val COMMON = FoundArtifact(
    repositoryName = "Google", groupId = "android.arch.lifecycle", name = "common", unsortedVersions = testArchVersionSet)

  private val COMMON_JAVA8 = FoundArtifact(
    repositoryName = "Google", groupId = "android.arch.lifecycle", name = "common-java8", unsortedVersions = testArchVersionSet)

  private lateinit var repository: GoogleRepositoryBase

  @Before
  fun setUp() {
    repository = GoogleRepositoryBase(StubGoogleMavenRepository(builtInData))
  }

  @Test
  fun searchByName() {
    Assert.assertThat(
      repository
        .search(SearchRequest(SearchQuery(null, "common-java8"), 50, 0))
        .get(10, TimeUnit.SECONDS)
        .clearStats(),
      CoreMatchers.equalTo(SearchResult(artifacts = listOf(COMMON_JAVA8))))
  }

  @Test
  fun searchByGroupId() {
    Assert.assertThat(
      repository
        .search(SearchRequest(SearchQuery("com.android.support", ""), 50, 0))
        .get(10, TimeUnit.SECONDS)
        .clearStats(),
      CoreMatchers.equalTo(
        SearchResult(artifacts = listOf(LEANBACK, RECOMMENDATION, RECYCLER_VIEW_V7, SUPPORT_COMPAT, SUPPORT_VECTOR_DRAWABLE))))
  }

  @Test
  fun searchByNameWildcard() {
    Assert.assertThat(
      repository
        .search(SearchRequest(SearchQuery("", "common*"), 50, 0))
        .get(10, TimeUnit.SECONDS)
        .clearStats(),
      CoreMatchers.equalTo(SearchResult(artifacts = listOf(COMMON, COMMON_JAVA8))))
  }

  @Test
  fun searchByGroupIdWildcard() {
    Assert.assertThat(
      repository
        .search(SearchRequest(SearchQuery("com.android.*", null), 50, 0))
        .get(10, TimeUnit.SECONDS)
        .clearStats(),
      CoreMatchers.equalTo(
        SearchResult(artifacts = listOf(LEANBACK, RECOMMENDATION, RECYCLER_VIEW_V7, SUPPORT_COMPAT, SUPPORT_VECTOR_DRAWABLE))))
  }

  @Test
  fun searchByExactMatch() {
    Assert.assertThat(
      repository
        .search(SearchRequest(SearchQuery("com.android.support", "leanback-v17"), 50, 0))
        .get(10, TimeUnit.SECONDS)
        .clearStats(),
      CoreMatchers.equalTo(SearchResult(artifacts = listOf(LEANBACK))))
  }

  @Test
  fun searchByWildcard() {
    Assert.assertThat(
      repository
        .search(SearchRequest(SearchQuery("android.*", "common-*"), 50, 0))
        .get(10, TimeUnit.SECONDS)
        .clearStats(),
      CoreMatchers.equalTo(SearchResult(artifacts = listOf(COMMON_JAVA8))))
  }
}

private fun SearchResult.clearStats() = copy(stats = SearchResultStats.EMPTY)