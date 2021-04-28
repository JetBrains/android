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
              "ktxTargets": [],
              "fqcns": [
                "androidx.activity.ComponentActivity",
                "androidx.activity.Fake"
              ]
            },
            {
              "groupId": "androidx.activity",
              "artifactId": "activity-ktx",
              "version": "1.1.0",
              "ktxTargets": [
                "androidx.activity:activity"
              ],
              "fqcns": []
            },
            {
              "groupId": "androidx.annotation",
              "artifactId": "annotation",
              "version": "1.1.0",
              "ktxTargets": [],
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

    assertThat(mavenClassRegistry.lookup.fqcnMap).containsExactlyEntriesIn(
      mapOf(
        "ComponentActivity" to listOf(
          MavenClassRegistryBase.Library(
            artifact = "androidx.activity:activity",
            packageName = "androidx.activity",
            version = "1.1.0"
          )
        ),
        "Fake" to listOf(
          MavenClassRegistryBase.Library(
            artifact = "androidx.activity:activity",
            packageName = "androidx.activity",
            version = "1.1.0"
          ),
          MavenClassRegistryBase.Library(
            artifact = "androidx.annotation:annotation",
            packageName = "androidx.annotation",
            version = "1.1.0"
          )
        ),
        "AnimRes" to listOf(
          MavenClassRegistryBase.Library(
            artifact = "androidx.annotation:annotation",
            packageName = "androidx.annotation",
            version = "1.1.0"
          )
        )
      )
    )

    assertThat(mavenClassRegistry.lookup.ktxMap).containsExactlyEntriesIn(
      mapOf(
        "androidx.activity:activity" to "androidx.activity:activity-ktx"
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

    assertThat(mavenClassRegistry.lookup.fqcnMap).isEmpty()
    assertThat(mavenClassRegistry.lookup.ktxMap).isEmpty()
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
              "ktxTargets": [],
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

    assertThat(mavenClassRegistry.lookup.fqcnMap).isEmpty()
    assertThat(mavenClassRegistry.lookup.ktxMap).isEmpty()
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
              "ktxTargets": [],
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

    assertThat(mavenClassRegistry.lookup.fqcnMap).isEmpty()
    assertThat(mavenClassRegistry.lookup.ktxMap).isEmpty()
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
              "artifactId": "activity-ktx",
              "ktxTargets": [
                "androidx.activity:activity"
              ],
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

    assertThat(mavenClassRegistry.lookup.fqcnMap).isEmpty()
    assertThat(mavenClassRegistry.lookup.ktxMap).isEmpty()
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
              "artifactId": "activity-ktx",
              "version": "1.1.0",
              "ktxTargets": [
                "androidx.activity:activity"
              ]
            },
          ]
        }
      """.trimIndent().byteInputStream(UTF_8)
    )

    val mavenClassRegistry = MavenClassRegistryFromRepository(gMavenIndexRepositoryMock)

    assertThat(mavenClassRegistry.lookup.fqcnMap).isEmpty()
    assertThat(mavenClassRegistry.lookup.ktxMap).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noKtxTargetsDeclared() {
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
          ]
        }
      """.trimIndent().byteInputStream(UTF_8)
    )

    val mavenClassRegistry = MavenClassRegistryFromRepository(gMavenIndexRepositoryMock)

    assertThat(mavenClassRegistry.lookup.fqcnMap).isEmpty()
    assertThat(mavenClassRegistry.lookup.ktxMap).isEmpty()
  }

  @Test
  fun parseJsonFile_skipUnknownKey() {
    val gMavenIndexRepositoryMock: GMavenIndexRepository = mock()
    `when`(gMavenIndexRepositoryMock.loadIndexFromDisk()).thenReturn(
      """
        {
          "UnKnown1": [],
          "UnKnown2": [
            {
              "a": "",
              "b": ""
            }
          ],
          "Index": [
            {
              "groupId": "androidx.activity",
              "artifactId": "activity",
              "version": "1.1.0",
              "ktxTargets": [],
              "unKnown3": "unknown content",
              "fqcns": [
                "androidx.activity.ComponentActivity",
                "androidx.activity.Fake"
              ]
            },
            {
              "groupId": "androidx.activity",
              "artifactId": "activity-ktx",
              "version": "1.1.0",
              "ktxTargets": [
                "androidx.activity:activity"
              ],
              "unKnown4": [
                "a",
                "b"
              ],
              "fqcns": []
            },
            {
              "groupId": "androidx.annotation",
              "artifactId": "annotation",
              "version": "1.1.0",
              "ktxTargets": [],
              "unKnown5": [],
              "fqcns": [
                "androidx.annotation.AnimRes",
                "androidx.annotation.Fake"
              ]
            }
          ],
          "UnKnown6": "unknown content"
        }
      """.trimIndent().byteInputStream(UTF_8)
    )

    val mavenClassRegistry = MavenClassRegistryFromRepository(gMavenIndexRepositoryMock)

    assertThat(mavenClassRegistry.lookup.fqcnMap).containsExactlyEntriesIn(
      mapOf(
        "ComponentActivity" to listOf(
          MavenClassRegistryBase.Library(
            artifact = "androidx.activity:activity",
            packageName = "androidx.activity",
            version = "1.1.0"
          )
        ),
        "Fake" to listOf(
          MavenClassRegistryBase.Library(
            artifact = "androidx.activity:activity",
            packageName = "androidx.activity",
            version = "1.1.0"
          ),
          MavenClassRegistryBase.Library(
            artifact = "androidx.annotation:annotation",
            packageName = "androidx.annotation",
            version = "1.1.0"
          )
        ),
        "AnimRes" to listOf(
          MavenClassRegistryBase.Library(
            artifact = "androidx.annotation:annotation",
            packageName = "androidx.annotation",
            version = "1.1.0"
          )
        )
      )
    )
    assertThat(mavenClassRegistry.lookup.ktxMap).containsExactlyEntriesIn(
      mapOf(
        "androidx.activity:activity" to "androidx.activity:activity-ktx"
      )
    )
  }
}