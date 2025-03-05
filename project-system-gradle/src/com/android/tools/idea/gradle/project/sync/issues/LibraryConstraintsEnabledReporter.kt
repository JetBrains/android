/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.project.sync.hyperlink.DisableLibraryConstraintsHyperlink
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class LibraryConstraintsEnabledReporter: SimpleDeduplicatingSyncIssueReporter() {
  override fun getSupportedIssueType(): Int {
    return IdeSyncIssue.TYPE_LIBRARY_CONSTRAINTS_SHOULD_BE_DISABLED
  }

  override fun shouldIncludeModuleLinks(): Boolean = false

  override fun getDeduplicationKey(issue: IdeSyncIssue): Any = supportedIssueType

  override fun getCustomLinks(
    project: Project,
    syncIssues: MutableList<IdeSyncIssue>,
    affectedModules: MutableList<Module>,
    buildFileMap: MutableMap<Module, VirtualFile>
  ): List<SyncIssueNotificationHyperlink> = listOf(DisableLibraryConstraintsHyperlink())

}
