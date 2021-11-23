/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION
import com.android.Version
import com.android.builder.model.AndroidProject
import com.android.builder.model.ModelBuilderParameter
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.ProjectSyncIssues
import com.android.builder.model.SyncIssue
import com.android.builder.model.Variant
import com.android.builder.model.v2.dsl.BuildType
import com.android.builder.model.v2.dsl.ProductFlavor
import com.android.builder.model.v2.ide.BasicVariant
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.Versions
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.AndroidProject as V2AndroidProject
import com.android.builder.model.v2.models.ndk.NativeModelBuilderParameter
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.builder.model.v2.models.ProjectSyncIssues as V2ProjectSyncIssues
import com.android.builder.model.v2.ide.Variant as V2Variant
import com.android.ide.common.repository.GradleVersion
import com.android.ide.gradle.model.composites.BuildMap
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeVariantAbi
import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.model.IdeUnresolvedDependencies
import com.android.tools.idea.gradle.model.impl.IdeSyncIssueImpl
import com.android.tools.idea.gradle.project.upgrade.computeGradlePluginUpgradeState
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.FORCE
import com.android.utils.appendCapitalized
import org.gradle.tooling.BuildController
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.kotlin.gradle.KotlinGradleModel
import org.jetbrains.kotlin.gradle.KotlinMPPGradleModel
import org.jetbrains.kotlin.kapt.idea.KaptGradleModel
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService
import java.io.File
import java.util.LinkedList

