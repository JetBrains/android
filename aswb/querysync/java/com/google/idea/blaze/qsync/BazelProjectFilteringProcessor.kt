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

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.traverser.DirectoryContents
import java.nio.file.Path

private val WORKSPACE_FILE_NAMES = setOf("MODULE.bazel", "WORKSPACE", "WORKSPACE.bazel")

fun bazelProjectFilteringProcessor(
  workspaceRoot: Path,
  excludeAbsolute: Set<Path>,
  context: Context<*>,
  processContents: (rootDir: Path, currentDir: Path, contents: DirectoryContents) -> DirectoryContents?,
): (rootDir: Path, currentDir: Path, contents: DirectoryContents) -> DirectoryContents? = { rootDir, currentDir, contents ->
  if (excludeAbsolute.any { currentDir.startsWith(it) }) {
    null
  } else if (contents.files.any { it.fileName.toString() in WORKSPACE_FILE_NAMES }) {
    context.output(PrintOutput.log("Skipping nested workspace at $currentDir"))
    null
  } else {
    val filtered = contents.copy(subDirectories = contents.subDirectories.filter { child -> excludeAbsolute.none { child.startsWith(it) } })
    processContents(rootDir, currentDir, filtered)
  }
}
