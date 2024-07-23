/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.builder.model.AndroidProject
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.ide.common.repository.AgpVersion
import com.android.ide.gradle.model.GradlePropertiesModel
import com.android.ide.gradle.model.LegacyAndroidGradlePluginProperties
import com.android.ide.gradle.model.LegacyAndroidGradlePluginPropertiesModelParameters
import com.android.tools.idea.gradle.project.sync.AndroidProjectResult.Companion.RuntimeClasspathBehaviour
import com.android.tools.idea.gradle.project.sync.ModelResult.Companion.ignoreExceptionsAndGet
import com.android.tools.idea.gradle.project.sync.ModelResult.Companion.mapCatching
import com.google.common.collect.ImmutableRangeSet
import com.google.common.collect.Range
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptGradleModel


/**
 * The container class of modules we couldn't fetch using parallel Gradle TAPI API.
 * For now this list has :
 *  - All the non-Android modules
 *  - The android modules using an older AGP version than the minimum supported for V2 sync
 */
internal sealed class BasicIncompleteGradleModule(
  val gradleProject: BasicGradleProject,
  val buildPath: String
) {
  val buildId: BuildId get() = BuildId(gradleProject.projectIdentifier.buildIdentifier.rootDir)
  val projectPath: String get() = gradleProject.path

  abstract fun getGradleModuleAction(
    internedModels: InternedModels,
    buildInfo: BuildInfo
  ): ActionToRun<GradleModule>
}

/** The information about the model consumer version required by AGP */
data class ModelConsumerVersion(val major: Int, val minor: Int, val description: String) : Comparable<ModelConsumerVersion> {
  override fun compareTo(other: ModelConsumerVersion): Int {
    return if (this.major != other.major) this.major.compareTo(other.major) else this.minor.compareTo(other.minor)
  }
}

data class ModelVersion(val major: Int, val minor: Int = 0, val description: String = "") : Comparable<ModelVersion> {
  override fun compareTo(other: ModelVersion): Int {
    return if (this.major != other.major) this.major.compareTo(other.major) else this.minor.compareTo(other.minor)
  }
}

