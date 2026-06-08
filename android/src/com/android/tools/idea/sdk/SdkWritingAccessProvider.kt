/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.WritingAccessProvider
import com.intellij.util.SlowOperations
import java.util.concurrent.Callable

/** Marks Android SDK sources as read-only to prevent accidental edits. */
class SdkWritingAccessProvider(private val project: Project) : WritingAccessProvider() {

  override fun requestWriting(files: Collection<VirtualFile>): Collection<VirtualFile> {
    return files.filter(::isInAndroidSdk)
  }

  override fun isPotentiallyWritable(file: VirtualFile): Boolean {
    return !isInAndroidSdk(file)
  }

  // JetBrains patch: ProjectFileIndex is backend API in a shared module. The Android plugin is not split into frontend and backend modules, so there is no backend module to move this into.
  @Suppress("SplitModeApiUsage")
  private fun isInAndroidSdk(file: VirtualFile): Boolean {
    val computation = {
      // Optimization: avoid querying isInAndroidSdk() in the common case where the file is within project sources.
      !ProjectFileIndex.getInstance(project).isInContent(file) && AndroidSdks.getInstance().isInAndroidSdk(project, file)
    }
    return SlowOperations.knownIssue("b/322462245").use {
      if (ApplicationManager.getApplication().isDispatchThread) {
        // ReadAction.nonBlocking on the UI thread will throw since it is not meant to be used in that way so, in this case, we just
        // run a regular blocking read action.
        ReadAction.computeBlocking<Boolean, Exception> { computation() }
      } else {
        ReadAction.nonBlocking(Callable { computation() }).expireWhen { project.isDisposed }.executeSynchronously()
      }
    }
  }
}
