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
package com.android.tools.idea.diagnostics.report

import com.android.utils.FileUtils
import com.google.common.truth.Truth
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.createFile
import junit.framework.TestCase
import java.nio.file.Path
import kotlin.io.path.createDirectory

class DirectoryBasedFileProviderTest : TestCase() {
  lateinit var testDirectoryPath: Path

  override fun setUp() {
    super.setUp()
    testDirectoryPath = FileUtil.createTempDirectory("DirectoryBasedFileProviderTest", null).toPath()
  }

  override fun tearDown() {
    FileUtils.deleteRecursivelyIfExists(testDirectoryPath.toFile())
    super.tearDown()
  }

  fun `test DirectoryBasedFileProvider resolves the correct files`() {
    val logDir = testDirectoryPath.resolve("logPath")
    logDir.createDirectory()

    val matchingDirectory = logDir.resolve("MatchingDirectory")
    matchingDirectory.createDirectory()

    val matchingFile = matchingDirectory.resolve("MatchingFile.txt")
    matchingFile.createFile()

    val otherDirectory = logDir.resolve("OtherDirectory")
    otherDirectory.createDirectory()

    val otherFile = otherDirectory.resolve("OtherFile.txt")
    otherFile.createFile()

    val logFile = logDir.resolve("idea.log")
    logFile.createFile()

    val pathProvider = PathProvider(logDir.toString(), null, null, null)
    val regex = Regex("^Matching.*")
    val directoryBasedFileProvider = DirectoryBasedFileProvider("Test", regex, pathProvider)

    val fileInfo = directoryBasedFileProvider.getFiles(null)

    Truth.assertThat(fileInfo.size).isEqualTo(1)

    Truth.assertThat(fileInfo[0].source).isEqualTo(matchingFile)
    Truth.assertThat(fileInfo[0].destination).isEqualTo(logDir.relativize(matchingFile))
  }
}