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
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.createFile
import junit.framework.TestCase
import java.nio.file.Path
import kotlin.io.path.createDirectory

class LogFileProviderTest : TestCase() {
  lateinit var testDirectoryPath: Path

  override fun setUp() {
    super.setUp()
    testDirectoryPath = FileUtil.createTempDirectory("LogFileProviderTest", null).toPath()
  }

  override fun tearDown() {
    FileUtils.deleteRecursivelyIfExists(testDirectoryPath.toFile())
    super.tearDown()
  }

  fun testLogFileProvider() {
    val logDir = testDirectoryPath.resolve("logPath")
    logDir.createDirectory()

    val logFile = logDir.resolve("idea.log")
    logFile.createFile()

    val vmOptionsFile = testDirectoryPath.resolve("studio.vmoptions")
    vmOptionsFile.createFile()

    val customOptionsDir = testDirectoryPath.resolve("customOptionsDir")
    customOptionsDir.createDirectory()

    val customOptionsFile = customOptionsDir.resolve(PathManager.PROPERTIES_FILE_NAME)
    customOptionsFile.createFile()

    val homeDir = testDirectoryPath.resolve("homeDir")
    homeDir.createDirectory()
    val jvmCrashFile = homeDir.resolve("java_error_in_STUDIO_123.log")
    jvmCrashFile.createFile()

    val pathProvider = PathProvider(logDir.toString(), vmOptionsFile, customOptionsDir.toString(), homeDir.toString())
    val logFileProvider = LogFileProvider(pathProvider)

    val fileInfo = logFileProvider.getFiles(null).sortedBy { it.source }

    assertThat(fileInfo.size).isEqualTo(4)

    assertThat(fileInfo[0].source).isEqualTo(customOptionsFile)
    assertThat(fileInfo[0].destination).isEqualTo(customOptionsDir.relativize(customOptionsFile))

    assertThat(fileInfo[1].source).isEqualTo(jvmCrashFile)
    assertThat(fileInfo[1].destination).isEqualTo(homeDir.relativize(jvmCrashFile))

    assertThat(fileInfo[2].source).isEqualTo(logFile)
    assertThat(fileInfo[2].destination).isEqualTo(logDir.relativize(logFile))

    assertThat(fileInfo[3].source).isEqualTo(vmOptionsFile)
    assertThat(fileInfo[3].destination).isEqualTo(testDirectoryPath.relativize(vmOptionsFile))
  }
}