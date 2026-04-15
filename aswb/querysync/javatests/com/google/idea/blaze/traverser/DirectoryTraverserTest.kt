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
package com.google.idea.blaze.traverser

import com.google.common.truth.Truth.assertThat
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DirectoryTraverserTest {

  @Test
  fun testTraverseIncludedDirectories() {
    runBlocking {
      val processedDirs = ConcurrentHashMap.newKeySet<Path>()
      val structure =
        mapOf(
          "root" to listOf("a", "b"),
          "a" to listOf("c", "b"), // "b" is repeated
          "b" to listOf("d"),
          "c" to emptyList(),
          "d" to emptyList(),
        )

      val processor = DirectoryProcessor { _, currentDir ->
        processedDirs.add(currentDir)
        val subDirs = structure[currentDir.toString()]?.map { Path.of(it) } ?: emptyList()
        DirectoryContents(emptyList(), subDirs)
      }

      traverseIncludedDirectories(listOf(Path.of("root")), processor)

      assertThat(processedDirs).containsExactly(Path.of("root"), Path.of("a"), Path.of("b"), Path.of("c"), Path.of("d"))
    }
  }

  @Test
  fun testTraverseIncludedDirectoriesWithNestedIncludes() {
    runBlocking {
      val processedDirs = ConcurrentHashMap<Path, Path>() // Maps currentDir to its assigned rootDir
      val structure =
        mapOf("root" to listOf("root/a", "root/b"), "root/a" to listOf("root/a/c"), "root/a/c" to emptyList(), "root/b" to emptyList())

      val processor = DirectoryProcessor { rootDir, currentDir ->
        processedDirs[currentDir] = rootDir
        val subDirs = structure[currentDir.toString()]?.map { Path.of(it) } ?: emptyList()
        DirectoryContents(emptyList(), subDirs)
      }

      // Seed both 'root' and 'root/a'
      traverseIncludedDirectories(listOf(Path.of("root"), Path.of("root/a")), processor)

      // Files in 'root' (but not in 'root/a') should be assigned to 'root'
      assertThat(processedDirs[Path.of("root")]).isEqualTo(Path.of("root"))
      assertThat(processedDirs[Path.of("root/b")]).isEqualTo(Path.of("root"))

      // 'root/a' and its subdirectories should be assigned to 'root/a'
      // because 'root/a' was seeded first and should not be claimed by 'root'
      assertThat(processedDirs[Path.of("root/a")]).isEqualTo(Path.of("root/a"))
      assertThat(processedDirs[Path.of("root/a/c")]).isEqualTo(Path.of("root/a"))
    }
  }
}
