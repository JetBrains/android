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

import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager
import com.google.idea.blaze.base.settings.BuildSystemName
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.intellij.openapi.project.Project
import kotlin.jvm.optionals.getOrNull

/**
 * Default implementation for logic that provides the Compose Tooling Target label.
 */
class DefaultBazelComposeToolingProjectLabelProvider : BazelComposeToolingProjectLabelProvider {
  override fun isApplicable(project: Project): Boolean {
    return BlazeImportSettingsManager.getInstance(project).importSettings?.buildSystem == BuildSystemName.Bazel
  }

  override fun getComposeToolingLabel(project: Project): Label? {
    if (BlazeImportSettingsManager.getInstance(project).importSettings?.buildSystem != BuildSystemName.Bazel) {
      return null
    }
    val graph = QuerySyncManager.getInstance(project).currentSnapshot.getOrNull()?.graph ?: return null
    return composeDeps(graph).firstOrNull()?.siblingWithName("androidx_compose_ui_ui_tooling")
  }

  override fun isComposeProject(graph: BuildGraphData): Boolean {
    return composeDeps(graph).any()
  }

  private fun isComposeUiLabel(label: Label): Boolean {
    return label.name.startsWith("androidx_compose_")
  }

  private fun composeDeps(graph: BuildGraphData): Sequence<Label> {
    return graph.allLoadedTargets().asSequence()
      .mapNotNull { graph.getProjectTarget(it) }
      .flatMap { it.deps() }
      .filter { isComposeUiLabel(it) }
  }
}
