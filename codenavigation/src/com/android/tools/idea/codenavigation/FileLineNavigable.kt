/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.codenavigation

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.pom.Navigatable

/** Use the file name and line number to navigate to a local file. */
internal class FileLineNavigable(private val project: Project) : NavSource {
  // JetBrains patch: ProjectFileIndex is backend API in a shared module. The Android plugin is not split into frontend and backend modules, so there is no backend module to move this into.
  @Suppress("SplitModeApiUsage")
  override fun lookUp(location: CodeLocation, arch: String?): Navigatable? {
    if (location.fileName.isNullOrEmpty() || location.lineNumber == CodeLocation.INVALID_LINE_NUMBER) {
      return null
    }

    // CodeLocation.fileName may originate from an untrusted imported capture (.hprof STACK_FRAME,
    // simpleperf File table, on-device inspector payload). Reject network paths outright and only
    // navigate to files that are part of this project's content or libraries; everything else falls through to
    // the PSI-index-based NavSources, which is the correct behavior for imported captures anyway.
    if (OSAgnosticPathUtil.isUncPath(FileUtil.toSystemDependentName(location.fileName!!))) {
      return null
    }

    // There is no need to check `sourceFile.exists()` since `findFileByPath()` will return null if
    // the file is not found. `exists()` could be false if the file was deleted, but that is not
    // likely since we are using the file immediately after looking it up.
    val sourceFile = StandardFileSystems.local().findFileByPath(location.fileName!!) ?: return null

    if (!ProjectFileIndex.getInstance(project).isInProject(sourceFile)) {
      return null
    }

    return OpenFileDescriptor(project, sourceFile, location.lineNumber, 0)
  }
}
