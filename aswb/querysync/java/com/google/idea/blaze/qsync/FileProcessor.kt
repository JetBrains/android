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

import com.google.idea.blaze.qsync.project.FileExtensions
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import com.google.idea.blaze.traverser.FileEntry
import java.nio.file.Path
import kotlin.io.path.extension

/** Result of processing a single file. */
sealed class FileProcessResult {
  data class Package(val packagePath: Path, val buildFileLastModifiedTimeMs: Long) : FileProcessResult()

  data class SourceFile(val relativePath: Path, val language: QuerySyncLanguage?) : FileProcessResult()

  object Ignored : FileProcessResult()
}

/**
 * This class is responsible for analyzing individual files encountered during workspace traversal and determining their role within the
 * project structure. It helps in categorizing files into packages, source files of different languages, or files to be ignored.
 */
class FileProcessor(private val workspaceRoot: Path, private val fileExtensions: FileExtensions) {

  private companion object {
    val BUILD_FILE_NAMES: Set<String> = setOf("BUILD", "BUILD.bazel")
  }

  fun processRegularFile(fileEntry: FileEntry, currentDir: Path): FileProcessResult {
    val fileName = fileEntry.path.fileName.toString()
    if (fileName in BUILD_FILE_NAMES) {
      return FileProcessResult.Package(workspaceRoot.relativize(currentDir), fileEntry.lastModifiedTimeMs)
    }

    val extension = fileEntry.path.extension
    if (extension.isEmpty()) {
      return FileProcessResult.Ignored
    }

    val relativePath = workspaceRoot.relativize(fileEntry.path)
    return when (extension) {
      in fileExtensions.jvmExtensions -> {
        FileProcessResult.SourceFile(relativePath, QuerySyncLanguage.JVM)
      }
      in fileExtensions.ccSourceExtensions -> {
        FileProcessResult.SourceFile(relativePath, QuerySyncLanguage.CC)
      }
      in fileExtensions.protoSourceExtensions -> {
        FileProcessResult.SourceFile(relativePath, null)
      }
      else -> FileProcessResult.Ignored
    }
  }
}
