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
import com.android.builder.model.SyncIssue
import com.android.builder.model.Variant
import com.android.builder.model.v2.models.ndk.NativeModelBuilderParameter
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.ide.common.gradle.model.IdeVariant
import com.android.ide.common.gradle.model.impl.BuildFolderPaths
import com.android.ide.common.gradle.model.impl.ModelCache
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeVariantAbi
import com.android.ide.common.repository.GradleVersion
import com.android.ide.gradle.model.GradlePluginModel
import com.android.tools.idea.gradle.project.sync.FullSyncActionOptions
import com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId
import com.android.tools.idea.gradle.project.sync.NativeVariantsSyncActionOptions
import com.android.tools.idea.gradle.project.sync.SelectedVariants
import com.android.tools.idea.gradle.project.sync.SingleVariantSyncActionOptions
import com.android.tools.idea.gradle.project.sync.SyncActionOptions
import com.android.tools.idea.gradle.project.sync.SyncProjectActionOptions
import com.android.tools.idea.gradle.project.sync.VariantSelectionChange
import com.android.tools.idea.gradle.project.sync.applyChange
import com.android.tools.idea.gradle.project.sync.createVariantDetailsFrom
import com.android.tools.idea.gradle.project.sync.idea.UsedInBuildAction
import com.android.tools.idea.gradle.project.sync.idea.getAdditionalClassifierArtifactsModel
import com.android.tools.idea.gradle.project.sync.idea.issues.AndroidSyncException
import com.android.tools.idea.gradle.project.sync.idea.svs.AndroidModule.NativeModelVersion
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueData
import org.gradle.tooling.BuildController
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.kotlin.kapt.idea.KaptGradleModel
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import java.util.LinkedList

interface GradleInjectedSyncActionRunner {
  fun <T> runActions(actions: List<(BuildController) -> T>): List<T>
  fun <T> runAction(action: (BuildController) -> T): T
}

@UsedInBuildAction
class AndroidExtraModelProvider(private val syncOptions: SyncActionOptions) : ProjectImportModelProvider {
  private var remainingIncludedBuildModels: Int? = null
  private val buildModels: MutableList<GradleBuild> = mutableListOf()

  override fun populateBuildModels(
    controller: BuildController,
    buildModel: GradleBuild,
    consumer: ProjectImportModelProvider.BuildModelConsumer
  ) {
    // Flatten the platform's handling of included builds. We need all models together to resolve cross `includeBuild` dependencies
    // correctly. This, unfortunately, makes assumptions about the order in which these methods are invoked. If broken it will be caught
    // by any test attempting to sync a composite build.
    if (remainingIncludedBuildModels == null) {
      remainingIncludedBuildModels = 1 /* this one */ + (runCatching { buildModel.includedBuilds.size }.getOrNull() ?: 0)
    }
    buildModels.add(buildModel)
    remainingIncludedBuildModels = remainingIncludedBuildModels!! - 1
    if (remainingIncludedBuildModels == 0) {
      Worker(
        controller,
        syncOptions,
        buildModels,
        // Consumers for different build models are all equal except they aggregate statistics to different targets. We cannot request all
        // models we need until we have enough information to do it. In the case of a composite builds all model fetching time will be
        // reported against the last included build.
        consumer
      ).populateBuildModels()
    }
  }

  override fun populateProjectModels(
    controller: BuildController,
    projectModel: Model,
    modelConsumer: ProjectImportModelProvider.ProjectModelConsumer
  ) {
    controller.findModel(projectModel, GradlePluginModel::class.java)
      ?.also { pluginModel -> modelConsumer.consume(pluginModel, GradlePluginModel::class.java) }
    controller.findModel(projectModel, KaptGradleModel::class.java)
      ?.also { model -> modelConsumer.consume(model, KaptGradleModel::class.java) }
  }

