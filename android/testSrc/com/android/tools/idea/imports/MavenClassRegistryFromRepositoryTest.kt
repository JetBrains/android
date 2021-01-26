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

import com.android.testutils.MockitoKt.mock
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.`when`
import java.nio.charset.StandardCharsets.UTF_8

/**
 * Tests for [MavenClassRegistryFromRepository].
 */
class MavenClassRegistryFromRepositoryTest {
  @Test
  fun parseJsonFile() {
    val gMavenIndexRepositoryMock: GMavenIndexRepository = mock()
    `when`(gMavenIndexRepositoryMock.loadIndexFromDisk()).thenReturn(
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
      """.trimIndent().byteInputStream(UTF_8)
    )

    val mavenClassRegistry = MavenClassRegistryFromRepository(gMavenIndexRepositoryMock)

    assertThat(mavenClassRegistry.lookup).containsExactlyEntriesIn(
      mapOf(
        "ComponentActivity" to listOf(
          MavenClassRegistryBase.Library(artifact = "androidx.activity:activity", packageName = "androidx.activity")
        ),
        "Fake" to listOf(
          MavenClassRegistryBase.Library(artifact = "androidx.activity:activity", packageName = "androidx.activity"),
          MavenClassRegistryBase.Library(artifact = "androidx.annotation:annotation", packageName = "androidx.annotation")
        ),
        "AnimRes" to listOf(
          MavenClassRegistryBase.Library(artifact = "androidx.annotation:annotation", packageName = "androidx.annotation")
        )
      )
    )
  }

  @Test
  fun parseMalformedJsonFile_noIndexKeyDeclared() {
    val gMavenIndexRepositoryMock: GMavenIndexRepository = mock()
    `when`(gMavenIndexRepositoryMock.loadIndexFromDisk()).thenReturn(
      """
        {
          "Indices": [
          ]
        }
      """.trimIndent().byteInputStream(UTF_8)
    )

    val mavenClassRegistry = MavenClassRegistryFromRepository(gMavenIndexRepositoryMock)

    assertThat(mavenClassRegistry.lookup).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noGroupIdDeclared() {
    val gMavenIndexRepositoryMock: GMavenIndexRepository = mock()
    `when`(gMavenIndexRepositoryMock.loadIndexFromDisk()).thenReturn(
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
      """.trimIndent().byteInputStream(UTF_8)
    )

    val mavenClassRegistry = MavenClassRegistryFromRepository(gMavenIndexRepositoryMock)

    assertThat(mavenClassRegistry.lookup).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noArtifactIdDeclared() {
    val gMavenIndexRepositoryMock: GMavenIndexRepository = mock()
    `when`(gMavenIndexRepositoryMock.loadIndexFromDisk()).thenReturn(
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
      """.trimIndent().byteInputStream(UTF_8)
    )

    val mavenClassRegistry = MavenClassRegistryFromRepository(gMavenIndexRepositoryMock)

    assertThat(mavenClassRegistry.lookup).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noVersionDeclared() {
    val gMavenIndexRepositoryMock: GMavenIndexRepository = mock()
    `when`(gMavenIndexRepositoryMock.loadIndexFromDisk()).thenReturn(
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
      """.trimIndent().byteInputStream(UTF_8)
    )

    val mavenClassRegistry = MavenClassRegistryFromRepository(gMavenIndexRepositoryMock)

    assertThat(mavenClassRegistry.lookup).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noFqcnsDeclared() {
    val gMavenIndexRepositoryMock: GMavenIndexRepository = mock()
    `when`(gMavenIndexRepositoryMock.loadIndexFromDisk()).thenReturn(
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
      """.trimIndent().byteInputStream(UTF_8)
    )

    val mavenClassRegistry = MavenClassRegistryFromRepository(gMavenIndexRepositoryMock)

    assertThat(mavenClassRegistry.lookup).isEmpty()
  }
}