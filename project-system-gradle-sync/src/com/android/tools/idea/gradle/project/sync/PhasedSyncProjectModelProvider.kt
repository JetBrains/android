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
@file:Suppress("UnstableApiUsage")

package com.android.tools.idea.gradle.project.sync

import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.Versions
import com.android.ide.common.repository.AgpVersion
import com.android.ide.gradle.model.GradlePluginModel
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider


class PhasedSyncProjectModelProvider : ProjectImportModelProvider {
   /**
    * This is just indicating which phase the provider will  on. To match the names it can technically run on
    * [GradleModelFetchPhase.PROJECT_MODEL_PHASE] but we populate source sets with this information, so it's kept in the source set phase.
    * The source of the clash is the mismatch between the platform models and our models. Platform models for the projects don't have source
    * set information whereas Android models do, so some sort of inconsistency will always be there with the current definition of phases.
   */
  override fun getPhase() =  GradleModelFetchPhase.PROJECT_SOURCE_SET_PHASE

  override fun populateModels(controller: BuildController,
                              buildModels: MutableCollection<out GradleBuild>,
                              modelConsumer: ProjectImportModelProvider.GradleModelConsumer) {
    controller.run(buildModels.flatMap { buildModel ->
      buildModel.projects.mapNotNull { gradleProject ->
        BuildAction {
          if (controller.findModel(gradleProject, GradlePluginModel::class.java)?.hasKotlinMultiPlatform() == true) {
            // Kotlin multiplatform projects are not supported for phased sync yet.
            return@BuildAction null
          }
          gradleProject to AndroidProjectData(
            controller.findModel(gradleProject, Versions::class.java)
              // TODO(b/384022658): Reconsider this check if we implement a cache between model providers to avoid fetching the models twice
              ?.takeIf { it.isAtLeastAgp8() } ?: return@BuildAction null,
            controller.findModel(gradleProject, BasicAndroidProject::class.java)!!,
            controller.findModel(gradleProject, AndroidProject::class.java)!!,
            controller.findModel(gradleProject, AndroidDsl::class.java)!!
          )
        }
      }
    }).filterNotNull()
      .forEach { (gradleProject, data) ->
        modelConsumer.consumeProjectModel(gradleProject, data.versions, Versions::class.java)
        modelConsumer.consumeProjectModel(gradleProject, data.basicAndroidProject, BasicAndroidProject::class.java)
        modelConsumer.consumeProjectModel(gradleProject, data.androidProject, AndroidProject::class.java)
        modelConsumer.consumeProjectModel(gradleProject, data.androidDsl, AndroidDsl::class.java)
      }
  }
}

private fun Versions.isAtLeastAgp8() = AgpVersion.parse(agp).isAtLeast(8, 0, 0)

private data class AndroidProjectData(
  val versions: Versions,
  val basicAndroidProject: BasicAndroidProject,
  val androidProject: AndroidProject,
  val androidDsl: AndroidDsl
)
