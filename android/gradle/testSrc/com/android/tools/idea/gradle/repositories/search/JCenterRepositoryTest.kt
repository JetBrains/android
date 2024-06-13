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

import com.google.common.truth.Truth.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader

/**
 * Tests for [JCenterRepository].
 */
class JCenterRepositoryTest {
  @Test
  fun testCreateUrlWithGroupId() {
    val request = SearchRequest(SingleModuleSearchQuery("com.google.guava", "guava"), 20, 1)
    val url = JCenterRepository.createRequestUrl(request)
    assertEquals("https://api.bintray.com/search/packages/maven?g=com.google.guava&a=guava&subject=bintray&repo=jcenter", url)
  }

  @Test
  fun testCreateUrlWithoutGroupId() {
    val request = SearchRequest(ArbitraryModulesSearchQuery(null, "guava"), 20, 1)
    val url = JCenterRepository.createRequestUrl(request)
    assertEquals("https://api.bintray.com/search/packages/maven?a=guava&subject=bintray&repo=jcenter", url)
  }

  @Test
  fun testCreateUrlWithoutModuleName() {
    val request = SearchRequest(ArbitraryModulesSearchByModuleQuery("guava"), 20, 1)
    val url = JCenterRepository.createRequestUrl(request)
    assertEquals("https://api.bintray.com/search/packages/maven?a=guava&subject=bintray&repo=jcenter", url)
  }

  @Test
  fun testParse() {
    @Language("JSON")
    val response = """
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
            "8.1"
          ],
          "latest_version": "8.1"
        },
        {
          "name": "just-some-test-not-necessarily-in-a-form-of-maven-id",
          "repo": "jcenter",
          "owner": "bintray",
          "desc": null,
          "system_ids": [
            "io.janusproject.guava:guava"
          ],
          "versions": [
            "19.0.0"
          ],
          "latest_version": "19.0.0"
        },
        {
          "name": "com.google.guava:guava",
          "repo": "jcenter",
          "owner": "bintray",
          "desc": "Guava is a suite of core and expanded libraries that include\\n    utility classes, google's collections...",
          "system_ids": [
            "com.google.guava:guava"
          ],
          "versions": [
            "19.0",
            "18.0",
            "17.0",
            "16.0",
            "15.0",
            "14.0",
            "13.0"
          ],
          "latest_version": "19.0"
        },
        {
          "name": "de.weltraumschaf.commons:guava",
          "repo": "jcenter",
          "owner": "bintray",
          "desc": null,
          "system_ids": [
            "de.weltraumschaf.commons:guava"
          ],
          "versions": [
            "2.1.0"
          ],
          "latest_version": "2.1.0"
        }
      ]"""
    val responseReader = StringReader(response)
    val result = JCenterRepository.parse(responseReader)
    val coordinates = result.artifactCoordinates
    assertThat(coordinates).containsExactly(
      "com.atlassian.guava:guava:15.0",
      "com.atlassian.bundles:guava:8.1",
      "io.janusproject.guava:guava:19.0.0",
      "com.google.guava:guava:19.0",
      "com.google.guava:guava:18.0",
      "com.google.guava:guava:17.0",
      "com.google.guava:guava:16.0",
      "com.google.guava:guava:15.0",
      "com.google.guava:guava:14.0",
      "com.google.guava:guava:13.0",
      "de.weltraumschaf.commons:guava:2.1.0")
  }
}