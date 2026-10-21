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
package com.android.tools.profilers.cpu

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.util.zip.ZipFile
import org.junit.Test

class TraceMergerTest {

  /**
   * Tests the behavior of the TraceMerger utility used by Compose Tracing 2.0.
   *
   * It validates that:
   * 1. The merger successfully zips the OS trace and all Compose app traces together.
   * 2. The merger uses the actual file names for the zip entries.
   * 3. The integrity of the payload data is maintained inside the archive.
   */
  @Test
  fun `mergeTraces creates valid zip with inputs`() {
    val tempDir = FileUtil.createTempDirectory("TraceMergerTest", "zip")

    try {
      val osTraceName = "os_trace.pb"
      val osTrace = File(tempDir, osTraceName)
      osTrace.writeText("os data")

      val appTrace1Name = "app1.perfetto-trace"
      val appTrace1 = File(tempDir, appTrace1Name)
      appTrace1.writeText("app 1 data")

      val appTrace2Name = "app2.perfetto-trace"
      val appTrace2 = File(tempDir, appTrace2Name)
      appTrace2.writeText("app 2 data")

      val filesToMerge = listOf(osTrace, appTrace1, appTrace2)

      // Assert files exist before merge
      assertThat(osTrace.exists()).isTrue()
      assertThat(appTrace1.exists()).isTrue()
      assertThat(appTrace2.exists()).isTrue()

      val mergedZip = File(tempDir, "merged.zip")
      TraceMerger.mergeTraces(osTrace, listOf(appTrace1, appTrace2), mergedZip)

      // Assert zip contents
      assertThat(mergedZip.exists()).isTrue()
      assertThat(mergedZip.extension).isEqualTo("zip")

      val zipFile = ZipFile(mergedZip)
      val entries = zipFile.entries().toList()
      assertThat(entries.map { it.name }).containsExactly(osTraceName, appTrace1Name, appTrace2Name)

      zipFile.getInputStream(zipFile.getEntry(osTraceName)).use { assertThat(it.readBytes()).isEqualTo(osTrace.readBytes()) }

      zipFile.getInputStream(zipFile.getEntry(appTrace1Name)).use { assertThat(it.readBytes()).isEqualTo(appTrace1.readBytes()) }

      zipFile.getInputStream(zipFile.getEntry(appTrace2Name)).use { assertThat(it.readBytes()).isEqualTo(appTrace2.readBytes()) }

      zipFile.close()
      mergedZip.delete()
    } finally {
      FileUtil.delete(tempDir)
    }
  }
}
