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

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.WritingAccessProvider
import com.intellij.util.SlowOperations

/** Marks Android SDK sources as read-only to prevent accidental edits. */
class SdkWritingAccessProvider(private val project: Project) : WritingAccessProvider() {

  override fun requestWriting(files: Collection<VirtualFile>): Collection<VirtualFile> {
    return files.filter(::isInAndroidSdk)
  }

  override fun isPotentiallyWritable(file: VirtualFile): Boolean {
    return !isInAndroidSdk(file)
  }

  private fun isInAndroidSdk(file: VirtualFile): Boolean {
    return SlowOperations.allowSlowOperations(ThrowableComputable {
      AndroidSdks.getInstance().isInAndroidSdk(project, file)
    })
  }
}
