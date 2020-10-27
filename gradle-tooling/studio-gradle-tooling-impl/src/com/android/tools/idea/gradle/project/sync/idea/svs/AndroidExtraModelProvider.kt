/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea.svs

import com.android.builder.model.AndroidProject
import com.android.builder.model.ModelBuilderParameter
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.ProjectSyncIssues
import com.android.builder.model.level2.GlobalLibraryMap
import com.android.ide.gradle.model.GradlePluginModel
import com.android.tools.idea.gradle.project.sync.SyncActionOptions
import com.android.tools.idea.gradle.project.sync.idea.UsedInBuildAction
import com.android.tools.idea.gradle.project.sync.idea.getAdditionalClassifierArtifactsModel
import org.gradle.tooling.BuildController
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.kotlin.kapt.idea.KaptGradleModel
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider

@UsedInBuildAction
class AndroidExtraModelProvider(private val syncActionOptions: SyncActionOptions) : ProjectImportModelProvider {
  override fun populateBuildModels(
    controller: BuildController,
    buildModel: GradleBuild,
    consumer: ProjectImportModelProvider.BuildModelConsumer) {
    populateAndroidModels(controller, buildModel, consumer)
    // Requesting ProjectSyncIssues must be performed last since all other model requests may produces addition issues.
    populateProjectSyncIssues(controller, buildModel, consumer)
  }

  override fun populateProjectModels(controller: BuildController,
                                     projectModel: Model,
                                     modelConsumer: ProjectImportModelProvider.ProjectModelConsumer) {
    controller.findModel(projectModel, GradlePluginModel::class.java)
      ?.also { pluginModel -> modelConsumer.consume(pluginModel, GradlePluginModel::class.java) }
    controller.findModel(projectModel, KaptGradleModel::class.java)
      ?.also { model -> modelConsumer.consume(model, KaptGradleModel::class.java) }
  }

  /**
   * Requests Android project models for the given [buildModel] and registers them with the [consumer]
   *
   * We do this by going through each module and query Gradle for the following models:
   *   1. Query for the AndroidProject for the module
   *   2. Query for the NativeAndroidProject (only if we also obtain an Android project)
   *   3. Query for the GlobalLibraryMap for the module (we ALWAYS do this regardless of the other two models)
   *   4. (Single Variant Sync only) Work out which variant for which models we need to request, and request them.
   *      See IdeaSelectedVariantChooser for more details.
   *
   * If single variant sync is enabled then [findParameterizedAndroidModel] will use Gradle parameterized model builder API
   * in order to stop Gradle from building the variant.
   * All of the requested models are registered back to the external project system via the
   * [ProjectImportModelProvider.BuildModelConsumer] callback.
   */
  private fun populateAndroidModels(
    controller: BuildController,
    buildModel: GradleBuild,
    consumer: ProjectImportModelProvider.BuildModelConsumer
  ) {
    val androidModules: MutableList<AndroidModule> = mutableListOf()
    buildModel.projects.forEach { gradleProject ->
      findParameterizedAndroidModel(controller, gradleProject, AndroidProject::class.java)?.also { androidProject ->
        consumer.consumeProjectModel(gradleProject, androidProject, AndroidProject::class.java)

        val nativeAndroidProject = findParameterizedAndroidModel(controller, gradleProject, NativeAndroidProject::class.java)?.also {
          consumer.consumeProjectModel(gradleProject, it, NativeAndroidProject::class.java)
        }

        androidModules.add(AndroidModule(gradleProject, androidProject, nativeAndroidProject))
      }
    }

    if (syncActionOptions.isSingleVariantSyncEnabled) {
      // This section is for Single Variant Sync specific models if we have reached here we should have already requested AndroidProjects
      // without any Variant information. Now we need to request that Variant information for the variants that we are interested in.
      // e.g the ones that should be selected by the IDE.
      chooseSelectedVariants(controller, androidModules, syncActionOptions)
      androidModules.forEach { module ->
        // Variants can be empty if single-variant sync is enabled but not supported for current module.
        if (module.variantGroup.variants.isNotEmpty()) {
          consumer.consumeProjectModel(module.gradleProject, module.variantGroup, VariantGroup::class.java)
        }
      }
    }

    // GlobalLibraryMap must be requested after Variant models since it is built during dependency resolution.
    if (androidModules.isNotEmpty()) {
      val project = androidModules[0].gradleProject
      controller.findModel(project, GlobalLibraryMap::class.java)?.also { globalLibraryMap ->
        consumer.consumeProjectModel(project, globalLibraryMap, GlobalLibraryMap::class.java)
      }
    }

    // AdditionalClassiferArtifactsModel must be requested after AndroidProject and Variant model since it requires the library list in dependency model.
    getAdditionalClassifierArtifactsModel(
      controller,
      androidModules,
      syncActionOptions.cachedLibraries,
      consumer,
      syncActionOptions.downloadAndroidxUISamplesSources
    )
  }

  private fun populateProjectSyncIssues(
    controller: BuildController,
    buildModel: GradleBuild,
    consumer: ProjectImportModelProvider.BuildModelConsumer
  ) {
    buildModel.projects.forEach { gradleProject ->
      controller.findModel(gradleProject, ProjectSyncIssues::class.java)?.also { projectSyncIssues ->
        consumer.consumeProjectModel(gradleProject, projectSyncIssues, ProjectSyncIssues::class.java)
      }
    }
  }

  /**
   * Gets the [AndroidProject] or [NativeAndroidProject] (based on [modelType]) for the given [BasicGradleProject].
   */
  private fun <T> findParameterizedAndroidModel(controller: BuildController,
                                                project: BasicGradleProject,
                                                modelType: Class<T>): T? {
    if (syncActionOptions.isSingleVariantSyncEnabled) {
      try {
        val model = controller.getModel(project, modelType, ModelBuilderParameter::class.java) { parameter ->
          parameter.shouldBuildVariant = false
        }
        if (model != null) return model
      }
      catch (e: UnsupportedVersionException) {
        // Using old version of Gradle. Fall back to full variants sync for this module.
      }
    }
    return controller.findModel(project, modelType)
  }
}