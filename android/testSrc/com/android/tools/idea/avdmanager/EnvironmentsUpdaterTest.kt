/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.avdmanager

import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.testutils.file.recordExistingFile
import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import org.junit.Test

/** Tests for [EnvironmentsUpdater]. */
class EnvironmentsUpdaterTest {

  @Test
  fun testUpdateDirectory() {
    val root = createInMemoryFileSystemAndFolder("testRoot")
    val sourceDir = root.resolve("source")
    val destDir = root.resolve("dest")

    // Setup source directory.
    sourceDir.resolve("file1.txt").recordExistingFile(10000L, "v1".toByteArray())
    sourceDir.resolve("sub/file2.txt").recordExistingFile(10000L, "v2".toByteArray())

    // 1) Initial copy.
    updateDirectory(sourceDir, destDir)

    assertThat(Files.readAllBytes(destDir.resolve("file1.txt"))).isEqualTo("v1".toByteArray())
    assertThat(Files.readAllBytes(destDir.resolve("sub/file2.txt"))).isEqualTo("v2".toByteArray())

    // 2) Modify file in destination to simulate existing older file; recordExistingFile will also create parent directories if needed.
    sourceDir.resolve("file1.txt").recordExistingFile(5000L, "v1_older".toByteArray())
    updateDirectory(sourceDir, destDir)

    // Verify it wasn't overwritten by the older source.
    assertThat(Files.readAllBytes(destDir.resolve("file1.txt"))).isEqualTo("v1".toByteArray())

    // 3) Update source with a newer timestamp.
    sourceDir.resolve("file1.txt").recordExistingFile(20000L, "v1_newer".toByteArray())
    updateDirectory(sourceDir, destDir)

    // Verify it was overwritten by the newer source.
    assertThat(Files.readAllBytes(destDir.resolve("file1.txt"))).isEqualTo("v1_newer".toByteArray())
  }
}
