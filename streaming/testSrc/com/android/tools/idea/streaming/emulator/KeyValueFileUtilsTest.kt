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
package com.android.tools.idea.streaming.emulator

import com.android.testutils.file.DelegatingFileSystemProvider
import com.android.testutils.file.createInMemoryFileSystem
import com.android.testutils.file.getExistingFiles
import com.android.testutils.file.someRoot
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.idea.testing.executeCapturingLoggedErrors
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.TestLoggerFactory
import java.io.IOException
import java.nio.file.CopyOption
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Test for functions defined in `KeyValueFileUtils.kt`. */
class KeyValueFileUtilsTest {
  private var exception: IOException? = null
  private lateinit var originalLoggerFactory: Logger.Factory

  @Before
  fun setUp() {
    originalLoggerFactory = Logger.getFactory()
    Logger.setFactory(TestLoggerFactory::class.java)
  }

  @After
  fun tearDown() {
    Logger.setFactory(originalLoggerFactory)
  }

  fun updateAndAssert(path: Path) {
    Files.write(
      path,
      listOf(
        "AvdId = Pixel_4_XL_API_30",
        "PlayStore.enabled = false",
        "avd.ini.displayname = Pixel 4 XL API 30",
        "fastboot.chosenSnapshotFile = snapshot42",
        "fastboot.forceChosenSnapshotBoot = yes",
        "hw.sensors.orientation = yes",
      ),
    )
    // Check normal update.
    updateKeyValueFile(
      path,
      mapOf(
        "PlayStore.enabled" to "true",
        "fastboot.chosenSnapshotFile" to null,
        "fastboot.forceChosenSnapshotBoot" to "no",
        "fastboot.forceFastBoot" to "yes",
      ),
    )
    assertThat(path)
      .hasContents(
        "AvdId=Pixel_4_XL_API_30",
        "PlayStore.enabled=true",
        "avd.ini.displayname=Pixel 4 XL API 30",
        "fastboot.forceChosenSnapshotBoot=no",
        "fastboot.forceFastBoot=yes",
        "hw.sensors.orientation=yes",
      )
  }

  @Test
  fun testUpdateKeyValueFileMockFilesystem() {
    val fileSystem = MockFileSystemProvider(createInMemoryFileSystem()).fileSystem
    val path = fileSystem.someRoot.resolve("test.ini")
    updateAndAssert(path)

    assertThat(fileSystem.getExistingFiles()).containsExactly("$path") // No extra files left behind.

    // Check with I/O errors.
    exception = IOException("simulated I/O error")
    val errors = executeCapturingLoggedErrors { updateKeyValueFile(path, mapOf("PlayStore.enabled" to "false")) }
    assertThat(errors).containsExactly("Error writing $path - simulated I/O error")
    assertThat(fileSystem.getExistingFiles()).containsExactly("$path") // No extra files left behind.
  }

  @Test
  fun testUpdateKeyValueFileRealFilesystem() {
    val dir = Files.createTempDirectory("key-value")
    val path = dir.resolve("test.ini")
    try {
      updateAndAssert(path)
    } finally {
      path.deleteIfExists()
      Files.delete(dir)
    }
  }

  private inner class MockFileSystemProvider(fileSystem: FileSystem) : DelegatingFileSystemProvider(fileSystem) {
    override fun move(source: Path, target: Path, vararg options: CopyOption) {
      exception?.let { throw it }
      // https://github.com/google/jimfs/issues/478
      super.move(source, target, StandardCopyOption.REPLACE_EXISTING, *options)
    }
  }
}