enum class ModelFeature(
  private val enabledForModelVersions: ImmutableRangeSet<ModelVersion>,
  private val alsoEnabledForAgpVersions: ImmutableRangeSet<AgpVersion> = ImmutableRangeSet.of(),
  private val disabledForAgpVersions: ImmutableRangeSet<AgpVersion> = ImmutableRangeSet.of(),
) {
  SUPPORTS_ADDITIONAL_CLASSIFIER_ARTIFACTS_MODEL(AgpVersion.parse("3.5.0")),
  HAS_INSTANT_APP_COMPATIBLE_IN_V1_MODELS(AgpVersion.parse("3.3.0-alpha10")),
  HAS_V2_MODELS(AgpVersion.parse("7.2.0-alpha01")),
  HAS_SOURCES_JAVADOC_AND_SAMPLES_IN_VARIANT_DEPENDENCIES(AgpVersion.parse("8.1.0-alpha08")),
  SUPPORTS_PARALLEL_SYNC(
    enabledForModelVersions = ImmutableRangeSet.of(Range.atLeast(ModelVersion(8, 0))),
    alsoEnabledForAgpVersions = ImmutableRangeSet.builder<AgpVersion>().add(Range.atLeast(AgpVersion.parse("7.3.0-alpha04"))).add(
      Range.closedOpen(AgpVersion.parse("7.2.0"), AgpVersion.parse("7.3.0-alpha01"))).build()),
  HAS_NAMESPACE(AgpVersion.parse("7.0.0")),
  HAS_APPLICATION_ID(AgpVersion.parse("7.4.0-alpha04")),
  HAS_PRIVACY_SANDBOX_SDK_INFO(AgpVersion.parse("8.3.0-alpha14")),
  HAS_GENERATED_CLASSPATHS(AgpVersion.parse("8.2.0-alpha07")),
  HAS_BYTECODE_TRANSFORMS(AgpVersion.parse("8.3.0-alpha14")),
  HAS_DESUGARED_METHOD_FILES_PROJECT_GLOBAL(AgpVersion.parse("7.3.0-alpha06")),
  HAS_DESUGARED_METHOD_FILES_PER_ARTIFACT(AgpVersion.parse("8.0.0-alpha02")),
  HAS_DESUGAR_LIB_CONFIG(AgpVersion.parse("8.1.0-alpha05")),
  HAS_LINT_JAR_IN_ANDROID_PROJECT(AgpVersion.parse("8.4.0-alpha06")),
  HAS_BASELINE_PROFILE_DIRECTORIES(AgpVersion.parse("8.0.0-beta01")),
  HAS_RUN_TEST_IN_SEPARATE_PROCESS(AgpVersion.parse("8.3.0-alpha11")),
  USES_ABSOLUTE_GRADLE_BUILD_PATHS_IN_DEPENDENCY_MODEL(AgpVersion.parse("8.2.0-alpha13")),
  HAS_ADJACENCY_LIST_DEPENDENCY_GRAPH(AgpVersion.parse("8.2.0-alpha03")),
  HAS_SCREENSHOT_TESTS_SUPPORT(AgpVersion.parse("8.4.0-alpha07")),
  HAS_EXPERIMENTAL_PROPERTIES(AgpVersion.parse("8.6.0-alpha01")),
  HAS_DATA_BINDING(ModelVersion(9)),
  HAS_PROJECT_GRAPH_MODEL(ModelVersion(10, 0));

  init {
    check(!enabledForModelVersions.isEmpty) { "All features should be enabled for some model versions" }
    check(alsoEnabledForAgpVersions.intersection(disabledForAgpVersions).isEmpty) { """
      AGP based enable and disable flags must be distinct for $name.
           alsoEnabledForAgpVersions = $alsoEnabledForAgpVersions
           disabledForAgpVersions = $disabledForAgpVersions
           overlap:  ${alsoEnabledForAgpVersions.intersection(disabledForAgpVersions)}
      """.trimIndent()
    }
  }

  constructor(minimumModelVersion: ModelVersion): this(enabledForModelVersions = ImmutableRangeSet.of(Range.atLeast(minimumModelVersion)))

  @Deprecated("All new features should be gated on Model version, not AGP version")
  constructor(legacyMinimumAgpVersion: AgpVersion) : this(
    enabledForModelVersions = ImmutableRangeSet.of(Range.atLeast(ModelVersion(8, 9))), // Implicitly all new checks should use model version
    alsoEnabledForAgpVersions = ImmutableRangeSet.of(Range.atLeast(legacyMinimumAgpVersion)))

  internal fun appliesTo(modelVersion: ModelVersion, agpVersion: AgpVersion): Boolean {
    return (enabledForModelVersions.contains(modelVersion) && !disabledForAgpVersions.contains(agpVersion)) || alsoEnabledForAgpVersions.contains(agpVersion)
  }

}

data class ModelVersions(
  private val agp: AgpVersion,
  private val modelVersion: ModelVersion,
  private val minimumModelConsumer: ModelConsumerVersion?
) {

  private val features: BooleanArray = ModelFeature.values().map { it.appliesTo(modelVersion, agp) }.toBooleanArray()
  operator fun get(feature: ModelFeature): Boolean = features[feature.ordinal]

  fun checkAgpVersionCompatibility(syncFlags: GradleSyncStudioFlags) {
    checkAgpVersionCompatibility(minimumModelConsumer, agp, syncFlags)
  }

  val agpVersionAsString: String = agp.toString()
}

private fun getLegacyAndroidGradlePluginProperties(controller: BuildController,
                                                   gradleProject: BasicGradleProject,
                                                   modelVersions: ModelVersions): LegacyAndroidGradlePluginProperties? {
  if (modelVersions[ModelFeature.HAS_APPLICATION_ID] && modelVersions[ModelFeature.HAS_NAMESPACE] && modelVersions[ModelFeature.HAS_DATA_BINDING]) return null // Only fetch the model if it is needed.
  return controller.findModel(gradleProject, LegacyAndroidGradlePluginProperties::class.java,
                              LegacyAndroidGradlePluginPropertiesModelParameters::class.java) {
    it.componentToApplicationIdMap = !modelVersions[ModelFeature.HAS_APPLICATION_ID]
    it.namespace = !modelVersions[ModelFeature.HAS_NAMESPACE]
    it.dataBinding = !modelVersions[ModelFeature.HAS_DATA_BINDING]
  }
}
/**
 * The container class of Android modules.
 */
