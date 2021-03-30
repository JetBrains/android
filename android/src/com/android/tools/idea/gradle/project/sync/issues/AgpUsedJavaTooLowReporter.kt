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
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenGradleSettingsHyperlink
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class AgpUsedJavaTooLowReporter: SimpleDeduplicatingSyncIssueReporter() {
  public override fun getSupportedIssueType(): Int {
    return IdeSyncIssue.TYPE_AGP_USED_JAVA_VERSION_TOO_LOW
  }

  override fun shouldIncludeModuleLinks(): Boolean = false

  // All issues of this type should be grouped together
  override fun getDeduplicationKey(issue: IdeSyncIssue): Any = supportedIssueType

  override fun getCustomLinks(project: Project,
                              syncIssues: MutableList<IdeSyncIssue>,
                              affectedModules: MutableList<Module>,
                              buildFileMap: MutableMap<Module, VirtualFile>): MutableList<NotificationHyperlink> {
    return createQuickFixes()
  }

  @VisibleForTesting
  fun createQuickFixes(): MutableList<NotificationHyperlink> {
    val quickFixes: ArrayList<NotificationHyperlink> = ArrayList()
    quickFixes.add(OpenGradleSettingsHyperlink())
    return quickFixes
  }
}