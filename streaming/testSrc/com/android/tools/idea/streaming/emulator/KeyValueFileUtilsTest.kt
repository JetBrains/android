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
import com.android.tools.idea.executeCapturingLoggedErrors
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.TestLoggerFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.nio.file.CopyOption
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path

/**
 * Test for functions defined in `KeyValueFileUtils.kt`.
 */
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

  @Test
  fun testUpdateKeyValueFile() {
    val fileSystem = MockFileSystemProvider(createInMemoryFileSystem()).fileSystem
    val file = fileSystem.someRoot.resolve("test.ini")
    Files.write(file, listOf("AvdId = Pixel_4_XL_API_30",
                             "PlayStore.enabled = false",
                             "avd.ini.displayname = Pixel 4 XL API 30",
                             "fastboot.chosenSnapshotFile = snapshot42",
                             "fastboot.forceChosenSnapshotBoot = yes",
                             "hw.sensors.orientation = yes"))
    // Check normal update.
    updateKeyValueFile(file, mapOf("PlayStore.enabled" to "true",
                                   "fastboot.chosenSnapshotFile" to null,
                                   "fastboot.forceChosenSnapshotBoot" to "no",
                                   "fastboot.forceFastBoot" to "yes"))
    assertThat(file).hasContents("AvdId=Pixel_4_XL_API_30",
                                 "PlayStore.enabled=true",
                                 "avd.ini.displayname=Pixel 4 XL API 30",
                                 "fastboot.forceChosenSnapshotBoot=no",
                                 "fastboot.forceFastBoot=yes",
                                 "hw.sensors.orientation=yes")
    assertThat(fileSystem.getExistingFiles()).containsExactly("$file") // No extra files left behind.

    // Check with I/O errors.
    exception = IOException("simulated I/O error")
    val errors = executeCapturingLoggedErrors {
      updateKeyValueFile(file, mapOf("PlayStore.enabled" to "false"))
    }
    assertThat(errors).containsExactly("Error writing $file - simulated I/O error")
    assertThat(fileSystem.getExistingFiles()).containsExactly("$file") // No extra files left behind.
  }

  private inner class MockFileSystemProvider(fileSystem: FileSystem) : DelegatingFileSystemProvider(fileSystem) {
    override fun move(source: Path, target: Path, vararg options: CopyOption) {
      exception?.let { throw it }
      super.move(source, target, *options)
    }
  }
}