internal sealed class BasicIncompleteAndroidModule(gradleProject: BasicGradleProject, buildPath: String, val modelVersions: ModelVersions)
  :  BasicIncompleteGradleModule(gradleProject, buildPath) {
}

/**
 *  The container class of Android modules that can be fetched using V1 builder models.
 *  legacyV1AgpVersion: The model that contains the agp version used by the AndroidProject. This can be null if the AndroidProject is using
 *  an AGP version lower than the minimum supported version by Android Studio
 */
internal class BasicV1AndroidModuleGradleProject(
  gradleProject: BasicGradleProject,
  buildPath: String,
  modelVersions: ModelVersions,
) :  BasicIncompleteAndroidModule(gradleProject, buildPath, modelVersions) {

  override fun getGradleModuleAction(
    internedModels: InternedModels,
    buildInfo: BuildInfo
  ): ActionToRun<GradleModule> {
    return ActionToRun(
      fun(controller: BuildController): GradleModule {
        val androidProject = controller.findParameterizedAndroidModel(
          gradleProject,
          AndroidProject::class.java,
          shouldBuildVariant = false
        ) ?: error("Cannot fetch AndroidProject models for V1 projects.")

        val legacyAndroidGradlePluginProperties = getLegacyAndroidGradlePluginProperties(controller, gradleProject, modelVersions)
        val gradlePropertiesModel = controller.findModel(gradleProject, GradlePropertiesModel::class.java)
          ?: error("Cannot get GradlePropertiesModel (V1) for project '$gradleProject'")

        val modelCache = modelCacheV1Impl(internedModels, buildInfo.buildFolderPaths)
        val buildId = BuildId(gradleProject.projectIdentifier.buildIdentifier.rootDir)
        val rootBuildId = buildInfo.buildPathMap[":"] ?: error("Root build (':') not found")
        val androidProjectResult = AndroidProjectResult.V1Project(
          modelCache = modelCache,
          rootBuildId = rootBuildId,
          buildId = buildId,
          projectPath = gradleProject.path,
          androidProject = androidProject,
          legacyAndroidGradlePluginProperties = legacyAndroidGradlePluginProperties,
          gradlePropertiesModel = gradlePropertiesModel
        )

        return androidProjectResult
          .mapCatching { androidProjectResult ->
            val nativeModule = controller.findNativeModuleModel(gradleProject, syncAllVariantsAndAbis = false)
            val nativeAndroidProject: NativeAndroidProject? =
              if (nativeModule == null)
                controller.findParameterizedAndroidModel(
                  gradleProject, NativeAndroidProject::class.java,
                  shouldBuildVariant = false
                )
              else null

            createAndroidModuleV1(
              modelVersions,
              gradleProject,
              androidProjectResult,
              nativeAndroidProject,
              nativeModule,
              buildInfo.buildPathMap,
              modelCache
            )
          }
          .let {
            val result = it.ignoreExceptionsAndGet()
            // If we were unable to create an AndroidModule we have enough data to create a JavaModule. This is a fallback allowing users
            // access to at least build configuration files.
              ?: JavaModule(gradleProject, kotlinGradleModel = null, kaptGradleModel = null)
            result.recordExceptions(it.exceptions)
            result
          }
      },
      fetchesV1Models = true,
      fetchesKotlinModels = true
    )
  }
}

/**
 * The container class of Android modules that can be fetched using V2 builder models.
 */