internal class AndroidExtraModelProviderWorker(
  controller: BuildController,
  private val syncOptions: SyncActionOptions,
  private val buildModels: List<GradleBuild>, // Always not empty.
  private val consumer: ProjectImportModelProvider.BuildModelConsumer
) {
  private val androidModulesById: MutableMap<String, AndroidModule> = HashMap()
  private val buildFolderPaths = ModelConverter.populateModuleBuildDirs(
    controller)
  private val actionRunner = createActionRunner(controller, syncOptions.flags)
  private var canFetchV2Models: Boolean? = null
  private val modelCache: ModelCache by lazy {
    ModelCache.create(canFetchV2Models ?: error("Unable to determine which modelCache should be used"), buildFolderPaths)
  }

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
            fetchNativeVariantsAndroidModels(syncOptions)
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

  data class ExtendedVariantData(
    val basicVariant: BasicVariant,
    val variant: V2Variant,
    val dependencies: VariantDependencies
  )

  sealed class AndroidProjectResult {
    class V1Project(val androidProject: AndroidProject) : AndroidProjectResult() {
      override val buildName: String? = null
    }

    class V2Project(
      val basicAndroidProject: BasicAndroidProject,
      val androidProject: V2AndroidProject,
      val modelVersions: Versions,
      val androidDsl: AndroidDsl
    ) : AndroidProjectResult() {
      override val buildName: String = basicAndroidProject.buildName
    }

    abstract val buildName: String?
  }

  private fun canFetchV2Models(gradlePluginVersion: GradleVersion?): Boolean {
    return gradlePluginVersion != null && gradlePluginVersion.isAtLeast(7, 2, 0, "alpha", 1, true)
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
   * All the requested models are registered back to the external project system via the
   * [ProjectImportModelProvider.BuildModelConsumer] callback.
   */
  private fun populateAndroidModels(syncOptions: SyncProjectActionOptions): List<GradleModule> {
    val buildNameMap =
      (buildModels
         .mapNotNull { build ->
           actionRunner.runAction { controller -> controller.findModel(build.rootProject, BuildMap::class.java) }
         }
         .flatMap { buildNames -> buildNames.buildIdMap.entries.map { it.key to it.value } } +
       (":" to buildFolderPaths.buildRootDirectory!!)
      )
        .toMap()

    val modules: List<GradleModule> = actionRunner.runActions(
      buildModels.projects.map { gradleProject ->
        fun(controller: BuildController): GradleModule {

          var androidProjectResult: AndroidProjectResult? = null
          // Request V2 models if flag is enabled.
          if (syncOptions.flags.studioFlagUseV2BuilderModels) {
            // First request the Versions model to make sure we can fetch V2 models.
            val versions = controller.findNonParameterizedV2Model(gradleProject, Versions::class.java)

            if (versions?.also { checkAgpVersionCompatibility(versions.agp, syncOptions) } != null &&
                canFetchV2Models(GradleVersion.tryParseAndroidGradlePluginVersion(versions.agp))) {
              val basicAndroidProject = controller.findNonParameterizedV2Model(gradleProject, BasicAndroidProject::class.java)
              val androidProject = controller.findNonParameterizedV2Model(gradleProject, V2AndroidProject::class.java)
              val androidDsl = controller.findNonParameterizedV2Model(gradleProject, AndroidDsl::class.java)

              if (basicAndroidProject != null && androidProject != null && androidDsl != null)  {
                if (canFetchV2Models == null) {
                  canFetchV2Models = true
                } else if (canFetchV2Models == false) {
                  error("Cannot initiate V2 models for Sync.")
                }

                androidProjectResult =
                  AndroidProjectResult.V2Project(basicAndroidProject, androidProject, versions, androidDsl)

                // TODO(solodkyy): Perhaps request the version interface depending on AGP version.
                val nativeModule = controller.getNativeModuleFromGradle(gradleProject, syncAllVariantsAndAbis = false)
                val nativeAndroidProject: NativeAndroidProject? =
                  if (nativeModule == null)
                    controller.findParameterizedAndroidModel(gradleProject, NativeAndroidProject::class.java, shouldBuildVariant = false)
                  else null

                return createAndroidModule(
                  gradleProject,
                  androidProjectResult,
                  nativeAndroidProject,
                  nativeModule,
                  buildNameMap,
                  modelCache
                )
              }
            }
          }
          // Fall back to V1 if we can't request V2 models.
          if (androidProjectResult == null) {
            val androidProject = controller.findParameterizedAndroidModel(
              gradleProject,
              AndroidProject::class.java,
              shouldBuildVariant = false
            )
            if (androidProject?.also { checkAgpVersionCompatibility(androidProject.modelVersion, syncOptions) } != null) {
              if (canFetchV2Models == null) {
                canFetchV2Models = false
              } else if (canFetchV2Models == true) {
                error("Cannot initiate V1 models for Sync.")
              }
              androidProjectResult = AndroidProjectResult.V1Project(androidProject)

              val nativeModule = controller.getNativeModuleFromGradle(gradleProject, syncAllVariantsAndAbis = false)
              val nativeAndroidProject: NativeAndroidProject? =
                if (nativeModule == null)
                  controller.findParameterizedAndroidModel(gradleProject, NativeAndroidProject::class.java, shouldBuildVariant = false)
                else null

              return createAndroidModule(
                gradleProject,
                androidProjectResult,
                nativeAndroidProject,
                nativeModule,
                buildNameMap,
                modelCache
              )
            }
          }

          val kotlinGradleModel = controller.findModel(gradleProject, KotlinGradleModel::class.java)
          val kaptGradleModel = controller.findModel(gradleProject, KaptGradleModel::class.java)
          return JavaModule(gradleProject, kotlinGradleModel, kaptGradleModel)
        }
      }.toList()
    )

    modules.filterIsInstance<AndroidModule>().forEach { androidModulesById[it.id] = it }

    val variantNameResolvers = modules.filterIsInstance<AndroidModule>()
      .associate { (it.gradleProject.projectIdentifier.buildIdentifier.rootDir to it.gradleProject.path) to it.buildVariantNameResolver() } +
      modules.filterIsInstance<JavaModule>()
      .associate { (it.gradleProject.projectIdentifier.buildIdentifier.rootDir to it.gradleProject.path) to {_, _ -> null} }

    fun getVariantNameResolver(buildId: File, projectPath: String): VariantNameResolver =
      variantNameResolvers.getOrElse(buildId to projectPath) {
        error("Project identifier cannot be resolved. BuildName: $buildId, ProjectPath: $projectPath")
      }

    when (syncOptions) {
      is SingleVariantSyncActionOptions -> {
        // This section is for Single Variant Sync specific models if we have reached here we should have already requested AndroidProjects
        // without any Variant information. Now we need to request that Variant information for the variants that we are interested in.
        // e.g the ones that should be selected by the IDE.
        chooseSelectedVariants(modules.filterIsInstance<AndroidModule>(), syncOptions, ::getVariantNameResolver)
      }
      is AllVariantsSyncActionOptions -> {
        syncAllVariants(modules.filterIsInstance<AndroidModule>(), syncOptions, ::getVariantNameResolver)
      }
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

  private fun checkAgpVersionCompatibility(agpVersionString: String?, syncOptions: SyncActionOptions) {
    if (syncOptions.flags.studioFlagDisableForcedUpgrades) return
    val agpVersion = if (agpVersionString != null) GradleVersion.parse(agpVersionString) else return
    if (agpVersion.compareIgnoringQualifiers(GRADLE_PLUGIN_MINIMUM_VERSION) < 0) {
      throw AgpVersionTooOld(agpVersion)
    }
    // We don't have access to LatestKnownPluginVersionProvider: just use the build version
    val latestKnown = GradleVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION)
    val upgradeState = computeGradlePluginUpgradeState(agpVersion, latestKnown, setOf())
    if (upgradeState.importance == FORCE) {
      throw AgpVersionIncompatible(agpVersion)
    }
  }

  private fun fetchNativeVariantsAndroidModels(
    syncOptions: NativeVariantsSyncActionOptions
  ): List<GradleModule> {
    val modelCache = ModelCache.create(false)
    return actionRunner.runActions(
      buildModels.projects.map { gradleProject ->
        fun(controller: BuildController): GradleModule? {
          val projectIdentifier = gradleProject.projectIdentifier
          val moduleId = Modules.createUniqueModuleId(projectIdentifier.buildIdentifier.rootDir, projectIdentifier.projectPath)
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
          val syncIssues = if (syncOptions.flags.studioFlagUseV2BuilderModels && (module is AndroidModule) &&
                               canFetchV2Models(module.modelVersion)) {
            // Request V2 sync issues.
            controller.findModel(module.findModelRoot, V2ProjectSyncIssues::class.java)?.syncIssues?.toV2SyncIssueData()
          } else {
            controller.findModel(module.findModelRoot, ProjectSyncIssues::class.java)?.syncIssues?.toSyncIssueData()
          }

          // For V2: we do not populate SyncIssues with Unresolved dependencies because we pass them through builder models.
          val v2UnresolvedDependenciesIssues = if (module is AndroidModule) {
            module.unresolvedDependencies.map {
              IdeSyncIssueImpl(
                message = "Unresolved dependencies",
                data = it.name,
                multiLineMessage = it.cause?.lines(),
                severity = IdeSyncIssue.SEVERITY_ERROR,
                type = IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY
              ) }
          } else {
            emptyList()
          }

          if (syncIssues != null) {
            module.setSyncIssues(syncIssues + v2UnresolvedDependenciesIssues)
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
        // Using old version of Gradle. Fall back to all variants sync for this module.
      }
    }
    return findModel(project, modelType)
  }

  /**
   * Gets the [V2AndroidProject] or [ModelVersions] (based on [modelType]) for the given [BasicGradleProject].
   */
  private fun <T> BuildController.findNonParameterizedV2Model(
    project: BasicGradleProject,
    modelType: Class<T>
  ): T? {
    return findModel(project, modelType)
  }

  /**
   * Valid only for [VariantDependencies] using model parameter for the given [BasicGradleProject] using .
   */
  private fun BuildController.findVariantDependenciesV2Model(
    project: BasicGradleProject,
    variantName: String
  ): VariantDependencies? {
    return findModel(
      project,
      VariantDependencies::class.java,
      com.android.builder.model.v2.models.ModelBuilderParameter::class.java
    ) { it.variantName = variantName }
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

  private fun BuildController.isKotlinMppProject(root: Model) = findModel(root, KotlinMPPGradleModel::class.java) != null

  private fun BuildController.findKotlinGradleModelForAndroidProject(root: Model, variantName: String): KotlinGradleModel? {
    // Do not apply single-variant sync optimization to Kotlin multi-platform projects. We do not know the exact set of source sets
    // that needs to be processed.
    return if (isKotlinMppProject(root)) findModel(root, KotlinGradleModel::class.java)
    else findModel(root, KotlinGradleModel::class.java, ModelBuilderService.Parameter::class.java) {
      it.value = androidArtifactSuffixes.joinToString(separator = ",") { artifactSuffix -> variantName.appendCapitalized(artifactSuffix) }
    }
  }

  private fun BuildController.findKaptGradleModelForAndroidProject(root: Model, variantName: String): KaptGradleModel? {
    // Do not apply single-variant sync optimization to Kotlin multi-platform projects. We do not know the exact set of source sets
    // that needs to be processed.
    return if (isKotlinMppProject(root)) findModel(root, KaptGradleModel::class.java)
    else findModel(root, KaptGradleModel::class.java, ModelBuilderService.Parameter::class.java) {
      it.value = androidArtifactSuffixes.joinToString(separator = ",") { artifactSuffix -> variantName.appendCapitalized(artifactSuffix) }
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
    syncOptions: SingleVariantSyncActionOptions,
    getVariantNameResolver: (buildId: File, projectPath: String) -> VariantNameResolver
  ) {
    val allModulesToSetUp = prepareRequestedOrDefaultModuleConfigurations(inputModules, syncOptions)

    // When re-syncing a project without changing the selected variants it is likely that the selected variants won't in the end.
    // However, variant resolution is not perfectly parallelizable. To overcome this we try to fetch the previously selected variant
    // models in parallel and discard any that happen to change.
    val preResolvedVariants =
      when {
        !syncOptions.flags.studioFlagParallelSyncEnabled -> emptyMap()
        !syncOptions.flags.studioFlagParallelSyncPrefetchVariantsEnabled -> emptyMap()
        !actionRunner.parallelActionsSupported -> emptyMap()
        // TODO(b/181028873): Predict changed variants and build models in parallel.
        syncOptions.moduleIdWithVariantSwitched != null -> emptyMap()
        else -> {
          actionRunner
            .runActions(allModulesToSetUp.map {
              getVariantAndModuleDependenciesAction(
                it,
                syncOptions.selectedVariants,
                getVariantNameResolver
              )
            })
            .filterNotNull()
            .associateBy { it.moduleConfiguration }
        }
      }

    // This first starts by requesting models for all the modules that can be reached from the app modules (via dependencies) and then
    // requests any other modules that can't be reached.
    var propagatedToModules: List<ModuleConfiguration>? = null
    val visitedModules = HashSet<String>()

    while (propagatedToModules != null || allModulesToSetUp.isNotEmpty()) {
      // Walk the tree of module dependencies in the breadth-first-search order.
      val moduleConfigurationsToRequest: List<ModuleConfiguration> =
        if (propagatedToModules != null) {
          // If there are modules for which we know the selected variant, build models for these modules in parallel.
          val modules = propagatedToModules
          propagatedToModules = null
          modules.filter { visitedModules.add(it.id) }
        }
        else {
          // Otherwise take the next module from the main queue and follow its dependencies.
          val moduleConfiguration = allModulesToSetUp.removeFirst()
          if (!visitedModules.add(moduleConfiguration.id)) emptyList() else listOf(moduleConfiguration)
        }
      if (moduleConfigurationsToRequest.isEmpty()) continue

      val actions =
        moduleConfigurationsToRequest.map { moduleConfiguration ->
          val prefetchedModel = preResolvedVariants[moduleConfiguration]
          if (prefetchedModel != null) {
            // Return an action that simply returns the `prefetchedModel`.
            fun(_: BuildController) = prefetchedModel
          }
          else {
            getVariantAndModuleDependenciesAction(
              moduleConfiguration,
              syncOptions.selectedVariants,
              getVariantNameResolver
            )
          }
        }

      val preModuleDependencies = actionRunner.runActions(actions)

      preModuleDependencies.filterNotNull().forEach { result ->
        result.module.syncedVariant = result.ideVariant
        result.module.unresolvedDependencies = result.unresolvedDependencies
        result.module.syncedNativeVariant = when (val nativeVariantAbiResult = result.nativeVariantAbi) {
          is NativeVariantAbiResult.V1 -> nativeVariantAbiResult.variantAbi
          is NativeVariantAbiResult.V2 -> null
          NativeVariantAbiResult.None -> null
        }
        result.module.syncedNativeVariantAbiName = when (val nativeVariantAbiResult = result.nativeVariantAbi) {
          is NativeVariantAbiResult.V1 -> nativeVariantAbiResult.variantAbi.abi
          is NativeVariantAbiResult.V2 -> nativeVariantAbiResult.selectedAbiName
          NativeVariantAbiResult.None -> null
        }
      }
      propagatedToModules = preModuleDependencies.filterNotNull().flatMap { it.moduleDependencies }.takeUnless { it.isEmpty() }
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
      .mapNotNull { module -> selectedOrDefaultModuleConfiguration(module, syncOptions)?.let { module to it } }
      .sortedBy { (module, _) ->
        when {
          module.id == syncOptions.moduleIdWithVariantSwitched -> 0
          // All app modules must be requested first since they are used to work out which variants to request for their dependencies.
          // The configurations requested here represent just what we know at this moment. Many of these modules will turn out to be
          // dependencies of others and will be visited sooner and the configurations created below will be discarded. This is fine since
          // `createRequestedModuleConfiguration()` is cheap.
          module.projectType == IdeAndroidProjectType.PROJECT_TYPE_APP -> 1
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
    // TODO(b/181028873): Better predict variants that needs to be prefetched. Add a new field to record the predicted variant.
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

  fun syncAllVariants(
    inputModules: List<AndroidModule>,
    syncOptions: AllVariantsSyncActionOptions,
    variantNameResolvers: (buildId: File, projectPath: String) -> VariantNameResolver
  ) {
    val variants =
      actionRunner
        .runActions(inputModules.flatMap { module ->
          module.allVariantNames.orEmpty().map { variant ->
            getVariantAction(ModuleConfiguration(module.id, variant, abi = null), variantNameResolvers)
          }
        })
        .filterNotNull()
        .groupBy { it.module }

    variants.entries.forEach { (module, result) ->
      module.allVariants = result.map {it.ideVariant}
    }
  }

  private class SyncVariantResultCore(
    val moduleConfiguration: ModuleConfiguration,
    val module: AndroidModule,
    val ideVariant: IdeVariant,
    val nativeVariantAbi: NativeVariantAbiResult,
    val unresolvedDependencies: List<IdeUnresolvedDependencies>
  )

  private class SyncVariantResult(
    val core: SyncVariantResultCore,
    val moduleDependencies: List<ModuleConfiguration>
  ) {
    val moduleConfiguration: ModuleConfiguration get() = core.moduleConfiguration
    val module: AndroidModule get() = core.module
    val ideVariant: IdeVariant get() = core.ideVariant
    val nativeVariantAbi: NativeVariantAbiResult get() = core.nativeVariantAbi
    val unresolvedDependencies: List<IdeUnresolvedDependencies> get() = core.unresolvedDependencies
  }

  private fun SyncVariantResultCore.getModuleDependencyConfigurations(selectedVariants: SelectedVariants): List<ModuleConfiguration> {
    val selectedVariantDetails = selectedVariants.selectedVariants[moduleConfiguration.id]?.details

    // Regardless of the current selection in the IDE we try to select the same ABI in all modules the "top" module depends on even
    // when intermediate modules do not have native code.
    val abiToPropagate = nativeVariantAbi.abi ?: moduleConfiguration.abi

    val newlySelectedVariantDetails = createVariantDetailsFrom(module.androidProject.flavorDimensions, ideVariant, nativeVariantAbi.abi)
    val variantDiffChange =
      VariantSelectionChange.extractVariantSelectionChange(from = newlySelectedVariantDetails, base = selectedVariantDetails)


    fun propagateVariantSelectionChangeFallback(dependencyModuleId: String): ModuleConfiguration? {
      val dependencyModule = androidModulesById[dependencyModuleId] ?: return null
      val dependencyModuleCurrentlySelectedVariant = selectedVariants.selectedVariants[dependencyModuleId]
      val dependencyModuleSelectedVariantDetails = dependencyModuleCurrentlySelectedVariant?.details

      val newSelectedVariantDetails = dependencyModuleSelectedVariantDetails?.applyChange(
        variantDiffChange ?: VariantSelectionChange.EMPTY, applyAbiMode = ApplyAbiSelectionMode.ALWAYS
      )
                                      ?: return null

      // Make sure the variant name we guessed in fact exists.
      if (dependencyModule.allVariantNames?.contains(newSelectedVariantDetails.name) != true) return null

      return ModuleConfiguration(dependencyModuleId, newSelectedVariantDetails.name, abiToPropagate)
    }

    fun generateDirectModuleDependencies(): List<ModuleConfiguration> {
      return (ideVariant.mainArtifact.level2Dependencies.moduleDependencies
              + ideVariant.unitTestArtifact?.level2Dependencies?.moduleDependencies.orEmpty()
              + ideVariant.androidTestArtifact?.level2Dependencies?.moduleDependencies.orEmpty()
              + ideVariant.testFixturesArtifact?.level2Dependencies?.moduleDependencies.orEmpty()
             )
        .mapNotNull { moduleDependency ->
          val dependencyProject = moduleDependency.projectPath
          val dependencyModuleId = Modules.createUniqueModuleId(moduleDependency.buildId ?: "", dependencyProject)
          val dependencyVariant = moduleDependency.variant
          if (dependencyVariant != null) {
            ModuleConfiguration(dependencyModuleId, dependencyVariant, abiToPropagate)
          }
          else {
            propagateVariantSelectionChangeFallback(dependencyModuleId)
          }
        }
        .distinct()
    }

    /**
     * Attempt to propagate variant changes to feature modules. This is not guaranteed to be correct, but since we do not know what the
     * real dependencies of each feature module variant are we can only guess.
     */
    fun generateDynamicFeatureDependencies(): List<ModuleConfiguration> {
      val rootProjectGradleDirectory = module.gradleProject.projectIdentifier.buildIdentifier.rootDir
      return module.androidProject.dynamicFeatures.mapNotNull { featureModuleGradlePath ->
        val featureModuleId = Modules.createUniqueModuleId(rootProjectGradleDirectory, featureModuleGradlePath)
        propagateVariantSelectionChangeFallback(featureModuleId)
      }
    }

    return generateDirectModuleDependencies() + generateDynamicFeatureDependencies()
  }

  /**
   * Given a [moduleConfiguration] returns an action that fetches variant specific models for the given module+variant and returns the set
   * of [SyncVariantResult]s containing the fetched models and describing resolved module configurations for the given module.
   */
  private fun getVariantAndModuleDependenciesAction(
    moduleConfiguration: ModuleConfiguration,
    selectedVariants: SelectedVariants,
    getVariantNameResolver: (buildId: File, projectPath: String) -> VariantNameResolver
  ): (BuildController) -> SyncVariantResult? {
    val getVariantAction = getVariantAction(moduleConfiguration, getVariantNameResolver)
    return fun(controller: BuildController): SyncVariantResult? {
      val syncVariantResultCore = getVariantAction(controller) ?: return null
      return SyncVariantResult(
        syncVariantResultCore,
        syncVariantResultCore.getModuleDependencyConfigurations(selectedVariants),
      )
    }
  }

  private fun getVariantAction(
    moduleConfiguration: ModuleConfiguration,
    variantNameResolvers: (buildId: File, projectPath: String) -> VariantNameResolver
  ): (BuildController) -> SyncVariantResultCore? {
    val module = androidModulesById[moduleConfiguration.id] ?: return { null }
    return fun(controller: BuildController): SyncVariantResultCore? {
      val ideVariant : IdeVariant?
      val abiToRequest : String?
      val nativeVariantAbi : NativeVariantAbiResult?
      var variantName = ""

      if (syncOptions.flags.studioFlagUseV2BuilderModels && canFetchV2Models == true) {
        // In V2, we get the variants from AndroidModule.v2Variants.
        val variant = module.v2Variants?.firstOrNull { it.name == moduleConfiguration.variant }
                      ?: error("Resolved variant '${moduleConfiguration.variant}' does not exist.")

        variantName = variant.name

        // Request VariantDependencies model for the variant's dependencies.
        val variantDependencies = controller.findVariantDependenciesV2Model(module.gradleProject, moduleConfiguration.variant) ?: return null
        ideVariant = modelCache.variantFrom(
          variant,
          variantDependencies,
          variantNameResolvers,
          module.buildNameMap ?: error("Build name map not available for: ${module.id}")
        )
      }
      else {
        val androidModuleId = ModuleId(module.gradleProject.path, module.gradleProject.projectIdentifier.buildIdentifier.rootDir.path)
        val adjustedVariantName = module.adjustForTestFixturesSuffix(moduleConfiguration.variant)
        val variant = controller.findVariantModel(module, adjustedVariantName) ?: return null
        variantName = variant.name
        ideVariant = modelCache.variantFrom(module.androidProject, variant, module.modelVersion, androidModuleId)
      }

      module.kotlinGradleModel = controller.findKotlinGradleModelForAndroidProject(module.findModelRoot, variantName)
      module.kaptGradleModel = controller.findKaptGradleModelForAndroidProject(module.findModelRoot, variantName)
      abiToRequest = chooseAbiToRequest(module, variantName, moduleConfiguration.abi)
      nativeVariantAbi = abiToRequest?.let {
        controller.findNativeVariantAbiModel(modelCache, module, variantName, it) } ?: NativeVariantAbiResult.None

      fun getUnresolvedDependencies(): List<IdeUnresolvedDependencies> {
         val unresolvedDependencies = mutableListOf<IdeUnresolvedDependencies>()
        unresolvedDependencies.addAll(ideVariant.mainArtifact.unresolvedDependencies)
        ideVariant.androidTestArtifact?.let { unresolvedDependencies.addAll(it.unresolvedDependencies) }
        ideVariant.testFixturesArtifact?.let { unresolvedDependencies.addAll(it.unresolvedDependencies) }
        ideVariant.unitTestArtifact?.let { unresolvedDependencies.addAll(it.unresolvedDependencies) }
        return unresolvedDependencies
      }

      return SyncVariantResultCore(
        moduleConfiguration,
        module,
        ideVariant,
        nativeVariantAbi,
        getUnresolvedDependencies()
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

  sealed class NativeVariantAbiResult {
    class V1(val variantAbi: IdeNativeVariantAbi) : NativeVariantAbiResult()
    class V2(val selectedAbiName: String) : NativeVariantAbiResult()
    object None : NativeVariantAbiResult()

    val abi: String? get() = when(this) {
      is V1 -> variantAbi.abi
      is V2 -> selectedAbiName
      None -> null
    }
  }

  private fun BuildController.findNativeVariantAbiModel(
    modelCache: ModelCache,
    module: AndroidModule,
    variantName: String,
    abiToRequest: String
  ): NativeVariantAbiResult {
    return if (module.nativeModelVersion == AndroidModule.NativeModelVersion.V2) {
      // V2 model is available, trigger the sync with V2 API
      // NOTE: Even though we drop the value returned the side effects of this code are important. Native sync creates file on the disk
      // which are later used.
      val model = findModel(module.findModelRoot, NativeModule::class.java, NativeModelBuilderParameter::class.java) { parameter ->
        parameter.variantsToGenerateBuildInformation = listOf(variantName)
        parameter.abisToGenerateBuildInformation = listOf(abiToRequest)
      }
      if (model != null) NativeVariantAbiResult.V2(abiToRequest) else NativeVariantAbiResult.None
    }
    else {
      // Fallback to V1 models otherwise.
      val model = findModel(module.findModelRoot, NativeVariantAbi::class.java, ModelBuilderParameter::class.java) { parameter ->
        parameter.setVariantName(variantName)
        parameter.setAbiName(abiToRequest)
      }
      if (model != null) NativeVariantAbiResult.V1(modelCache.nativeVariantAbiFrom(model)) else NativeVariantAbiResult.None
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
    if (module.nativeModelVersion == AndroidModule.NativeModelVersion.None) return null

    // Attempt to get the list of supported abiNames for this variant from the NativeAndroidProject
    // Otherwise return from this method with a null result as abis are not supported.
    val abiNames = module.getVariantAbiNames(variantName) ?: return null

    return (if (selectedAbi != null && abiNames.contains(selectedAbi)) selectedAbi else abiNames.getDefaultOrFirstItem("x86"))
           ?: throw AndroidSyncException("No valid Native abi found to request!")
  }
}

private val List<GradleBuild>.projects: Sequence<BasicGradleProject> get() = asSequence().flatMap { it.projects.asSequence() }
private val androidArtifactSuffixes = listOf("", "unitTest", "androidTest")

private fun Collection<SyncIssue>.toSyncIssueData(): List<IdeSyncIssue> {
  return map { syncIssue ->
    IdeSyncIssueImpl(
      message = syncIssue.message,
      data = syncIssue.data,
      multiLineMessage = ModelCache.safeGet(syncIssue::multiLineMessage, null)?.toList(),
      severity = syncIssue.severity,
      type = syncIssue.type
    )
  }
}

private fun Collection<com.android.builder.model.v2.ide.SyncIssue>.toV2SyncIssueData(): List<IdeSyncIssue> {
  return map { syncIssue ->
    IdeSyncIssueImpl(
      message = syncIssue.message,
      data = syncIssue.data,
      multiLineMessage = ModelCache.safeGet(syncIssue::multiLineMessage, null)?.filterNotNull()?.toList(),
      severity = syncIssue.severity,
      type = syncIssue.type
    )
  }
}

private fun createAndroidModule(
  gradleProject: BasicGradleProject,
  androidProjectResult: AndroidExtraModelProviderWorker.AndroidProjectResult,
  nativeAndroidProject: NativeAndroidProject?,
  nativeModule: NativeModule?,
  buildNameMap: Map<String, File>,
  modelCache: ModelCache
): AndroidModule {
  val gradleVersionString = when (androidProjectResult) {
    is AndroidExtraModelProviderWorker.AndroidProjectResult.V1Project -> ModelCache.safeGet(androidProjectResult.androidProject::getModelVersion, "")
    is AndroidExtraModelProviderWorker.AndroidProjectResult.V2Project -> ModelCache.safeGet(androidProjectResult.modelVersions::agp, "")
  }
  val modelVersion: GradleVersion? = GradleVersion.tryParseAndroidGradlePluginVersion(gradleVersionString)

  val ideAndroidProject = when (androidProjectResult) {
    is AndroidExtraModelProviderWorker.AndroidProjectResult.V1Project -> modelCache.androidProjectFrom(androidProjectResult.androidProject)
    is AndroidExtraModelProviderWorker.AndroidProjectResult.V2Project -> modelCache.androidProjectFrom(androidProjectResult.basicAndroidProject,
                                                                                                       androidProjectResult.androidProject,
                                                                                                       androidProjectResult.modelVersions,
                                                                                                       androidProjectResult.androidDsl)
  }

  val (basicVariants, v2Variants) = when (androidProjectResult) {
    is AndroidExtraModelProviderWorker.AndroidProjectResult.V1Project -> null to null
    is AndroidExtraModelProviderWorker.AndroidProjectResult.V2Project ->
      ModelCache.safeGet(androidProjectResult.basicAndroidProject::variants, null)?.toList() to ModelCache.safeGet(
        androidProjectResult.androidProject::variants, null)?.toList()
  }

  // Single-variant-sync models have variantNames property and all-variants-sync model should have all variants present instead.
  val allVariantNames: Set<String>? = when (androidProjectResult) {
    is AndroidExtraModelProviderWorker.AndroidProjectResult.V1Project ->
      ModelCache.safeGet(androidProjectResult.androidProject::getVariantNames, null).orEmpty().toSet()
    // For V2, the project always have allVariants because these do not contain any dependency data.
    is AndroidExtraModelProviderWorker.AndroidProjectResult.V2Project -> basicVariants?.map { it.name }?.toSet()
  }

  fun List<BasicVariant>.getDefaultVariant(
    buildTypes: List<BuildType>,
    productFlavors: List<ProductFlavor>,
    flavorDimensions: Collection<String>
  ) : String? {
    // Get the default buildType or fall back to debug if none isDefault.
    val defaultBuildTypeName = buildTypes.firstOrNull { it.isDefault == true } ?: "debug"

    val defaultFlavors = mutableListOf<String>()
    // Get the default product flavors in each dimension.
    for (flavorDimension in flavorDimensions) {
      val defaultProductFlavorName = productFlavors.firstOrNull { it.dimension == flavorDimension && it.isDefault == true }?.name ?:
                                 // if no productFlavor is marked isDefault within the dimension, then we get the first one in an alphabetical order.
                                 productFlavors.filter { it.dimension == flavorDimension }.minByOrNull { it.name }?.name

      if (defaultProductFlavorName != null) defaultFlavors.add(defaultProductFlavorName)
    }

    // Get the variants with buildType marked as default.
    val variants = this.filter { variant -> variant.buildType == defaultBuildTypeName }

    // Find the variant for which all the the productFlavors are marked as default.
    return variants.firstOrNull { variant -> defaultFlavors.containsAll(variant.productFlavors) }?.name
  }


  val defaultVariantName: String? = when (androidProjectResult) {
    is AndroidExtraModelProviderWorker.AndroidProjectResult.V1Project ->
      ModelCache.safeGet(androidProjectResult.androidProject::getDefaultVariant, null)
      ?: allVariantNames?.getDefaultOrFirstItem("debug")
    is AndroidExtraModelProviderWorker.AndroidProjectResult.V2Project -> {
      val flavorDimensions = androidProjectResult.androidDsl.flavorDimensions
      val productFlavors = androidProjectResult.androidDsl.productFlavors
      val buildTypes = androidProjectResult.androidDsl.buildTypes
      // Try to get the default variant based on default BuildTypes and productFlavors, otherwise get first one in the list.
      basicVariants?.getDefaultVariant(buildTypes, productFlavors, flavorDimensions) ?: allVariantNames?.getDefaultOrFirstItem("debug")
    }
  }

  val ideNativeAndroidProject = when (androidProjectResult) {
    is AndroidExtraModelProviderWorker.AndroidProjectResult.V1Project ->
      nativeAndroidProject?.let {
        modelCache.nativeAndroidProjectFrom(it, ModelCache.safeGet(androidProjectResult.androidProject::getNdkVersion, ""))
      }
    is AndroidExtraModelProviderWorker.AndroidProjectResult.V2Project ->
      if (nativeAndroidProject != null) {
        error("V2 models do not compatible with NativeAndroidProject. Please check your configuration.")
      } else {
        null
      }
  }
  val ideNativeModule = nativeModule?.let(modelCache::nativeModuleFrom)

  val basicVariantMap = basicVariants?.associateBy { it.name }.orEmpty()

  val androidModule = AndroidModule(
    modelVersion = modelVersion,
    buildName = androidProjectResult.buildName,
    buildNameMap = buildNameMap,
    gradleProject = gradleProject,
    androidProject = ideAndroidProject,
    allVariantNames = allVariantNames,
    defaultVariantName = defaultVariantName,
    v2Variants = v2Variants?.map { modelCache.variantFrom(
      androidProject = ideAndroidProject,
      basicVariant = basicVariantMap[it.name] ?: error("BasicVariant not found. Name: ${it.name}"),
      variant = it,
      modelVersion = modelVersion
    ) },
    nativeAndroidProject = ideNativeAndroidProject,
    nativeModule = ideNativeModule
  )

  @Suppress("DEPRECATION")
  val syncIssues = when (androidProjectResult) {
    is AndroidExtraModelProviderWorker.AndroidProjectResult.V1Project ->
      ModelCache.safeGet(androidProjectResult.androidProject::getSyncIssues, null)
    // For V2, we are going to request the Sync issues model after populating build models.
    is AndroidExtraModelProviderWorker.AndroidProjectResult.V2Project -> null
  }

  // It will be overridden if we receive something here but also a proper sync issues model later.
  if (syncIssues != null) androidModule.setSyncIssues(syncIssues.toSyncIssueData())

  return androidModule
}

private fun AndroidModule.adjustForTestFixturesSuffix(variantName: String): String {
  val allVariantNames = allVariantNames.orEmpty()
  val variantNameWithoutSuffix = variantName.removeSuffix("TestFixtures")
  return if (!allVariantNames.contains(variantName) && allVariantNames.contains(variantNameWithoutSuffix)) variantNameWithoutSuffix
  else variantName
}
