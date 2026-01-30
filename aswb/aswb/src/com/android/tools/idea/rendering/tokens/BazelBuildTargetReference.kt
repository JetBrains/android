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

package com.android.tools.idea.rendering.tokens

import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.run.deployment.liveedit.tokens.toPreferredLabel
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.common.Label
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.AlreadyDisposedException
import kotlin.jvm.optionals.getOrNull

@ConsistentCopyVisibility
internal data class BazelBuildTargetReference internal constructor(val module_: Module, val file: VirtualFile) : BuildTargetReference {
  fun getFileWorkspaceRelativePath() = WorkspaceRoot.virtualFilesToWorkspaceRelativePaths(project, listOf(file)).single()

  override val module
    get() = if (module_.isDisposed) throw AlreadyDisposedException("Already disposed: $module_") else module_

  override val moduleIfNotDisposed = if (module_.isDisposed) null else module_
}

internal fun BazelBuildTargetReference.toAllLabels(): Set<Label> {
  return QuerySyncManager.getInstance(project)
    .getTargetsToBuildByPaths(listOf(getFileWorkspaceRelativePath()))
    .flatMap { it.targets }
    .toSet()
}

internal fun BazelBuildTargetReference.toPreferredLabel(): Label? {
  val snapshot = QuerySyncManager.getInstance(project).currentSnapshot.getOrNull() ?: return null
  val builds = snapshot.artifactIndex.builtDepsMap()
  return QuerySyncManager.getInstance(project).getTargetsToBuildByPaths(listOf(getFileWorkspaceRelativePath())).toPreferredLabel() {
    builds.containsKey(it)
  }
}
