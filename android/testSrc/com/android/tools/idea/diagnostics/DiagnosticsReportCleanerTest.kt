/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.google.common.truth.Truth
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.createFile
import com.intellij.util.io.exists
import junit.framework.TestCase
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectory

class DiagnosticsReportCleanerTest : TestCase() {
  lateinit var testDirectoryPath: Path

  override fun setUp() {
    super.setUp()
    testDirectoryPath = FileUtil.createTempDirectory("DiagnosticsReportCleanerTest", null).toPath()
  }

  override fun tearDown() {
    testDirectoryPath.toFile().deleteRecursively()
    super.tearDown()
  }

  fun `test DiagnosticsReportCleanerTest cleanupFiles only deletes old files`() {
    val reportDir = testDirectoryPath.resolve("reportDir")
    reportDir.createDirectory()

    val oldReportFile = reportDir.resolve("oldReport.txt")
    oldReportFile.createFile()
    Files.setLastModifiedTime(oldReportFile, FileTime.fromMillis(0))

    val newReportFile = reportDir.resolve("newReport.txt")
    newReportFile.createFile()

    DiagnosticsReportCleaner.cleanupFiles(reportDir)
    Truth.assertThat(oldReportFile.exists()).isFalse()
    Truth.assertThat(newReportFile.exists())
  }

  fun `test DiagnosticsReportCleanerTest cleanupDirectories only deletes old directories`() {
    // setModifiedFileTime doesn't appear to work on directories
    // check that new and other directories are not deleted
    val newReportDir = testDirectoryPath.resolve("reportDirNew")
    newReportDir.createDirectory()

    val otherDir = testDirectoryPath.resolve("otherDir")
    otherDir.createDirectory()
    Files.setLastModifiedTime(otherDir, FileTime.fromMillis(0))

    DiagnosticsReportCleaner.cleanupDirectories(testDirectoryPath, arrayOf(Regex("^reportDir.*")))
    Truth.assertThat(newReportDir.exists())
    Truth.assertThat(otherDir.exists())
  }

  fun `test DiagnosticsReportCleanerTest handles missing directories`() {
    val missingDir = testDirectoryPath.resolve("missingDir")
    DiagnosticsReportCleaner.cleanupFiles(missingDir)
    DiagnosticsReportCleaner.cleanupDirectories(missingDir, arrayOf(Regex("^reportDir.*")))
  }

  fun `test DiagnosticsReportCleanerTest handles files instead of directories`() {
    val reportFile = testDirectoryPath.resolve("report.txt")
    reportFile.createFile()

    DiagnosticsReportCleaner.cleanupFiles(reportFile)
    DiagnosticsReportCleaner.cleanupDirectories(reportFile, arrayOf(Regex("^reportDir.*")))
  }
}