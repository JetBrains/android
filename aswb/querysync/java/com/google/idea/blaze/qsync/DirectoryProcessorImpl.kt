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
import com.google.idea.blaze.qsync.dispatchers.QuerySyncDispatchers
import com.google.idea.blaze.traverser.DirectoryContents
import com.google.idea.blaze.traverser.DirectoryProcessor
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.withContext

/**
 * This class is an implementation of the [DirectoryProcessor] interface. Its purpose is to list
 * the files and subdirectories within a single directory, to be used for workspace traversal.
 */
class DirectoryProcessorImpl(
  private val context: Context<*>,
  private val excludeAbsolute: Set<Path>,
) : DirectoryProcessor {

  private companion object {
    val WORKSPACE_FILE_NAMES = setOf("MODULE.bazel", "WORKSPACE", "WORKSPACE.bazel")
  }

  override fun processDirectory(currentDir: Path): DirectoryContents? {
    if (excludeAbsolute.any { currentDir.startsWith(it) }) {
      return null
    }

    if (isNestedWorkspace(currentDir)) {
      return null
    }

    val files = mutableListOf<Path>()
    val subDirs = mutableListOf<Path>()
    try {
      Files.newDirectoryStream(currentDir).use { stream ->
        for (child in stream) {
          if (Files.isRegularFile(child)) {
            files.add(child)
          } else if (Files.isDirectory(child)) {
            if (excludeAbsolute.none { child.startsWith(it) }) {
              subDirs.add(child)
            }
          }
        }
      }
    } catch (e: IOException) {
      context.output(PrintOutput.log("Error reading directory $currentDir: ${e.message}"))
      return null
    }
    return DirectoryContents(files, subDirs)
  }

  private fun isNestedWorkspace(path: Path): Boolean {
    val found = WORKSPACE_FILE_NAMES.any { Files.exists(path.resolve(it)) }
    if (found) {
      context.output(PrintOutput.log("Skipping nested workspace at $path"))
    }
    return found
  }
}
