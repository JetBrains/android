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

import com.android.AndroidProjectTypes.PROJECT_TYPE_APP
import com.android.builder.model.AndroidProject
import com.android.builder.model.ModelBuilderParameter
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.ProjectSyncIssues
import com.android.builder.model.Variant
import com.android.builder.model.v2.models.ndk.NativeModelBuilderParameter
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.ide.common.gradle.model.IdeVariant
import com.android.ide.gradle.model.GradlePluginModel
import com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId
import com.android.tools.idea.gradle.project.sync.SelectedVariants
import com.android.tools.idea.gradle.project.sync.SyncActionOptions
import com.android.tools.idea.gradle.project.sync.idea.UsedInBuildAction
import com.android.tools.idea.gradle.project.sync.idea.getAdditionalClassifierArtifactsModel
import com.android.tools.idea.gradle.project.sync.idea.issues.AndroidSyncException
import com.android.tools.idea.gradle.project.sync.idea.svs.AndroidModule.NativeModelVersion
import org.gradle.tooling.BuildController
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.kotlin.kapt.idea.KaptGradleModel
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import java.util.LinkedList

@UsedInBuildAction
class AndroidExtraModelProvider(private val syncActionOptions: SyncActionOptions) : ProjectImportModelProvider {
  private val modulesById: MutableMap<String, AndroidModule> = HashMap()

