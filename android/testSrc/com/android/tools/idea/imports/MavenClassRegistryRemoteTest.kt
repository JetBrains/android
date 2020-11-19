/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.imports

import com.android.ide.common.repository.NetworkCache
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.InputStream

/**
 * Tests for [MavenClassRegistryRemote].
 */
class MavenClassRegistryRemoteTest {
  @Test
  fun parseJsonFile() {
    val fakeGMavenIndexRepository = FakeGMavenIndexRepository(
      """
        {
          "Index": [
            {
              "groupId": "androidx.activity",
              "artifactId": "activity",
              "version": "1.1.0",
              "fqcns": [
                "androidx.activity.ComponentActivity",
                "androidx.activity.Fake"
              ]
            },
            {
              "groupId": "androidx.activity",
              "artifactId": "activity-ktx",
              "version": "1.1.0",
              "fqcns": []
            },
            {
              "groupId": "androidx.annotation",
              "artifactId": "annotation",
              "version": "1.1.0",
              "fqcns": [
                "androidx.annotation.AnimRes",
                "androidx.annotation.Fake"
              ]
            }
          ]
        }
      """.trimIndent()
    )
    val mavenClassRegistry = MavenClassRegistryRemote(fakeGMavenIndexRepository)

    assertThat(mavenClassRegistry.lookup).containsExactlyEntriesIn(
      mapOf(
        "ComponentActivity" to listOf(
          MavenClassRegistry.Library(artifact = "androidx.activity:activity", packageName = "androidx.activity")
        ),
        "Fake" to listOf(
          MavenClassRegistry.Library(artifact = "androidx.activity:activity", packageName = "androidx.activity"),
          MavenClassRegistry.Library(artifact = "androidx.annotation:annotation", packageName = "androidx.annotation")
        ),
        "AnimRes" to listOf(
          MavenClassRegistry.Library(artifact = "androidx.annotation:annotation", packageName = "androidx.annotation")
        )
      )
    )
  }

  @Test
  fun parseMalformedJsonFile_noIndexKeyDeclared() {
    val fakeGMavenIndexRepository = FakeGMavenIndexRepository(
      """
        {
          "Indices": [
          ]
        }
      """.trimIndent()
    )
    val mavenClassRegistry = MavenClassRegistryRemote(fakeGMavenIndexRepository)

    assertThat(mavenClassRegistry.lookup).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noGroupIdDeclared() {
    val fakeGMavenIndexRepository = FakeGMavenIndexRepository(
      """
        {
          "Index": [
            {
              "artifactId": "activity",
              "version": "1.1.0",
              "fqcns": [
                "androidx.activity.ComponentActivity",
                "androidx.activity.Fake"
              ]
            },
          ]
        }
      """.trimIndent()
    )
    val mavenClassRegistry = MavenClassRegistryRemote(fakeGMavenIndexRepository)

    assertThat(mavenClassRegistry.lookup).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noArtifactIdDeclared() {
    val fakeGMavenIndexRepository = FakeGMavenIndexRepository(
      """
        {
          "Index": [
            {
              "groupId": "androidx.activity",
              "version": "1.1.0",
              "fqcns": [
                "androidx.activity.ComponentActivity",
                "androidx.activity.Fake"
              ]
            },
          ]
        }
      """.trimIndent()
    )
    val mavenClassRegistry = MavenClassRegistryRemote(fakeGMavenIndexRepository)

    assertThat(mavenClassRegistry.lookup).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noVersionDeclared() {
    val fakeGMavenIndexRepository = FakeGMavenIndexRepository(
      """
        {
          "Index": [
            {
              "groupId": "androidx.activity",
              "artifactId": "activity",
              "fqcns": [
                "androidx.activity.ComponentActivity",
                "androidx.activity.Fake"
              ]
            },
          ]
        }
      """.trimIndent()
    )
    val mavenClassRegistry = MavenClassRegistryRemote(fakeGMavenIndexRepository)

    assertThat(mavenClassRegistry.lookup).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noFqcnsDeclared() {
    val fakeGMavenIndexRepository = FakeGMavenIndexRepository(
      """
        {
          "Index": [
            {
              "groupId": "androidx.activity",
              "artifactId": "activity",
              "version": "1.1.0"
            },
          ]
        }
      """.trimIndent()
    )
    val mavenClassRegistry = MavenClassRegistryRemote(fakeGMavenIndexRepository)

    assertThat(mavenClassRegistry.lookup).isEmpty()
  }

  private class FakeGMavenIndexRepository(
    val data: String
  ) : GMavenIndexRepository, NetworkCache("https://example.com/", GMAVEN_INDEX_CACHE_DIR_KEY, null) {
    override fun readUrlData(url: String, timeout: Int): ByteArray? = data.toByteArray()
    override fun error(throwable: Throwable, message: String?) = throw throwable
    override fun readDefaultData(relative: String) = data.byteInputStream()
    override fun fetchIndex(relative: String): InputStream? = findData(relative)
  }
}