  private class Worker(
    controller: BuildController,
    private val syncOptions: SyncActionOptions,
    private val buildModels: List<GradleBuild>, // Always not empty.
    private val consumer: ProjectImportModelProvider.BuildModelConsumer
  ) {
    private val androidModulesById: MutableMap<String, AndroidModule> = HashMap()
    private val buildFolderPaths = ModelConverter.populateModuleBuildDirs(controller)
    private val actionRunner = createActionRunner(controller)
    private val modelCache = ModelCache.create(buildFolderPaths)

    fun populateBuildModels() {
      try {
        val modules: List<GradleModule> =
          when (syncOptions) {
            is SyncProjectActionOptions -> {
              populateAndroidModels(syncOptions)
            }
            is NativeVariantsSyncActionOptions -> {
              consumer.consume(
                buildModels.first(),
                actionRunner.runAction { controller -> controller.getModel(IdeaProject::class.java) },
                IdeaProject::class.java
              )
              fetchNativeVariantsAndroidModels(syncOptions, buildFolderPaths)
            }
            // Note: No more cases.
          }
        // Requesting ProjectSyncIssues must be performed "last" since all other model requests may produces additional issues.
        // Note that "last" here means last among Android models since many non-Android models are requested after this point.
        populateProjectSyncIssues(modules)
        modules.forEach { it.deliverModels(consumer) }
      }
      catch (e: AndroidSyncException) {
        consumer.consume(
          buildModels.first(),
          IdeAndroidSyncError(e.message.orEmpty(), e.stackTrace.map { it.toString() }),
          IdeAndroidSyncError::class.java
        )
      }
    }

    /**
     * Requests Android project models for the given [buildModels]
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
    private fun populateAndroidModels(syncOptions: SyncProjectActionOptions): List<GradleModule> {
      val isFullSync = when (syncOptions) {
        is FullSyncActionOptions -> true
        is SingleVariantSyncActionOptions -> false
        // Note: No other cases.
      }
      val modules: List<GradleModule> = actionRunner.runActions(
        buildModels.projects.map { gradleProject ->
          fun(controller: BuildController): GradleModule {
            val androidProject = controller.findParameterizedAndroidModel(
              gradleProject,
              AndroidProject::class.java,
              shouldBuildVariant = isFullSync
            )
            if (androidProject != null) {
              // TODO(solodkyy): Perhaps request the version interface depending on AGP version.
              val nativeModule = controller.getNativeModuleFromGradle(gradleProject, syncAllVariantsAndAbis = isFullSync)
              val nativeAndroidProject: NativeAndroidProject? =
                if (nativeModule == null)
                  controller.findParameterizedAndroidModel(
                    gradleProject,
                    NativeAndroidProject::class.java,
                    shouldBuildVariant = isFullSync
                  )
                else null

              return createAndroidModule(
                gradleProject,
                androidProject,
                nativeAndroidProject,
                nativeModule,
                modelCache
              )
            }
            return JavaModule(gradleProject)
          }
        }.toList()
      )

      modules.filterIsInstance<AndroidModule>().forEach { androidModulesById[it.id] = it }

      if (syncOptions is SingleVariantSyncActionOptions) {
        // This section is for Single Variant Sync specific models if we have reached here we should have already requested AndroidProjects
        // without any Variant information. Now we need to request that Variant information for the variants that we are interested in.
        // e.g the ones that should be selected by the IDE.
        chooseSelectedVariants(modules.filterIsInstance<AndroidModule>(), syncOptions)
      }

      // AdditionalClassifierArtifactsModel must be requested after AndroidProject and Variant model since it requires the library list in dependency model.
      getAdditionalClassifierArtifactsModel(
        actionRunner,
        modules.filterIsInstance<AndroidModule>(),
        syncOptions.additionalClassifierArtifactsAction.cachedLibraries,
        syncOptions.additionalClassifierArtifactsAction.downloadAndroidxUISamplesSources
      )

      return modules
    }

    private fun fetchNativeVariantsAndroidModels(
      syncOptions: NativeVariantsSyncActionOptions,
      buildFolderPaths: BuildFolderPaths
    ): List<GradleModule> {
      val modelCache = ModelCache.create(buildFolderPaths)
      return actionRunner.runActions(
        buildModels.projects.map { gradleProject ->
          fun(controller: BuildController): GradleModule? {
            val projectIdentifier = gradleProject.projectIdentifier
            val moduleId = createUniqueModuleId(projectIdentifier.buildIdentifier.rootDir, projectIdentifier.projectPath)
            val variantName = syncOptions.moduleVariants[moduleId] ?: return null

            fun tryV2(): NativeVariantsAndroidModule? {
              controller.findModel(gradleProject, NativeModule::class.java, NativeModelBuilderParameter::class.java) {
                it.variantsToGenerateBuildInformation = listOf(variantName)
                it.abisToGenerateBuildInformation = syncOptions.requestedAbis.toList()
              } ?: return null
              return NativeVariantsAndroidModule.createV2(gradleProject)
            }

            fun fetchV1Abi(abi: String): IdeNativeVariantAbi? {
              val model = controller.findModel(
                gradleProject,
                NativeVariantAbi::class.java,
                ModelBuilderParameter::class.java
              ) { parameter ->
                parameter.setVariantName(variantName)
                parameter.setAbiName(abi)
              } ?: return null
              return modelCache.nativeVariantAbiFrom(model)
            }

            fun tryV1(): NativeVariantsAndroidModule {
              return NativeVariantsAndroidModule.createV1(gradleProject, syncOptions.requestedAbis.mapNotNull { abi -> fetchV1Abi(abi) })
            }

            return tryV2() ?: tryV1()
          }
        }.toList()
      )
        .filterNotNull()
    }

    private fun populateProjectSyncIssues(androidModules: List<GradleModule>) {
      actionRunner.runActions(
        androidModules.map { module ->
          fun(controller: BuildController) {
            val syncIssues = controller.findModel(module.findModelRoot, ProjectSyncIssues::class.java)
            if (syncIssues != null) {
              // It is FINE to assign on the worker thread since we operate at one module at a time and the same AndroidModule instance
              // cannot accessed from multiple worker threads.
              module.setSyncIssues(syncIssues.syncIssues.toSyncIssueData())
            }
          }
        }
      )
    }

    /**
     * Gets the [AndroidProject] or [NativeAndroidProject] (based on [modelType]) for the given [BasicGradleProject].
     */
    private fun <T> BuildController.findParameterizedAndroidModel(
      project: BasicGradleProject,
      modelType: Class<T>,
      shouldBuildVariant: Boolean
    ): T? {
      if (!shouldBuildVariant) {
        try {
          val model = getModel(project, modelType, ModelBuilderParameter::class.java) { parameter ->
            parameter.shouldBuildVariant = false
          }
          if (model != null) return model
        }
        catch (e: UnsupportedVersionException) {
          // Using old version of Gradle. Fall back to full variants sync for this module.
        }
      }
      return findModel(project, modelType)
    }

    private fun BuildController.getNativeModuleFromGradle(project: BasicGradleProject, syncAllVariantsAndAbis: Boolean): NativeModule? {
      return try {
        if (!syncAllVariantsAndAbis) {
          // With single variant mode, we first only collect basic project information. The more complex information will be collected later
          // for the selected variant and ABI.
          getModel(project, NativeModule::class.java, NativeModelBuilderParameter::class.java) {
            it.variantsToGenerateBuildInformation = emptyList()
            it.abisToGenerateBuildInformation = emptyList()
          }
        }
        else {
          // If single variant is not enabled, we sync all variant and ABIs at once.
          getModel(project, NativeModule::class.java, NativeModelBuilderParameter::class.java) {
            it.variantsToGenerateBuildInformation = null
            it.abisToGenerateBuildInformation = null
          }
        }
      }
      catch (e: UnsupportedVersionException) {
        // Using old version of Gradle that does not support V2 models.
        null
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
      inputModules: List<AndroidModule>,
      syncOptions: SingleVariantSyncActionOptions
    ) {
      val allModulesToSetUp = prepareRequestedOrDefaultModuleConfigurations(inputModules, syncOptions)

      // This first starts by requesting models for all the modules that can be reached from the app modules (via dependencies) and then
      // requests any other modules that can't be reached.
      val propagatedToModules = LinkedList<ModuleConfiguration>()
      val visitedModules = HashSet<String>()
      while (propagatedToModules.isNotEmpty() || allModulesToSetUp.isNotEmpty()) {
        // Walk the tree of module dependencies in the breadth-first-search order.
        val moduleConfiguration =
          if (propagatedToModules.isNotEmpty()) propagatedToModules.removeFirst() else allModulesToSetUp.removeFirst()
        if (!visitedModules.add(moduleConfiguration.id)) continue

        val result = actionRunner.runAction(
          getVariantAndModuleDependenciesAction(moduleConfiguration, syncOptions.selectedVariants)
        ) ?: continue

        result.module.addVariant(result.ideVariant)
        result.nativeVariantAbi?.let { result.module.addNativeVariant(it) }
        propagatedToModules.addAll(result.moduleDependencies)
      }
    }

    private fun prepareRequestedOrDefaultModuleConfigurations(
      inputModules: List<AndroidModule>,
      syncOptions: SingleVariantSyncActionOptions
    ): LinkedList<ModuleConfiguration> {
      // The module whose variant selection was changed from UI, the dependency modules should be consistent with this module. Achieve this by
      // adding this module to the head of allModules so that its dependency modules are resolved first.
      return inputModules
        .asSequence()
        .filter { it.fetchedVariantNames.isEmpty() }
        .mapNotNull { module -> selectedOrDefaultModuleConfiguration(module, syncOptions)?.let { module to it } }
        .sortedBy { (module, _) ->
          when {
            module.id == syncOptions.moduleIdWithVariantSwitched -> 0
            // All app modules must be requested first since they are used to work out which variants to request for their dependencies.
            // The configurations requested here represent just what we know at this moment. Many of these modules will turn out to be
            // dependencies of others and will be visited sooner and the configurations created below will be discarded. This is fine since
            // `createRequestedModuleConfiguration()` is cheap.
            module.projectType == PROJECT_TYPE_APP -> 1
            else -> 2
          }
        }
        .map { it.second }
        .toCollection(LinkedList<ModuleConfiguration>())
    }

    private fun selectedOrDefaultModuleConfiguration(
      module: AndroidModule,
      syncOptions: SingleVariantSyncActionOptions
    ): ModuleConfiguration? {
      val selectedVariants = syncOptions.selectedVariants
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

    private class SyncVariantResult(
      val module: AndroidModule,
      val ideVariant: IdeVariant,
      val nativeVariantAbi: IdeNativeVariantAbi?,
      val moduleDependencies: List<ModuleConfiguration>
    )

    /**
     * Given a [moduleConfiguration] returns an action that fetches variant specific models for the given module+variant and returns the set
     * of [SyncVariantResult]s containing the fetched models and describing resolved module configurations for the given module.
     */
    private fun getVariantAndModuleDependenciesAction(
      moduleConfiguration: ModuleConfiguration,
      selectedVariants: SelectedVariants
    ): (BuildController) -> SyncVariantResult? {
      val selectedVariantDetails = selectedVariants.selectedVariants[moduleConfiguration.id]?.details
      val module = androidModulesById[moduleConfiguration.id] ?: return { null }
      return fun(controller: BuildController): SyncVariantResult? {
        val variant = controller.findVariantModel(module, moduleConfiguration.variant) ?: return null
        val abiToRequest = chooseAbiToRequest(module, variant.name, moduleConfiguration.abi)
        val nativeVariantAbi = abiToRequest
          ?.let { controller.findNativeVariantAbiModel(modelCache, module, variant.name, abiToRequest) }

        val ideVariant = modelCache.variantFrom(module.androidProject, variant, module.modelVersion)
        val newlySelectedVariantDetails = createVariantDetailsFrom(module.androidProject.flavorDimensions, ideVariant)
        val variantDiffChange =
          VariantSelectionChange.extractVariantSelectionChange(from = newlySelectedVariantDetails, base = selectedVariantDetails)

        fun propagateVariantSelectionChangeFallback(dependencyModuleId: String): ModuleConfiguration? {
          val dependencyModule = androidModulesById[dependencyModuleId] ?: return null
          val dependencyModuleCurrentlySelectedVariant = selectedVariants.selectedVariants[dependencyModuleId]
          val dependencyModuleSelectedVariantDetails = dependencyModuleCurrentlySelectedVariant?.details

          val newSelectedVariantDetails = dependencyModuleSelectedVariantDetails?.applyChange(
            variantDiffChange ?: VariantSelectionChange.EMPTY)
                                          ?: return null

          // Make sure the variant name we guessed in fact exists.
          if (dependencyModule.allVariantNames?.contains(newSelectedVariantDetails.name) != true) return null

          return ModuleConfiguration(dependencyModuleId, newSelectedVariantDetails.name, abiToRequest)
        }

        fun generateDirectModuleDependencies(): List<ModuleConfiguration> {
          return ideVariant.mainArtifact.level2Dependencies.moduleDependencies.mapNotNull { moduleDependency ->
            val dependencyProject = moduleDependency.projectPath
            val dependencyModuleId = createUniqueModuleId(moduleDependency.buildId ?: "", dependencyProject)
            val dependencyVariant = moduleDependency.variant
            if (dependencyVariant != null) {
              ModuleConfiguration(dependencyModuleId, dependencyVariant, abiToRequest)
            }
            else {
              propagateVariantSelectionChangeFallback(dependencyModuleId)
            }
          }
        }

        /**
         * Attempt to propagate variant changes to feature modules. This is not guaranteed to be correct, but since we do not know what the
         * real dependencies of each feature module variant are we can only guess.
         */
        fun generateDynamicFeatureDependencies(): List<ModuleConfiguration> {
          val rootProjectGradleDirectory = module.gradleProject.projectIdentifier.buildIdentifier.rootDir
          return module.androidProject.dynamicFeatures.mapNotNull { featureModuleGradlePath ->
            val featureModuleId = createUniqueModuleId(rootProjectGradleDirectory, featureModuleGradlePath)
            propagateVariantSelectionChangeFallback(featureModuleId)
          }
        }

        return SyncVariantResult(
          module,
          ideVariant,
          nativeVariantAbi,
          generateDirectModuleDependencies() + generateDynamicFeatureDependencies()
        )
      }
    }

    private fun BuildController.findVariantModel(
      module: AndroidModule,
      variantName: String
    ): Variant? {
      return findModel(
        module.findModelRoot,
        Variant::class.java,
        ModelBuilderParameter::class.java
      ) { parameter ->
        parameter.setVariantName(variantName)
      }
    }

    private fun BuildController.findNativeVariantAbiModel(
      modelCache: ModelCache,
      module: AndroidModule,
      variantName: String,
      abiToRequest: String
    ): IdeNativeVariantAbi? {
      return if (module.nativeModelVersion == NativeModelVersion.V2) {
        // V2 model is available, trigger the sync with V2 API
        // NOTE: Even though we drop the value returned the side effects of this code are important. Native sync creates file on the disk
        // which are later used.
        findModel(module.findModelRoot, NativeModule::class.java, NativeModelBuilderParameter::class.java) { parameter ->
          parameter.variantsToGenerateBuildInformation = listOf(variantName)
          parameter.abisToGenerateBuildInformation = listOf(abiToRequest)
        }
        null
      }
      else {
        // Fallback to V1 models otherwise.
        val model = findModel(module.findModelRoot, NativeVariantAbi::class.java, ModelBuilderParameter::class.java) { parameter ->
          parameter.setVariantName(variantName)
          parameter.setAbiName(abiToRequest)
        }
        model?.let { modelCache.nativeVariantAbiFrom(model) }
      }
    }

    /**
     * [selectedAbi] if null or the abi doesn't exist a default will be picked. This default will be "x86" if
     *               it exists in the abi names returned by the [NativeAndroidProject] otherwise the first item of this list will be
     *               chosen.
     */
    private fun chooseAbiToRequest(
      module: AndroidModule,
      variantName: String,
      selectedAbi: String?
    ): String? {
      // This module is not a native one, nothing to do
      if (module.nativeModelVersion == NativeModelVersion.None) return null

      // Attempt to get the list of supported abiNames for this variant from the NativeAndroidProject
      // Otherwise return from this method with a null result as abis are not supported.
      val abiNames = module.getVariantAbiNames(variantName) ?: return null

      return (if (selectedAbi != null && abiNames.contains(selectedAbi)) selectedAbi else abiNames.getDefaultOrFirstItem("x86"))
             ?: throw AndroidSyncException("No valid Native abi found to request!")
    }
  }
}

private val List<GradleBuild>.projects: Sequence<BasicGradleProject> get() = asSequence().flatMap { it.projects.asSequence() }

private fun createAndroidModule(
  gradleProject: BasicGradleProject,
  androidProject: AndroidProject,
  nativeAndroidProject: NativeAndroidProject?,
  nativeModule: NativeModule?,
  modelCache: ModelCache
): AndroidModule {
  val modelVersionString = ModelCache.safeGet(androidProject::getModelVersion, "")
  val modelVersion: GradleVersion? = GradleVersion.tryParseAndroidGradlePluginVersion(modelVersionString)

  val ideAndroidProject = modelCache.androidProjectFrom(androidProject)
  val idePrefetchedVariants =
    ModelCache.safeGet(androidProject::getVariants, emptyList())
      .map { modelCache.variantFrom(ideAndroidProject, it, modelVersion) }
      .takeUnless { it.isEmpty() }

  // Single-variant-sync models have variantNames property and pre-single-variant sync model should have all variants present instead.
  val allVariantNames: Set<String>? = (ModelCache.safeGet(androidProject::getVariantNames, null)
                                       ?: idePrefetchedVariants?.map { it.name })?.toSet()

  val defaultVariantName: String? = ModelCache.safeGet(androidProject::getDefaultVariant, null)
                                    ?: allVariantNames?.getDefaultOrFirstItem("debug")

  val ideNativeAndroidProject = nativeAndroidProject?.let(modelCache::nativeAndroidProjectFrom)
  val ideNativeModule = nativeModule?.let(modelCache::nativeModuleFrom)

  val androidModule = AndroidModule(
    modelVersion,
    gradleProject,
    ideAndroidProject,
    allVariantNames,
    defaultVariantName,
    idePrefetchedVariants,
    ideNativeAndroidProject,
    ideNativeModule
  )

  @Suppress("DEPRECATION")
  ModelCache.safeGet(androidProject::getSyncIssues, null)?.let {
    // It will be overridden if we receive something here but also a proper sync issues model later.
    syncIssues ->
    androidModule.setSyncIssues(syncIssues.toSyncIssueData())
  }

  return androidModule
}

private fun Collection<SyncIssue>.toSyncIssueData(): List<SyncIssueData> {
  return map { syncIssue ->
    SyncIssueData(
      message = syncIssue.message,
      data = syncIssue.data,
      multiLineMessage = ModelCache.safeGet(syncIssue::multiLineMessage, null)?.toList(),
      severity = syncIssue.severity,
      type = syncIssue.type
    )
  }
}

private fun createActionRunner(controller: BuildController): GradleInjectedSyncActionRunner {
  return object : GradleInjectedSyncActionRunner {
    override fun <T> runActions(actions: List<(BuildController) -> T>): List<T> {
      return actions.map { it(controller) }
    }

    override fun <T> runAction(action: (BuildController) -> T): T {
      return action(controller)
    }
  }
}
