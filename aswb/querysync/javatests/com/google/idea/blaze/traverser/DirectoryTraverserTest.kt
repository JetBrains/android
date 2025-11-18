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

    val processor = DirectoryProcessor { currentDir ->
      processedDirs.add(currentDir)
      structure[currentDir.toString()]?.map { Path.of(it) } ?: emptyList()
    }

    traverseIncludedDirectories(listOf(Path.of("root")), processor)

    assertThat(processedDirs)
      .containsExactly(Path.of("root"), Path.of("a"), Path.of("b"), Path.of("c"), Path.of("d"))
    }
  }
}
