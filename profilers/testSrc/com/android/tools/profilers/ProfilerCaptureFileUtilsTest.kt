/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.profilers

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import org.junit.Before
import org.junit.Test

class ProfilerCaptureFileUtilsTest {

  @Before
  fun setUp() {
    FileUtil.resetCanonicalTempPathCache(FileUtil.getTempDirectory())
  }

  @Test
  fun testRenameToTargetFile() {
    val tempDir = FileUtil.getTempDirectory()
    val sourceFile = File(tempDir, "temp_capture_123.tmp")
    sourceFile.writeText("test trace content")

    val targetName = "test_target_trace.trace"
    val resultFile = ProfilerCaptureFileUtils.renameToTargetFile(sourceFile, targetName)

    assertThat(resultFile).isNotNull()
    assertThat(resultFile!!.name).isEqualTo(targetName)
    assertThat(resultFile.parentFile.absolutePath).isEqualTo(File(tempDir).absolutePath)
    assertThat(resultFile.exists()).isTrue()
    assertThat(resultFile.readText()).isEqualTo("test trace content")
    assertThat(sourceFile.exists()).isFalse()

    // Cleanup
    resultFile.delete()
  }

  @Test
  fun testGetTraceFile() {
    val tempDir = FileUtil.getTempDirectory()
    val expectedFile = File(tempDir, "capture_98765.trace")
    val resultFile = ProfilerCaptureFileUtils.getTraceFile(98765L)
    assertThat(resultFile.absolutePath).isEqualTo(expectedFile.absolutePath)
  }

  @Test
  fun testGetCaptureFile() {
    val tempDir = FileUtil.getTempDirectory()
    val expectedFile = File(tempDir, "memory_test_capture.hprof")
    val resultFile = ProfilerCaptureFileUtils.getCaptureFile("memory_test_capture.hprof")

    assertThat(resultFile.absolutePath).isEqualTo(expectedFile.absolutePath)
  }
}
