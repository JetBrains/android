/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync

import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.common.NoopContext
import com.google.idea.blaze.traverser.DirectoryContents
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DirectoryProcessorImplTest {

  @get:Rule val temporaryFolder = TemporaryFolder()

  private lateinit var workspaceRoot: Path
  private val context = NoopContext()

  @Before
  fun setUp() {
    workspaceRoot = temporaryFolder.root.toPath()
  }

  private fun createFile(projectRelativePath: String, content: String = "") {
    val absPath = workspaceRoot.resolve(projectRelativePath)
    absPath.parent.createDirectories()
    absPath.writeText(content)
  }

  private fun createDirectory(projectRelativePath: String) {
    val absPath = workspaceRoot.resolve(projectRelativePath)
    absPath.createDirectories()
  }

  @Test
  fun testProcessDirectory_emptyDir() {
    runBlocking {
      createDirectory("empty")
      val processor = DirectoryProcessorImpl(context, emptySet())
      val result = processor.processDirectory(workspaceRoot.resolve("empty"))

      assertThat(result)
        .isEqualTo(
          DirectoryContents(emptyList(), emptyList())
        )
    }
  }

  @Test
  fun testProcessDirectory_withFilesAndDirs() {
    runBlocking {
      createFile("dir1/file1.txt")
      createFile("dir1/file2.txt")
      createDirectory("dir1/subdir1")
      createDirectory("dir1/subdir2")

      val processor = DirectoryProcessorImpl(context, emptySet())
      val result = processor.processDirectory(workspaceRoot.resolve("dir1"))

      assertThat(result?.files)
        .containsExactly(
          workspaceRoot.resolve("dir1/file1.txt"),
          workspaceRoot.resolve("dir1/file2.txt"),
        )
      assertThat(result?.subDirectories)
        .containsExactly(
          workspaceRoot.resolve("dir1/subdir1"),
          workspaceRoot.resolve("dir1/subdir2"),
        )
    }
  }

  @Test
  fun testProcessDirectory_excludes() {
    runBlocking {
      createFile("dir1/file1.txt")
      createDirectory("dir1/excluded")
      createFile("dir1/excluded/file2.txt")

      val excludes = setOf(workspaceRoot.resolve("dir1/excluded"))
      val processor = DirectoryProcessorImpl(context, excludes)

      // Processing a directory within the excluded set
      val excludedResult = processor.processDirectory(workspaceRoot.resolve("dir1/excluded"))
      assertThat(excludedResult).isNull()

      // Processing the parent, the excluded subdir should not be listed in subDirectories
      val parentResult = processor.processDirectory(workspaceRoot.resolve("dir1"))
      assertThat(parentResult?.files)
        .containsExactly(workspaceRoot.resolve("dir1/file1.txt"))
      assertThat(parentResult?.subDirectories).isEmpty()
    }
  }

  @Test
  fun testProcessDirectory_nestedWorkspace_moduleBazel() {
    runBlocking {
      createFile("dir1/file1.txt")
      createDirectory("dir1/nested")
      createFile("dir1/nested/MODULE.bazel")
      createFile("dir1/nested/file2.txt")

      val processor = DirectoryProcessorImpl(context, emptySet())
      val result = processor.processDirectory(workspaceRoot.resolve("dir1/nested"))
      assertThat(result).isNull()
    }
  }

  @Test
  fun testProcessDirectory_nestedWorkspace_workspaceFile() {
    runBlocking {
      createFile("dir1/file1.txt")
      createDirectory("dir1/nested")
      createFile("dir1/nested/WORKSPACE")
      createFile("dir1/nested/file2.txt")

      val processor = DirectoryProcessorImpl(context, emptySet())
      val result = processor.processDirectory(workspaceRoot.resolve("dir1/nested"))
      assertThat(result).isNull()
    }
  }

  @Test
  fun testProcessDirectory_unreadableDir() {
    runBlocking {
      val unreadableDir = workspaceRoot.resolve("unreadable")
      unreadableDir.createDirectories()
      createFile("unreadable/secret.txt")

      val success = unreadableDir.toFile().setReadable(false)
      assertThat(success).isTrue()

      try {
        val processor = DirectoryProcessorImpl(context, emptySet())
        val result = processor.processDirectory(unreadableDir)
        assertThat(result).isNull() // Expect null because the directory is not readable
      } finally {
        // Restore readability for cleanup
        unreadableDir.toFile().setReadable(true)
      }
    }
  }
}