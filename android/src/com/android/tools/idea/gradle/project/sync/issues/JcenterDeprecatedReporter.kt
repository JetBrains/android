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

import com.android.ide.common.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.project.sync.hyperlink.RemoveJcenterHyperlink
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Process SyncIssues with type [IdeSyncIssue.TYPE_JCENTER_IS_DEPRECATED]. It will add [RemoveJcenterHyperlink] when the project is
 * initialized and the quickfix can be applied.
 */
class JcenterDeprecatedReporter: SimpleDeduplicatingSyncIssueReporter() {
  public override fun getSupportedIssueType(): Int {
    return IdeSyncIssue.TYPE_JCENTER_IS_DEPRECATED
  }

  override fun shouldIncludeModuleLinks(): Boolean = false

  // All issues of this type should be grouped together
  override fun getDeduplicationKey(issue: IdeSyncIssue): Any = supportedIssueType

  override fun getCustomLinks(project: Project,
                              syncIssues: MutableList<IdeSyncIssue>,
                              affectedModules: MutableList<Module>,
                              buildFileMap: MutableMap<Module, VirtualFile>): MutableList<NotificationHyperlink> {
    return createQuickFixes(project, affectedModules, RemoveJcenterHyperlink::canBeApplied)
  }

  @VisibleForTesting
  fun createQuickFixes(project: Project,
                       affectedModules: MutableList<Module>,
                       canBeApplied: (project: Project, affectedModules: MutableList<Module>) -> Boolean
  ): MutableList<NotificationHyperlink> {
    val quickFixes : ArrayList<NotificationHyperlink> = ArrayList()
    if (project.isInitialized && canBeApplied(project, affectedModules)) {
      quickFixes.add(RemoveJcenterHyperlink(project, affectedModules))
    }
    return quickFixes
  }
}