internal class BasicV2AndroidModuleGradleProject(
  gradleProject: BasicGradleProject,
  buildPath: String,
  modelVersions: ModelVersions,
  val syncActionOptions: SyncActionOptions,
) : BasicIncompleteAndroidModule(gradleProject, buildPath, modelVersions) {


  override fun getGradleModuleAction(
    internedModels: InternedModels,
    buildInfo: BuildInfo,
  ): ActionToRun<GradleModule> {
    return ActionToRun(
      fun(controller: BuildController): GradleModule {
        val basicAndroidProject = controller.findNonParameterizedV2Model(gradleProject, BasicAndroidProject::class.java)
          ?: error("Cannot get BasicAndroidProject model for $gradleProject")
        val androidProject = controller.findNonParameterizedV2Model(gradleProject, com.android.builder.model.v2.models.AndroidProject::class.java)
          ?: error("Cannot get V2AndroidProject model for $gradleProject")
        val androidDsl = controller.findNonParameterizedV2Model(gradleProject, AndroidDsl::class.java)
          ?: error("Cannot get AndroidDsl model for $gradleProject")
        val legacyAndroidGradlePluginProperties = getLegacyAndroidGradlePluginProperties(controller, gradleProject, modelVersions)
        val gradlePropertiesModel = controller.findModel(gradleProject, GradlePropertiesModel::class.java)
          ?: error("Cannot get GradlePropertiesModel (V2) for project '$gradleProject'")

        val modelCache = modelCacheV2Impl(internedModels, modelVersions, syncActionOptions.syncTestMode,
                                          syncActionOptions.flags.studioFlagMultiVariantAdditionalArtifactSupport)
        val rootBuildId = buildInfo.buildPathMap[":"] ?: error("Root build (':') not found")
        val androidProjectResult =
          AndroidProjectResult.V2Project(
            modelCache = modelCache,
            rootBuildId = rootBuildId,
            buildId = BuildId(gradleProject.projectIdentifier.buildIdentifier.rootDir),
            basicAndroidProject = basicAndroidProject,
            androidProject = androidProject,
            modelVersions = modelVersions,
            androidDsl = androidDsl,
            legacyAndroidGradlePluginProperties = legacyAndroidGradlePluginProperties,
            gradlePropertiesModel = gradlePropertiesModel,
            runtimeClasspathBehaviour = RuntimeClasspathBehaviour(
              skipRuntimeClasspathForLibraries = syncActionOptions.flags.studioFlagSkipRuntimeClasspathForLibraries
                                                 && shouldSkipRuntimeClasspathForLibraries (androidProject.flags, gradlePropertiesModel),
              buildRuntimeClasspathForLibraryUnitTests = syncActionOptions.flags.studioFlagBuildRuntimeClasspathForLibraryUnitTests,
              buildRuntimeClasspathForLibraryScreenshotTests = syncActionOptions.flags.studioFlagBuildRuntimeClasspathForLibraryScreenshotTests
            ),
            useNewDependencyGraphModel = syncActionOptions.flags.studioFlagUseNewDependencyGraphModel
                                         && modelVersions[ModelFeature.HAS_ADJACENCY_LIST_DEPENDENCY_GRAPH]
          )

        return androidProjectResult.mapCatching { androidProjectResult ->
          // TODO(solodkyy): Perhaps request the version interface depending on AGP version.
          val nativeModule = controller.findNativeModuleModel(gradleProject, syncAllVariantsAndAbis = false)

          createAndroidModuleV2(
            modelVersions,
            gradleProject,
            androidProjectResult,
            nativeModule,
            buildInfo.buildPathMap,
            modelCache
          )
        }
          .let {
            val result = it.ignoreExceptionsAndGet()
            // If we were unable to create an AndroidModule we have enough data to create a JavaModule. This is a fallback allowing users
            // access to at least build configuration files.
            // TODO(b/254045637): Provide a fallback in the case when `BasicAndroidProject` is available but `AndroidProject` is not.
              ?: JavaModule(gradleProject, kotlinGradleModel = null, kaptGradleModel = null)
            result.recordExceptions(it.exceptions)
            result
          }
      },
      fetchesV2Models = true
    )
  }

  private fun shouldSkipRuntimeClasspathForLibraries(flags: AndroidGradlePluginProjectFlags, gradlePropertiesModel: GradlePropertiesModel) =
    AndroidGradlePluginProjectFlags.BooleanFlag.EXCLUDE_LIBRARY_COMPONENTS_FROM_CONSTRAINTS.getValue(flags, gradlePropertiesModel.excludeLibraryComponentsFromConstraints)

}