  override fun populateBuildModels(
    controller: BuildController,
    buildModel: GradleBuild,
    consumer: ProjectImportModelProvider.BuildModelConsumer
  ) {
    try {
      val androidModules = populateAndroidModels(controller, buildModel)
      // Requesting ProjectSyncIssues must be performed "last" since all other model requests may produces addition issues.
      // Note that "last" here means last among Android models since many non-Android models are requested after this point.
      populateProjectSyncIssues(controller, androidModules)

      androidModules.forEach { it.deliverModels(consumer) }
    }
    catch (e: AndroidSyncException) {
      consumer.consume(
        buildModel,
        IdeAndroidSyncError(e.message.orEmpty(), e.stackTrace.map { it.toString() }),
        IdeAndroidSyncError::class.java
      )
    }
    finally {
      // TODO(b/166240410): We DO ignore cross-included-build dependencies when selecting build variants to sync. This needs to be fixed.
      //  This `clear` is to ensure we are not somewhere in between two states and should be removed. Removing it now would make just some
      //  dependencies be processed.
      modulesById.clear()
    }
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
   * Requests Android project models for the given [buildModel]
   *
   * We do this by going through each module and query Gradle for the following models:
   *   1. Query for the AndroidProject for the module
   *   2. Query for the NativeModule (V2 API) (only if we also obtain an AndroidProject)
   *   3. Query for the NativeAndroidProject (V1 API) (only if we can obtain AndroidProject but cannot obtain V2 NativeModule)
   *   4. Query for the GlobalLibraryMap for the module (we ALWAYS do this regardless of the other two models)
   *   5. (Single Variant Sync only) Work out which variant for which models we need to request, and request them.
   *      See IdeaSelectedVariantChooser for more details.
   *
   * If single variant sync is enabled then [findParameterizedAndroidModel] will use Gradle parameterized model builder API
   * in order to stop Gradle from building the variant.
   * All of the requested models are registered back to the external project system via the
   * [ProjectImportModelProvider.BuildModelConsumer] callback.
   */
  private fun populateAndroidModels(
    controller: BuildController,
    buildModel: GradleBuild
  ): List<AndroidModule> {
    val buildFolderPaths = ModelConverter.populateModuleBuildDirs(controller)
    val androidModules: MutableList<AndroidModule> = mutableListOf()
    buildModel.projects.forEach { gradleProject ->
      val androidProject = findParameterizedAndroidModel(controller, gradleProject, AndroidProject::class.java)
      if (androidProject != null) {
        val nativeModule = controller.getNativeModuleFromGradle(gradleProject)
        val nativeAndroidProject: NativeAndroidProject? =
          if (nativeModule != null) null
          else findParameterizedAndroidModel(controller, gradleProject, NativeAndroidProject::class.java)

        val module = AndroidModule.create(
          gradleProject,
          androidProject,
          nativeAndroidProject,
          nativeModule,
          buildFolderPaths
        )
        modulesById[module.id] = module
        androidModules.add(module)
      }
    }

    if (syncActionOptions.isSingleVariantSyncEnabled) {
      // This section is for Single Variant Sync specific models if we have reached here we should have already requested AndroidProjects
      // without any Variant information. Now we need to request that Variant information for the variants that we are interested in.
      // e.g the ones that should be selected by the IDE.
      chooseSelectedVariants(controller, androidModules)
    }

    // AdditionalClassifierArtifactsModel must be requested after AndroidProject and Variant model since it requires the library list in dependency model.
    getAdditionalClassifierArtifactsModel(
      controller,
      androidModules,
      syncActionOptions.cachedLibraries,
      syncActionOptions.downloadAndroidxUISamplesSources
    )
    return androidModules
  }

  private fun populateProjectSyncIssues(
    controller: BuildController,
    androidModules: List<AndroidModule>
  ) {
    androidModules.forEach { module ->
      val syncIssues = controller.findModel(module.findModelRoot, ProjectSyncIssues::class.java)
      if (syncIssues != null) {
        module.setSyncIssues(syncIssues.syncIssues)
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

  private fun BuildController.getNativeModuleFromGradle(project: BasicGradleProject): NativeModule? {
    try {
      if (syncActionOptions.isSingleVariantSyncEnabled) {
        // With single variant mode, we first only collect basic project information. The more complex information will be collected later
        // for the selected variant and ABI.
        return getModel(project, NativeModule::class.java, NativeModelBuilderParameter::class.java) {
          it.variantsToGenerateBuildInformation = emptyList()
          it.abisToGenerateBuildInformation = emptyList()
        }
      }
      else {
        // If single variant is not enabled, we sync all variant and ABIs at once.
        return getModel(project, NativeModule::class.java, NativeModelBuilderParameter::class.java) {
          it.variantsToGenerateBuildInformation = null
          it.abisToGenerateBuildInformation = null
        }
      }
    }
    catch (e: UnsupportedVersionException) {
      // Using old version of Gradle that does not support V2 models.
      return null
    }
  }

  /**
   * This method requests all of the required [Variant] models from Gradle via the tooling API.
   *
   * Function for choosing the build variant that will be selected in Android Studio (the IDE can only work with one variant at a time.)
   * works as follows:
   *  1. For Android app modules using new versions of the plugin, the [Variant] to select is obtained from the model.
   *     For older plugins, the "debug" variant is selected. If the module doesn't have a variant named "debug", it sorts all the
   *     variant names alphabetically and picks the first one.
   *  2. For Android library modules, it chooses the variant needed by dependent modules. For example, if variant "debug" in module "app"
   *     depends on module "lib" - variant "freeDebug", the selected variant in "lib" will be "freeDebug". If a library module is a leaf
   *     (i.e. no other modules depend on it) a variant will be picked as if the module was an app module.
   */
  fun chooseSelectedVariants(
    controller: BuildController,
    inputModules: List<AndroidModule>
  ) {
    val allModulesToSetUp = prepareRequestedOrDefaultModuleConfigurations(inputModules)

    // This first starts by requesting models for all the modules that can be reached from the app modules (via dependencies) and then
    // requests any other modules that can't be reached.
    val visitedModules = HashSet<String>()
    while (allModulesToSetUp.isNotEmpty()) {
      val moduleConfiguration = allModulesToSetUp.removeFirst()
      if (!visitedModules.add(moduleConfiguration.id)) continue

      val moduleDependencies = syncVariantAndGetModuleDependencies(controller, moduleConfiguration) ?: continue
      allModulesToSetUp.addAll(0, moduleDependencies) // Walk the tree of module dependencies in depth-first-search order.
    }
  }

  private fun prepareRequestedOrDefaultModuleConfigurations(inputModules: List<AndroidModule>): LinkedList<ModuleConfiguration> {
    val allModulesToSetUp = LinkedList<ModuleConfiguration>()
    // The module whose variant selection was changed from UI, the dependency modules should be consistent with this module. Achieve this by
    // adding this module to the head of allModules so that its dependency modules are resolved first.
    var moduleWithVariantSwitched: ModuleConfiguration? = null

    inputModules.filter { it.fetchedVariantNames.isEmpty() }.forEach { module ->
      val moduleConfiguration = selectedOrDefaultModuleConfiguration(module) ?: return@forEach
      if (module.id == syncActionOptions.moduleIdWithVariantSwitched) {
        moduleWithVariantSwitched = moduleConfiguration
      }
      else {
        // All app modules must be requested first since they are used to work out which variants to request for their dependencies.
        // The configurations requested here represent just what we know at this moment. Many of these modules will turn out to be
        // dependencies of others and will be visited sooner and the configurations created below will be discarded. This is fine since
        // `createRequestedModuleConfiguration()` is cheap.
        when (module.projectType) {
          PROJECT_TYPE_APP -> allModulesToSetUp.addFirst(moduleConfiguration)
          else -> allModulesToSetUp.addLast(moduleConfiguration)
        }
      }
    }

    moduleWithVariantSwitched?.let { allModulesToSetUp.addFirst(it) }
    return allModulesToSetUp
  }

  private fun selectedOrDefaultModuleConfiguration(module: AndroidModule): ModuleConfiguration? {
    val selectedVariants = syncActionOptions.selectedVariants ?: error("Single variant sync requested, but SelectedVariants were null!")
    val requestedVariantName = selectVariantForAppOrLeaf(module, selectedVariants) ?: return null
    val requestedAbi = selectedVariants.getSelectedAbi(module.id)
    return ModuleConfiguration(module.id, requestedVariantName, requestedAbi)
  }

  private fun selectVariantForAppOrLeaf(
    androidModule: AndroidModule,
    selectedVariants: SelectedVariants
  ): String? {
    val variantNames = androidModule.allVariantNames ?: return null
    return selectedVariants
      .getSelectedVariant(androidModule.id)
      // Check to see if we have a variant selected in the IDE, and that it is still a valid one.
      ?.takeIf { variantNames.contains(it) }
    ?: androidModule.defaultVariantName
  }

  private fun syncVariantAndGetModuleDependencies(
    controller: BuildController,
    moduleConfiguration: ModuleConfiguration
  ): List<ModuleConfiguration>? {
    val module = modulesById[moduleConfiguration.id] ?: return null // TODO(b/166240410): Composite build modules are not be resolved here.
    val variant = syncAndAddVariant(controller, module, moduleConfiguration.variant) ?: return null
    val abi = syncAndAddNativeVariantAbi(controller, module, variant.name, moduleConfiguration.abi)
    return variant.mainArtifact.level2Dependencies.moduleDependencies.mapNotNull { moduleDependency ->
      val dependencyProject = moduleDependency.projectPath ?: return@mapNotNull null
      val dependencyVariant = moduleDependency.variant ?: return@mapNotNull null
      ModuleConfiguration(createUniqueModuleId(moduleDependency.buildId ?: "", dependencyProject), dependencyVariant, abi)
    }
  }

  /**
   * Query Gradle for the [Variant] of the [module] with the given [variantName]. Gradle's parameterized tooling API is used in order
   * to pass the name of the variant and whether or not sources should be generated to the ModelBuilder via the [ModelBuilderParameter].
   *
   * @param[module] the module to request a [Variant] for
   * @param[controller] the Gradle [BuildController] that is queried for the model
   * @param[variantName] the name of the [Variant] that should be requested
   */
  private fun syncAndAddVariant(
    controller: BuildController,
    module: AndroidModule,
    variantName: String
  ): IdeVariant? {
    val variant = controller.findModel(module.findModelRoot, Variant::class.java, ModelBuilderParameter::class.java) { parameter ->
      parameter.setVariantName(variantName)
    }
    return variant?.let { module.addVariant(it) }
  }

  /**
   * Query Gradle for the abi name of the [module] with the given [variantName]. Gradle's parameterized tooling API is used in order
   * to pass the name of the variant and the abi name to the ModelBuilder via the [ModelBuilderParameter].
   *
   * If the passed [module] doesn't have a NativeAndroidProject then this method does nothing.
   *
   * @param[module] the module to request a [Variant] for
   * @param[controller] the Gradle [BuildController] that is queried for the model
   * @param[variantName] the name of the [Variant] that should be requested
   * @param[selectedAbi] which abi to select, if null or the abi doesn't exist a default will be picked. This default will be "x86" if
   *                     it exists in the abi names returned by the [NativeAndroidProject] otherwise the first item of this list will be
   *                     chosen.
   */
  private fun syncAndAddNativeVariantAbi(
    controller: BuildController,
    module: AndroidModule,
    variantName: String,
    selectedAbi: String?
  ): String? {
    // This module is not a native one, nothing to do
    if (module.nativeModelVersion == NativeModelVersion.None) return null

    // Attempt to get the list of supported abiNames for this variant from the NativeAndroidProject
    // Otherwise return from this method with a null result as abis are not supported.
    val abiNames = module.getVariantAbiNames(variantName) ?: return null

    val abiToRequest = (if (selectedAbi != null && abiNames.contains(selectedAbi)) selectedAbi else abiNames.getDefaultOrFirstItem("x86"))
                       ?: throw AndroidSyncException("No valid Native abi found to request!")

    if (module.nativeModelVersion == NativeModelVersion.V2) {
      // V2 model is available, trigger the sync with V2 API
      controller.findModel(module.findModelRoot, NativeModule::class.java, NativeModelBuilderParameter::class.java) {
        it.variantsToGenerateBuildInformation = listOf(variantName)
        it.abisToGenerateBuildInformation = listOf(abiToRequest)
      }
    }
    else {
      // Fallback to V1 models otherwise.
      controller.findModel(module.findModelRoot, NativeVariantAbi::class.java, ModelBuilderParameter::class.java) { parameter ->
        parameter.setVariantName(variantName)
        parameter.setAbiName(abiToRequest)
      }?.also {
        module.addNativeVariant(it)
      }
    }
    return abiToRequest
  }
}

