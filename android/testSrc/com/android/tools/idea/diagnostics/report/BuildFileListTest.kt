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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.createFile
import junit.framework.TestCase
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectory

class BuildFileListTest : TestCase() {
  lateinit var testDirectoryPath: Path

  override fun setUp() {
    super.setUp()
    testDirectoryPath = FileUtil.createTempDirectory("BuildFileListTest", null).toPath()
  }

  override fun tearDown() {
    FileUtils.deleteRecursivelyIfExists(testDirectoryPath.toFile())
    super.tearDown()
  }

  fun `test DiagnosticSummaryFileProvider buildFileList generates the correct set of files`() {
    val dir1 = testDirectoryPath.resolve("dir1")
    dir1.createDirectory()

    val dir2 = testDirectoryPath.resolve("dir2")
    dir2.createDirectory()

    val dir3 = testDirectoryPath.resolve("dir3")
    dir3.createDirectory()

    val source1 = dir1.resolve("file1.txt")
    source1.createFile()

    val source2 = dir1.resolve("file2.txt")
    source2.createFile()

    val source3 = dir1.resolve("file3.txt")
    // deliberately not create source3 to test missing files

    val dest1 = Paths.get("file1.txt")
    val dest2 = Paths.get("file2.txt")
    val dest3 = Paths.get("file3.txt")

    val providers = listOf(TestFileProvider("Provider1", source1, dest1),
                           TestFileProvider("Provider2", source2, dest2),
                           TestFileProvider("Provider3", source3, dest3))

    val fileList = DiagnosticsSummaryFileProvider.buildFileList(null, providers)

    assertThat(fileList.size).isEqualTo(2)

    assertThat(fileList[0].source).isEqualTo(source1)
    assertThat(fileList[0].destination).isEqualTo(Paths.get("Provider1").resolve(dest1))

    assertThat(fileList[1].source).isEqualTo(source2)
    assertThat(fileList[1].destination).isEqualTo(Paths.get("Provider2").resolve(dest2))
  }

  private class TestFileProvider(override val name: String, private val source: Path, private val destination: Path) : DiagnosticsSummaryFileProvider {
    override fun getFiles(project: Project?): List<FileInfo> {
      return listOf(FileInfo(source, destination))
    }
  }
}