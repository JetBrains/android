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
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import org.junit.Test
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration

/**
 * Tests for [MavenClassRegistry].
 */
class MavenClassRegistryTest {
  @Test
  fun parseJsonFile() {
    val gMavenIndexRepositoryMock: GMavenIndexRepository = mock()
    whenever(gMavenIndexRepositoryMock.loadIndexFromDisk()).thenReturn(
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

    val mavenClassRegistry = MavenClassRegistry(gMavenIndexRepositoryMock)

    assertThat(mavenClassRegistry.lookup.classNameMap).containsExactlyEntriesIn(
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
    whenever(gMavenIndexRepositoryMock.loadIndexFromDisk()).thenReturn(
      """
        {
          "Indices": [
          ]
        }
      """.trimIndent().byteInputStream(UTF_8)
    )

    val mavenClassRegistry = MavenClassRegistry(gMavenIndexRepositoryMock)

    assertThat(mavenClassRegistry.lookup.classNameMap).isEmpty()
    assertThat(mavenClassRegistry.lookup.ktxMap).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noGroupIdDeclared() {
    val gMavenIndexRepositoryMock: GMavenIndexRepository = mock()
    whenever(gMavenIndexRepositoryMock.loadIndexFromDisk()).thenReturn(
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

    val mavenClassRegistry = MavenClassRegistry(gMavenIndexRepositoryMock)

    assertThat(mavenClassRegistry.lookup.classNameMap).isEmpty()
    assertThat(mavenClassRegistry.lookup.ktxMap).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noArtifactIdDeclared() {
    val gMavenIndexRepositoryMock: GMavenIndexRepository = mock()
    whenever(gMavenIndexRepositoryMock.loadIndexFromDisk()).thenReturn(
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

    val mavenClassRegistry = MavenClassRegistry(gMavenIndexRepositoryMock)

    assertThat(mavenClassRegistry.lookup.classNameMap).isEmpty()
    assertThat(mavenClassRegistry.lookup.ktxMap).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noVersionDeclared() {
    val gMavenIndexRepositoryMock: GMavenIndexRepository = mock()
    whenever(gMavenIndexRepositoryMock.loadIndexFromDisk()).thenReturn(
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

    val mavenClassRegistry = MavenClassRegistry(gMavenIndexRepositoryMock)

    assertThat(mavenClassRegistry.lookup.classNameMap).isEmpty()
    assertThat(mavenClassRegistry.lookup.ktxMap).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noFqcnsDeclared() {
    val gMavenIndexRepositoryMock: GMavenIndexRepository = mock()
    whenever(gMavenIndexRepositoryMock.loadIndexFromDisk()).thenReturn(
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

    val mavenClassRegistry = MavenClassRegistry(gMavenIndexRepositoryMock)

    assertThat(mavenClassRegistry.lookup.classNameMap).isEmpty()
    assertThat(mavenClassRegistry.lookup.ktxMap).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noKtxTargetsDeclared() {
    val gMavenIndexRepositoryMock: GMavenIndexRepository = mock()
    whenever(gMavenIndexRepositoryMock.loadIndexFromDisk()).thenReturn(
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

    val mavenClassRegistry = MavenClassRegistry(gMavenIndexRepositoryMock)

    assertThat(mavenClassRegistry.lookup.classNameMap).isEmpty()
    assertThat(mavenClassRegistry.lookup.ktxMap).isEmpty()
  }

  @Test
  fun parseJsonFile_skipUnknownKey() {
    val gMavenIndexRepositoryMock: GMavenIndexRepository = mock()
    whenever(gMavenIndexRepositoryMock.loadIndexFromDisk()).thenReturn(
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

    val mavenClassRegistry = MavenClassRegistry(gMavenIndexRepositoryMock)

    assertThat(mavenClassRegistry.lookup.classNameMap).containsExactlyEntriesIn(
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
  fun readOfflineIndexFile() {
    val tempDir = createInMemoryFileSystemAndFolder("tempCacheDir")
    val repository = GMavenIndexRepository("https://example.com", tempDir, Duration.ofDays(1))

    try {
      val mavenClassRegistry = MavenClassRegistry(repository)
      val data = repository.loadIndexFromDisk().bufferedReader(UTF_8).use {
        it.readText()
      }

      // Check if we have a valid built-in index file.
      assertThat(data).startsWith(
        """
          {
            "Index": [
              {
        """.trimIndent()
      )

      // Check if this offline index file can be properly parsed.
      mavenClassRegistry.lookup.classNameMap.let {
        assertThat(it.size).isAtLeast(6000)
        assertThat(it).containsEntry(
          "ComponentActivity",
          listOf(
            MavenClassRegistryBase.Library(
              artifact = "androidx.activity:activity",
              packageName = "androidx.activity",
              version = "1.6.1"
            )
          )
        )
        assertThat(it).containsEntry(
          "OnBackPressedDispatcher",
          listOf(
            MavenClassRegistryBase.Library(
              artifact = "androidx.activity:activity",
              packageName = "androidx.activity",
              version = "1.6.1"
            )
          )
        )
      }

      mavenClassRegistry.lookup.ktxMap.let {
        assertThat(it.size).isAtLeast(40)
        assertThat(it).containsEntry("androidx.navigation:navigation-fragment", "androidx.navigation:navigation-fragment-ktx")
        assertThat(it).containsEntry("androidx.activity:activity", "androidx.activity:activity-ktx")
      }
    }
    finally {
      Disposer.dispose(repository)
    }
  }
}