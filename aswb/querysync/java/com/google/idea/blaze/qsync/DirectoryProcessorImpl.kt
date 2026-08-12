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
import com.google.idea.blaze.traverser.DirectoryContents
import com.google.idea.blaze.traverser.DirectoryProcessor
import com.google.idea.blaze.traverser.FileEntry
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * This factory function provides an implementation of the [DirectoryProcessor] interface. Its purpose is to list the files and
 * subdirectories within a single directory, to be used for workspace traversal, before delegating the raw results to a callback.
 */
fun directoryProcessor(
  context: Context<*>,
  processContents: (rootDir: Path, currentDir: Path, contents: DirectoryContents) -> DirectoryContents?,
): DirectoryProcessor = DirectoryProcessor { rootDir, currentDir ->
  val files = mutableListOf<FileEntry>()
  val subDirs = mutableListOf<Path>()
  try {
    Files.newDirectoryStream(currentDir).use { stream ->
      for (child in stream) {
        try {
          val attrs = Files.readAttributes(child, BasicFileAttributes::class.java)
          if (attrs.isRegularFile) {
            files.add(FileEntry(child, attrs.lastModifiedTime().toMillis()))
          } else if (attrs.isDirectory) {
            subDirs.add(child)
          }
        } catch (e: IOException) {
          // Ignore files/directories we cannot read attributes for
        }
      }
    }
  } catch (e: IOException) {
    context.output(PrintOutput.log("Error reading directory $currentDir: ${e.message}"))
    return@DirectoryProcessor null
  }
  processContents(rootDir, currentDir, DirectoryContents(files, subDirs))
}
