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
import com.android.tools.idea.imports.MavenClassRegistryBase.LibraryImportData
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import org.junit.Assert.assertThrows
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
              ],
              "ktlfns": [
                {
                  "fqn": "androidx.activity.result.PickVisualMediaRequestKt.PickVisualMediaRequest"
                },
                {
                  "fqn": "androidx.activity.FakeFunctionKt.FakeFunction"
                }
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
              ],
              "ktlfns": [
                {
                  "fqn": "androidx.annotation.FacadeFileKt.AnnotationFunction"
                },
                {
                  "fqn": "androidx.annotation.FakeFunctionKt.FakeFunction"
                }
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
          LibraryImportData(
            artifact = "androidx.activity:activity",
            importedItemFqName = "androidx.activity.ComponentActivity",
            importedItemPackageName = "androidx.activity",
            version = "1.1.0"
          )
        ),
        "Fake" to listOf(
          LibraryImportData(
            artifact = "androidx.activity:activity",
            importedItemFqName = "androidx.activity.Fake",
            importedItemPackageName = "androidx.activity",
            version = "1.1.0"
          ),
          LibraryImportData(
            artifact = "androidx.annotation:annotation",
            importedItemFqName = "androidx.annotation.Fake",
            importedItemPackageName = "androidx.annotation",
            version = "1.1.0"
          )
        ),
        "AnimRes" to listOf(
          LibraryImportData(
            artifact = "androidx.annotation:annotation",
            importedItemFqName = "androidx.annotation.AnimRes",
            importedItemPackageName = "androidx.annotation",
            version = "1.1.0"
          )
        )
      )
    )

    assertThat(mavenClassRegistry.lookup.topLevelFunctionsMap).containsExactlyEntriesIn(
      mapOf(
        "PickVisualMediaRequest" to listOf(
          LibraryImportData(
            artifact = "androidx.activity:activity",
            importedItemFqName = "androidx.activity.result.PickVisualMediaRequest",
            importedItemPackageName = "androidx.activity.result",
            version = "1.1.0"
          )
        ),
        "FakeFunction" to listOf(
          LibraryImportData(
            artifact = "androidx.activity:activity",
            importedItemFqName = "androidx.activity.FakeFunction",
            importedItemPackageName = "androidx.activity",
            version = "1.1.0"
          ),
          LibraryImportData(
            artifact = "androidx.annotation:annotation",
            importedItemFqName = "androidx.annotation.FakeFunction",
            importedItemPackageName = "androidx.annotation",
            version = "1.1.0"
          )
        ),
        "AnnotationFunction" to listOf(
          LibraryImportData(
            artifact = "androidx.annotation:annotation",
            importedItemFqName = "androidx.annotation.AnnotationFunction",
            importedItemPackageName = "androidx.annotation",
            version = "1.1.0"
          )
        ),
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
  fun parseJsonFile_topLevelFunctionsPropertyIsOptional() {
    val gMavenIndexRepositoryMock: GMavenIndexRepository = mock()
    whenever(gMavenIndexRepositoryMock.loadIndexFromDisk()).thenReturn(
      """
        {
          "Index": [
            {
              "groupId": "group1",
              "artifactId": "artifact1",
              "version": "1",
              "ktxTargets": [],
              "fqcns": [
                "class1"
              ]
            },
            {
              "groupId": "group2",
              "artifactId": "artifact2",
              "version": "1",
              "ktxTargets": [],
              "fqcns": [
                "class2"
              ],
              "ktlfns": []
            },
            {
              "groupId": "group3",
              "artifactId": "artifact3",
              "version": "1",
              "ktxTargets": [],
              "fqcns": [
                "class3"
              ],
              "ktlfns": [
                {
                  "fqn": "FacadeFileKt.someFqn",
                  "unrecognized": "should be ignored"
                },
                {
                  "has_no_fqn": "should be ignored"
                }
              ]
            }
          ]
        }
      """.trimIndent().byteInputStream(UTF_8)
    )

    val mavenClassRegistry = MavenClassRegistry(gMavenIndexRepositoryMock)

    assertThat(mavenClassRegistry.lookup.topLevelFunctionsMap).containsExactlyEntriesIn(
      mapOf(
        "someFqn" to listOf(LibraryImportData(
          artifact = "group3:artifact3",
          importedItemFqName = "someFqn",
          importedItemPackageName = "",
          version = "1"
        ))
      )
    )
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
          LibraryImportData(
            artifact = "androidx.activity:activity",
            importedItemFqName = "androidx.activity.ComponentActivity",
            importedItemPackageName = "androidx.activity",
            version = "1.1.0"
          )
        ),
        "Fake" to listOf(
          LibraryImportData(
            artifact = "androidx.activity:activity",
            importedItemFqName = "androidx.activity.Fake",
            importedItemPackageName = "androidx.activity",
            version = "1.1.0"
          ),
          LibraryImportData(
            artifact = "androidx.annotation:annotation",
            importedItemFqName = "androidx.annotation.Fake",
            importedItemPackageName = "androidx.annotation",
            version = "1.1.0"
          )
        ),
        "AnimRes" to listOf(
          LibraryImportData(
            artifact = "androidx.annotation:annotation",
            importedItemFqName = "androidx.annotation.AnimRes",
            importedItemPackageName = "androidx.annotation",
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
            LibraryImportData(
              artifact = "androidx.activity:activity",
              importedItemFqName = "androidx.activity.ComponentActivity",
              importedItemPackageName = "androidx.activity",
              version = "1.7.2"
            )
          )
        )
        assertThat(it).containsEntry(
          "OnBackPressedDispatcher",
          listOf(
            LibraryImportData(
              artifact = "androidx.activity:activity",
              importedItemFqName = "androidx.activity.OnBackPressedDispatcher",
              importedItemPackageName = "androidx.activity",
              version = "1.7.2"
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

  @Test
  fun kotlinTopLevelFunction_fromJvmQualifiedName() {
    with(KotlinTopLevelFunction.fromJvmQualifiedName("com.example.FileFacadeKt.foo")) {
      assertThat(simpleName).isEqualTo("foo")
      assertThat(packageName).isEqualTo("com.example")
      assertThat(kotlinFqName.asString()).isEqualTo("com.example.foo")
    }
  }

  @Test
  fun kotlinTopLevelFunction_fromJvmQualifiedName_noPackageName() {
    with(KotlinTopLevelFunction.fromJvmQualifiedName("FileFacadeKt.foo")) {
      assertThat(simpleName).isEqualTo("foo")
      assertThat(packageName).isEqualTo("")
      assertThat(kotlinFqName.asString()).isEqualTo("foo")
    }
  }

  @Test
  fun kotlinTopLevelFunction_fromJvmQualifiedName_noFacadeFile() {
    assertThrows(IllegalArgumentException::class.java) { KotlinTopLevelFunction.fromJvmQualifiedName("foo") }
  }
}
