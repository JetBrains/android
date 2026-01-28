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
package com.google.idea.blaze.base.qsync

import com.google.idea.blaze.base.projectview.ProjectViewManager
import com.google.idea.blaze.base.projectview.section.sections.EnableCodeAnalysisOnSyncSection
import com.google.idea.common.experiments.BoolExperiment
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlin.jvm.optionals.getOrDefault

interface QuerySyncUserPreferences {
  val enableCodeAnalysisOnSync: Boolean
  val refreshQueryDataOnStartup: Boolean
  val experimentalBuildNativeTargetsFromAndroidTransitionPoint: Boolean
  val liveEditEnabled: Boolean
  val autoSyncComposeTooling: Boolean
}

private val skipRefreshQueryDataOnStartup = BoolExperiment("aswb.query.sync.skip.query.on.startup", true)
private val buildNativeTargetsFromAndroidTransitionPoint =
    BoolExperiment("aswb.query.sync.build.native.targets.from.android.transition.point", true)
private val liveEditSupportEnabled: BoolExperiment = BoolExperiment("aswb.live.edit.enabled", false)
private val autoSyncComposeToolingExperiment = BoolExperiment("aswb.query.sync.auto.sync.compose.tooling", true)


@Service(Service.Level.PROJECT)
class QuerySyncUserPreferencesProvider(private val project: Project) {
  val userPreferences: QuerySyncUserPreferences = object : QuerySyncUserPreferences {
    override val enableCodeAnalysisOnSync: Boolean
      get() =
        ProjectViewManager.getInstance(project).projectViewSet?.getScalarValue(EnableCodeAnalysisOnSyncSection.KEY)?.getOrDefault(false)
        ?: false

    override val refreshQueryDataOnStartup: Boolean
      get() = !skipRefreshQueryDataOnStartup.value

    override val experimentalBuildNativeTargetsFromAndroidTransitionPoint: Boolean
      get() = buildNativeTargetsFromAndroidTransitionPoint.value

    override val liveEditEnabled: Boolean
      get() = liveEditSupportEnabled.value

    override val autoSyncComposeTooling: Boolean
      get() = autoSyncComposeToolingExperiment.value
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): QuerySyncUserPreferencesProvider = project.service()
  }
}
