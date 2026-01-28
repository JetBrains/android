/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.rendering

import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * Extension point to provide the Compose Tooling Target label.
 */
interface BazelComposeToolingProjectLabelProvider {
  fun isApplicable(project: Project): Boolean

  fun getComposeToolingLabel(project: Project): Label?

  fun isComposeProject(graph: BuildGraphData): Boolean

  companion object {
    val EP_NAME: ExtensionPointName<BazelComposeToolingProjectLabelProvider> =
      ExtensionPointName.create("com.google.idea.blaze.base.qsync.rendering.bazelComposeToolingProjectLabelProvider")

    fun getComposeToolingLabel(project: Project): Label? {
      return EP_NAME.extensionList.filter { it.isApplicable(project) }.firstNotNullOfOrNull { it.getComposeToolingLabel(project) }
    }

    fun isComposeProject(project: Project, graph: BuildGraphData): Boolean {
      return EP_NAME.extensionList.filter { it.isApplicable(project) }.any { it.isComposeProject(graph) }
    }
  }
}