/**
 * The container class of non-Android modules.
 */
internal class BasicNonAndroidIncompleteGradleModule(gradleProject: BasicGradleProject, buildPath: String) :
  BasicIncompleteGradleModule(gradleProject, buildPath) {
  override fun getGradleModuleAction(
    internedModels: InternedModels,
    buildInfo: BuildInfo
  ): ActionToRun<GradleModule> {
    return ActionToRun(
      fun(controller: BuildController): GradleModule {
        val kotlinGradleModel = controller.findModel(gradleProject, KotlinGradleModel::class.java)
        val kaptGradleModel = controller.findModel(gradleProject, KaptGradleModel::class.java)
        return JavaModule(gradleProject, kotlinGradleModel, kaptGradleModel)
      },
      fetchesV1Models = false,
      fetchesKotlinModels = true
    )
  }
}

private fun createAndroidModuleV1(
  modelVersions: ModelVersions,
  gradleProject: BasicGradleProject,
  androidProjectResult: AndroidProjectResult.V1Project,
  nativeAndroidProject: NativeAndroidProject?,
  nativeModule: NativeModule?,
  buildPathMap: Map<String, BuildId>,
  modelCache: ModelCache.V1
): AndroidModule {
  val ideAndroidProject = androidProjectResult.ideAndroidProject
  val allVariantNames = androidProjectResult.allVariantNames
  val defaultVariantName: String? = androidProjectResult.defaultVariantName

  val ideNativeAndroidProject = nativeAndroidProject?.let {
    modelCache.nativeAndroidProjectFrom(it, androidProjectResult.ndkVersion)
  }
  val ideNativeModule = nativeModule?.let(modelCache::nativeModuleFrom)

  val androidModule = AndroidModule.V1(
    modelVersions = modelVersions,
    buildPathMap = buildPathMap,
    gradleProject = gradleProject,
    androidProject = ideAndroidProject,
    allVariantNames = allVariantNames,
    defaultVariantName = defaultVariantName,
    variantFetcher = androidProjectResult.createVariantFetcher(),
    nativeAndroidProject = ideNativeAndroidProject,
    nativeModule = ideNativeModule,
    legacyAndroidGradlePluginProperties = androidProjectResult.legacyAndroidGradlePluginProperties,
  )

  val syncIssues = androidProjectResult.syncIssues
  // It will be overridden if we receive something here but also a proper sync issues model later.
  if (syncIssues != null) {
    androidModule.setSyncIssues(syncIssues.toSyncIssueData() + androidModule.legacyAndroidGradlePluginProperties.getProblemsAsSyncIssues())
  }

  return androidModule
}

private fun createAndroidModuleV2(
  modelVersions: ModelVersions,
  gradleProject: BasicGradleProject,
  androidProjectResult: AndroidProjectResult.V2Project,
  nativeModule: NativeModule?,
  buildPathMap: Map<String, BuildId>,
  modelCache: ModelCache
): AndroidModule {

  val ideAndroidProject = androidProjectResult.ideAndroidProject
  val allVariantNames = androidProjectResult.allVariantNames
  val defaultVariantName: String? = androidProjectResult.defaultVariantName

  val ideNativeModule = nativeModule?.let(modelCache::nativeModuleFrom)

  return AndroidModule.V2(
    modelVersions = modelVersions,
    buildPathMap = buildPathMap,
    gradleProject = gradleProject,
    androidProject = ideAndroidProject,
    allVariantNames = allVariantNames,
    defaultVariantName = defaultVariantName,
    androidVariantResolver = androidProjectResult.androidVariantResolver,
    variantFetcher = androidProjectResult.createVariantFetcher(),
    nativeModule = ideNativeModule,
    legacyAndroidGradlePluginProperties = androidProjectResult.legacyAndroidGradlePluginProperties,
  )
}
