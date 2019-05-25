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
import com.android.builder.model.level2.GlobalLibraryMap
import com.android.java.model.GradlePluginModel
import com.android.tools.idea.gradle.project.sync.idea.UsedInBuildAction
import com.android.tools.idea.gradle.project.sync.ng.SyncActionOptions
import org.gradle.tooling.BuildController
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.plugins.gradle.model.ProjectImportExtraModelProvider

@UsedInBuildAction
class AndroidExtraModelProvider(private val syncActionOptions: SyncActionOptions) : ProjectImportExtraModelProvider {
  override fun populateBuildModels(controller: BuildController,
                                   project: IdeaProject,
                                   consumer: ProjectImportExtraModelProvider.BuildModelConsumer) {
    // Here we go through each module and query Gradle for the following models in this order:
    //   1 - Query for the AndroidProject for the module
    //   2 - Query for the NativeAndroidProject (only if we also obtain an Android project)
    //   3 - Query for the GlobalLibraryMap for the module (we ALWAYS do this reguardless of the other two models)
    // If single variant sync is enabled then [findParameterizedAndroidModel] will use Gradles parameterized model builder API
    // in order to stop Gradle from building the variant.
    //   4 - (Single Variant Sync only) Work out which variant for which models we need to request, and request them.
    //       See IdeaSelectedVariantChooser for more details.
    // All of the requested models are registered back to IDEAs external project system via the BuildModelConsumer callback
    val androidModules: MutableList<IdeaAndroidModule> = mutableListOf()
    project.modules.forEach { module ->
      findParameterizedAndroidModel(controller, module.gradleProject, AndroidProject::class.java)?.also { androidProject ->
        consumer.consume(module, androidProject, AndroidProject::class.java)

        val nativeAndroidProject = findParameterizedAndroidModel(controller, module.gradleProject, NativeAndroidProject::class.java)?.also {
          consumer.consume(module, it, NativeAndroidProject::class.java)
        }

        androidModules.add(IdeaAndroidModule(module, androidProject, nativeAndroidProject))
      }
    }

    if (!androidModules.isEmpty()) {
      val module = androidModules[0].ideaModule
      controller.findModel(module.gradleProject, GlobalLibraryMap::class.java)?.also { globalLibraryMap ->
        consumer.consume(module, globalLibraryMap, GlobalLibraryMap::class.java)
      }
    }

    if (!syncActionOptions.isSingleVariantSyncEnabled) return

    // This section is for Single Variant Sync specific models if we have reached here we should have already requested AndroidProjects
    // without any Variant information. Now we need to request that Variant information for the variants that we are interested in.
    // e.g the ones that should be selected by the IDE.

    val selectedVariants = syncActionOptions.selectedVariants
                           ?: throw IllegalStateException("Single variant sync requested, but SelectedVariants were null!")
    chooseSelectedVariants(controller, androidModules, selectedVariants, syncActionOptions.shouldGenerateSources())
    androidModules.forEach { module ->
      consumer.consume(module.ideaModule, module.variantGroup, VariantGroup::class.java)
    }
  }

  override fun populateProjectModels(controller: BuildController,
                                     module: IdeaModule?,
                                     consumer: ProjectImportExtraModelProvider.ProjectModelConsumer) {
    // We don't yet have any Global models so if module equal null we just return
    module?.let {
      controller
        .findModel(it.gradleProject, GradlePluginModel::class.java)
        ?.also { pluginModel ->
          consumer.consume(pluginModel, GradlePluginModel::class.java)
        }
    }
  }

  /**
   * Gets the [AndroidProject] or [NativeAndroidProject] (based on [modelType]) for the given [GradleProject].
   */
  private fun <T> findParameterizedAndroidModel(controller: BuildController,
                                                project: GradleProject,
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