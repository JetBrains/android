/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.traverser.DirectoryContents
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
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
class BazelProjectFilteringProcessorTest {

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
  fun testProcessContents_excludes() {
    createFile("dir1/file1.txt")
    createDirectory("dir1/excluded")
    createDirectory("dir1/validChild")

    val excludes = setOf(workspaceRoot.resolve("dir1/excluded"))
    var outerOutput: DirectoryContents? = null

    val processor =
      directoryProcessor(
        context,
        processContents =
          bazelProjectFilteringProcessor(workspaceRoot, excludes, context) { _, _, c ->
            outerOutput = c
            c
          },
      )

    processor.processDirectory(workspaceRoot, workspaceRoot.resolve("dir1"))

    assertThat(outerOutput?.files).containsExactly(workspaceRoot.resolve("dir1/file1.txt"))
    assertThat(outerOutput?.subDirectories).containsExactly(workspaceRoot.resolve("dir1/validChild"))
  }

  @Test
  fun testProcessContents_currentDirExcluded() {
    createDirectory("dir1/excluded")
    val excludes = setOf(workspaceRoot.resolve("dir1/excluded"))

    val processor =
      directoryProcessor(context, processContents = bazelProjectFilteringProcessor(workspaceRoot, excludes, context) { _, _, c -> c })

    val result = processor.processDirectory(workspaceRoot, workspaceRoot.resolve("dir1/excluded"))

    assertThat(result).isNull()
  }

  @Test
  fun testProcessContents_nestedWorkspace_moduleBazel() {
    createFile("dir1/nested/MODULE.bazel")
    createFile("dir1/nested/file2.txt")

    val processor =
      directoryProcessor(context, processContents = bazelProjectFilteringProcessor(workspaceRoot, emptySet(), context) { _, _, c -> c })

    val result = processor.processDirectory(workspaceRoot, workspaceRoot.resolve("dir1/nested"))

    assertThat(result).isNull()
  }

  @Test
  fun testProcessContents_nestedWorkspace_workspaceFile() {
    createFile("dir1/nested/WORKSPACE")
    createFile("dir1/nested/file2.txt")

    val processor =
      directoryProcessor(context, processContents = bazelProjectFilteringProcessor(workspaceRoot, emptySet(), context) { _, _, c -> c })

    val result = processor.processDirectory(workspaceRoot, workspaceRoot.resolve("dir1/nested"))

    assertThat(result).isNull()
  }

  @Test
  fun testTraverseProjectDirectories_emptyIncludes() {
    runBlocking {
      val def = ProjectDefinition.EMPTY.copy(projectIncludes = setOf(Path.of("nonexistent")))
      var callbackCalled = false
      traverseProjectDirectories(context, workspaceRoot, def) { _, _, c ->
        callbackCalled = true
        c
      }
      assertThat(callbackCalled).isFalse()
    }
  }

  @Test
  fun testTraverseProjectDirectories_traversesAndFilters() {
    runBlocking {
      createFile("dir1/file1.txt")
      createDirectory("dir1/excluded")
      createDirectory("dir1/validChild")
      createFile("dir1/nested/MODULE.bazel")
      createFile("dir1/nested/file2.txt")

      val def = ProjectDefinition.EMPTY.copy(projectIncludes = setOf(Path.of("dir1")), projectExcludes = setOf(Path.of("dir1/excluded")))
      val visitedDirs = ConcurrentHashMap.newKeySet<Path>()
      val visitedFiles = ConcurrentHashMap.newKeySet<Path>()

      traverseProjectDirectories(context, workspaceRoot, def) { _, currentDir, contents ->
        visitedDirs.add(currentDir)
        visitedFiles.addAll(contents.files)
        contents
      }

      assertThat(visitedDirs).containsExactly(workspaceRoot.resolve("dir1"), workspaceRoot.resolve("dir1/validChild"))
      assertThat(visitedFiles).containsExactly(workspaceRoot.resolve("dir1/file1.txt"))
    }
  }
}
