/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.assetstudio

import com.android.testutils.filesystemdiff.CreateDirectoryAction
import com.android.testutils.filesystemdiff.CreateFileAction
import com.android.testutils.filesystemdiff.Script
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Returns a list of created files according to a [script].
 * The method iterates through the [script.actions], selects created files and returns a list of their paths (relative to [root]).
 *
 * @param root directory to look into
 * @param script a [Script] with [CreateFileAction]s and [CreateDirectoryAction]
 * @param filter optional predicate for filtering of the returned files
 */
fun getNewFiles(root: Path, script: Script, filter: (Path) -> Boolean = { true }): List<String> {
  val newFiles = mutableListOf<String>()

  fun addRelativePathConditionally(pathToAdd: Path) {
    if (filter(pathToAdd)) {
      newFiles.add(root.relativize(pathToAdd).toString().replace('\\', '/'))
    }
  }

  for (action in script.actions) {
    if (action is CreateFileAction) {
      addRelativePathConditionally(action.sourceEntry.path)
    }
    if (action is CreateDirectoryAction) {
      try {
        Files.walk(action.sourceEntry.path).use { stream ->
          stream.filter { Files.isRegularFile(it) }.forEach(::addRelativePathConditionally)
        }
      }
      catch (ex: IOException) {
        throw RuntimeException(ex)
      }
    }
  }
  return newFiles
}
