/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags
import com.android.builder.model.v2.models.ModelBuilderParameter
import com.android.builder.model.v2.models.VariantDependenciesAdjacencyList
import com.android.ide.gradle.model.GradlePropertiesModel
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import kotlin.collections.forEach
import kotlin.collections.orEmpty

class PhasedSyncDependencyModelProvider(val syncOptions: SyncActionOptions, val cachedData: ModelProviderCachedData) : ProjectImportModelProvider {
  override fun getPhase() =  GradleModelFetchPhase.PROJECT_SOURCE_SET_DEPENDENCY_PHASE

  override fun populateModels(controller: BuildController,
                              buildModels: MutableCollection<out GradleBuild>,
                              modelConsumer: ProjectImportModelProvider.GradleModelConsumer) {

    val actions = buildModels.flatMap { buildModel ->
      // For each build, build a map per-project to indicate whether it has inbound dependencies
      // This is used as an optimization when determining whether to skip library runtime classpaths
      val hasInboundDependencyByPath = mutableMapOf<String, Boolean>()
      buildModel.projects.forEach {
        cachedData.data[it]?.allOutgoingProjectDependencies.orEmpty().forEach { gradleProjectPath ->
          hasInboundDependencyByPath[gradleProjectPath] = true
        }
      }
      buildModel.projects
        .mapNotNull { gradleProject ->
          BuildAction {
            val data = cachedData.data[gradleProject] ?: return@BuildAction null
            val variantDependencies = controller.findModel(
              gradleProject,VariantDependenciesAdjacencyList::class.java, ModelBuilderParameter::class.java
            ) {
              it.variantName = data.selectedVariantName
              // If the studio flag for it is enabled, we don't fetch runtime classpath for libraries with inbound depenencis
              if(data.shouldSkipRuntimeClassPathForLibraries
                 && data.projectType == IdeAndroidProjectType.PROJECT_TYPE_LIBRARY
                 && hasInboundDependencyByPath[gradleProject.path] == true) {
                it.buildOnlyTestRuntimeClasspaths(
                  syncOptions.flags.studioFlagBuildRuntimeClasspathForLibraryUnitTests,
                  syncOptions.flags.studioFlagBuildRuntimeClasspathForLibraryScreenshotTests,
                  addAdditionalArtifactsInModel = syncOptions.flags.studioFlagMultiVariantAdditionalArtifactSupport
                )
              } else {
                it.buildAllRuntimeClasspaths(addAdditionalArtifactsInModel = syncOptions.flags.studioFlagMultiVariantAdditionalArtifactSupport)
              }
            } ?: return@BuildAction null
            gradleProject to variantDependencies
          }
        }
    }
    controller.run(actions).filterNotNull().forEach { (gradleProject, variantDependencies) ->
      modelConsumer.consumeProjectModel(gradleProject, variantDependencies, VariantDependenciesAdjacencyList::class.java)
    }
    cachedData.data.clear()
  }
}

