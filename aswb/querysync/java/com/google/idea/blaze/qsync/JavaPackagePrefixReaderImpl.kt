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

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.qsync.java.PackageReader
import com.google.idea.blaze.qsync.query.PackageSet
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.measureTimedValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class JavaPackagePrefixReaderImpl @JvmOverloads constructor(
  private val workspaceRoot: Path,
  private val packageReader: PackageReader,
  private val parallelPackageReader: PackageReader.ParallelReader,
  private val fileExistenceCheck: (Path) -> Boolean = { defaultFileExistenceCheck(workspaceRoot, it) },
) : JavaPackagePrefixReader {

  // TODO(b/449955674): Colocate file existence check and file reading by changing the signature of
  //  readPackage to take an InputStream instead of a Path.

  override suspend fun readPrefixes(
    context: Context<*>, packages: PackageSet, sourceFiles: Collection<Path>,
  ): Map<Path, String> = coroutineScope {

    val filesByPath = sourceFiles.groupBy { it.parent }
    // A map from directory to the candidate chosen to represent that directory.
    // For each directory, we select the lexicographically first file that actually exists.
    val candidates: Map<Path, Path> = filesByPath.entries.map { (dir, filesInDir) ->
      async {
        try {
          // Find the lexicographically smallest file that exists in the directory.
          sequence {
              val queue = java.util.PriorityQueue<Path>(Comparator.comparing { it.fileName.toString() })
              queue.addAll(filesInDir)
              while (queue.isNotEmpty()) {
                yield(queue.poll())
              }
            }
            .filter {
              try {
                fileExistenceCheck(it)
              } catch (e: Exception) {
                context.output(
                  PrintOutput.log("Warning: File existence check failed for $it: ${e.message}")
                )
                false // Treat as non-existent on error
              }
            }
            .firstOrNull()
            ?.let { chosenCandidate -> dir to chosenCandidate }
        } catch (e: Exception) {
          context.output(PrintOutput.log("Warning: Error processing directory $dir: ${e.message}"))
          null // Skip this directory on error
        }
      }
    }.awaitAll().filterNotNull().toMap()

    // Filter the files that are top level files only.
    val chosenFiles = candidates.values.filter { file -> isTopLevel(packages, candidates, file) }
    val (allPackages, elapsed) = measureTimedValue {
      parallelPackageReader.readPackages(context, packageReader, chosenFiles)
    }
    context.output(
      PrintOutput.log(
        "%-10d Java files read (%d ms)", chosenFiles.size, elapsed.inWholeMilliseconds))
    allPackages.entries
      .groupBy({ it.key.parent }, { it.value })
      .mapNotNull { (parent, pkgs) ->
        if (pkgs.size > 1) {
          context.output(
            PrintOutput.log(
              "Warning: Multiple package prefixes found for directory $parent: $pkgs. Skipping."
            )
          )
          null
        } else {
          parent to pkgs.single()
        }
      }
      .toMap()
  }

  /**
   * Checks if a file is a "top-level" file for package prefix purposes.
   * A file is considered top-level if its directory is part of the [packages] set,
   * and no parent directory within the [packages] set has a different representative file.
   * This helps in identifying the highest level directory in a package hierarchy that contains sources.
   */
  private fun isTopLevel(packages: PackageSet, candidates: Map<Path, Path>, file: Path): Boolean {
    var dir = file.parent
    while (dir != null) {
      val existing = candidates[dir]
      // If a parent directory has a different representative file, this one is not top-level.
      if (existing != null && existing != file) {
        return false
      }
      // If we reach a directory that is in the package set, it's top-level.
      if (packages.contains(dir)) {
        return true
      }
      dir = dir.parent
    }
    // After checking all non-null parents, check if the root itself is in the PackageSet.
    return packages.contains(Path.of(""))
  }

  companion object {
    private fun defaultFileExistenceCheck(workspaceRoot: Path, path: Path): Boolean {
      return Files.isRegularFile(workspaceRoot.resolve(path))
    }
  }
}