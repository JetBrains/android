/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.tools.idea.gradle.project.sync.hyperlink.AddComposeCompilerGradlePluginHyperlink
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Handles the sync issue when the Compose Compiler Gradle plugin is not applied.
 */
class MissingComposeCompilerGradlePluginReporter : SimpleDeduplicatingSyncIssueReporter() {
  public override fun getSupportedIssueType() = IdeSyncIssue.TYPE_MISSING_COMPOSE_COMPILER_GRADLE_PLUGIN

  public override fun getCustomLinks(
    project: Project,
    syncIssues: List<IdeSyncIssue>,
    affectedModules: List<Module>,
    buildFileMap: Map<Module, VirtualFile>
  ): List<SyncIssueNotificationHyperlink> {
    // The data field is the kotlin version
    val kotlinVersions = syncIssues.mapNotNull { it.data }.distinct()
    // Don't attempt quickfix if there are different kotlin versions for some reason
    if (affectedModules.isEmpty() || kotlinVersions.size != 1) {
      return emptyList()
    }
    return listOf(AddComposeCompilerGradlePluginHyperlink(project, affectedModules, kotlinVersions[0]))
  }
}
