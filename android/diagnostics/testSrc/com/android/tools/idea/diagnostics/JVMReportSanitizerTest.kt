/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.diagnostics

import com.android.testutils.TestUtils
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Path
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JVMReportSanitizerTest {

  @Rule
  @JvmField
  val tmpFolder = TemporaryFolder()

  private fun generateTestFile(path: String): File {
    val reportPath: Path = TestUtils.resolveWorkspacePath(path)
    return reportPath.toFile()
  }

  private fun generateSanitizedString(path: String): String {
    val sanitizedReport = StringBuilder()
    val sanitizedReportPath: Path = TestUtils.resolveWorkspacePath(path)
    val sanitizedReportStream: InputStream = sanitizedReportPath.toFile().inputStream()
    BufferedReader(InputStreamReader(sanitizedReportStream)).use { reader ->
      var line: String?
      while (reader.readLine().also { line = it } != null) {
        sanitizedReport.append(line).append("\n")
      }
    }
    return sanitizedReport.toString()
  }

  @Test
  fun testSanitizeFullReport() {
    val JVMReportFullRelativePath = "tools/adt/idea/android/testData/diagnostics/JVMReportFull.log"
    val JVMReportFullSanitizedRelativePath = "tools/adt/idea/android/testData/diagnostics/JVMReportFullSanitized.log"

    val fullReportFile = generateTestFile(JVMReportFullRelativePath)
    val fullReportSanitizedString = generateSanitizedString(JVMReportFullSanitizedRelativePath)
    assertEquals(fullReportSanitizedString, JVMReportSanitizer.sanitize(fullReportFile))

    val JVMReportWindowsRelativePath = "tools/adt/idea/android/testData/diagnostics/JVMReportWindows.txt"
    val JVMReportWindowsSanitizedRelativePath = "tools/adt/idea/android/testData/diagnostics/JVMReportWindowsSanitized.txt"

    val windowsFile = generateTestFile(JVMReportWindowsRelativePath)
    val windowsSanitizedString = generateSanitizedString(JVMReportWindowsSanitizedRelativePath)
    assertEquals(windowsSanitizedString, JVMReportSanitizer.sanitize(windowsFile))
  }

  @Test
  fun testSanitizeReportsMissingSections() {
    val JVMReportMissingHeaderRelativePath = "tools/adt/idea/android/testData/diagnostics/JVMReportMissingHeader.log"
    val JVMReportMissingHeaderSanitizedRelativePath = "tools/adt/idea/android/testData/diagnostics/JVMReportMissingHeaderSanitized.log"

    val missingHeaderFile: File = generateTestFile(JVMReportMissingHeaderRelativePath)
    val missingHeaderSanitizedString = generateSanitizedString(JVMReportMissingHeaderSanitizedRelativePath)
    assertEquals(missingHeaderSanitizedString, JVMReportSanitizer.sanitize(missingHeaderFile))

    val JVMReportMissingMultipleSectionsRelativePath = "tools/adt/idea/android/testData/diagnostics/JVMReportMissingMultipleSections.log"
    val JVMReportMissingMultipleSectionsSanitizedRelativePath = "tools/adt/idea/android/testData/diagnostics/JVMReportMissingMultipleSectionsSanitized.log"

    val missingMultipleSectionsFile: File = generateTestFile(JVMReportMissingMultipleSectionsRelativePath)
    val missingMultipleSectionsSanitizedString = generateSanitizedString(JVMReportMissingMultipleSectionsSanitizedRelativePath)
    assertEquals(missingMultipleSectionsSanitizedString, JVMReportSanitizer.sanitize(missingMultipleSectionsFile))
  }

  @Test
  fun testSanitizeEmptyFile() {
    val emptyFile = tmpFolder.newFile("emptyFile")
    assertEquals("", JVMReportSanitizer.sanitize(emptyFile))
  }
}
