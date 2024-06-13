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
import com.android.builder.model.ModelBuilderParameter
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.Variant
import com.android.builder.model.v2.ide.ArtifactDependencies
import com.android.builder.model.v2.ide.ArtifactDependenciesAdjacencyList
import com.android.builder.model.v2.ide.Edge
import com.android.builder.model.v2.ide.GraphItem
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.UnresolvedDependency
import com.android.builder.model.v2.models.ClasspathParameterConfig
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.VariantDependenciesAdjacencyList
import com.android.builder.model.v2.models.ndk.NativeModelBuilderParameter
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.utils.appendCapitalized
import org.gradle.tooling.BuildController
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptGradleModel
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService

/**
 * Gets the [V2AndroidProject] or [ModelVersions] (based on [modelType]) for the given [BasicGradleProject].
 */
internal fun <T> BuildController.findNonParameterizedV2Model(
  project: BasicGradleProject,
  modelType: Class<T>
): T? {
  return findModel(project, modelType)
}

/**
 * Gets the [AndroidProject] or [NativeAndroidProject] (based on [modelType]) for the given [BasicGradleProject].
 */
internal fun <T> BuildController.findParameterizedAndroidModel(
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

internal fun BuildController.findVariantModel(
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

internal fun getClasspathConfigForProject(
  skipRuntimeClasspathForLibrariesFlag: Boolean,
  projectType: IdeAndroidProjectType,
  hasNoInboundDependencies: Boolean
): ClasspathParameterConfig = when {
  hasNoInboundDependencies -> ClasspathParameterConfig.ALL
  skipRuntimeClasspathForLibrariesFlag && projectType == IdeAndroidProjectType.PROJECT_TYPE_LIBRARY ->
    ClasspathParameterConfig.ANDROID_TEST_ONLY
  else -> ClasspathParameterConfig.ALL
}

/**
 * Valid only for [VariantDependencies] using model parameter for the given [BasicGradleProject] using .
 */
internal fun BuildController.findVariantDependenciesV2Model(
  project: BasicGradleProject,
  modelVersions: ModelVersions,
  variantName: String,
  useNewDependencyGraphModel: Boolean,
  classpathConfig: ClasspathParameterConfig
): VariantDependenciesCompat? {
  fun <T> findModel(clazz: Class<T>, classpathConfig: ClasspathParameterConfig) = findModel(
    project,
    clazz,
    com.android.builder.model.v2.models.ModelBuilderParameter::class.java
  ) {
    it.variantName = variantName
    classpathConfig.applyTo(it)
  }

  return if (useNewDependencyGraphModel) {
    findModel(VariantDependenciesAdjacencyList::class.java, classpathConfig)?.let {
      VariantDependenciesCompat.AdjacencyList(it, modelVersions)
    }
  } else {
    findModel(VariantDependencies::class.java, classpathConfig)?.let {
      VariantDependenciesCompat.GraphItemList(it, modelVersions)
    }
  }
}

internal fun BuildController.findNativeVariantAbiModel(
  modelCache: ModelCache.V1,
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

internal fun BuildController.findNativeModuleModel(
  project: BasicGradleProject,
  syncAllVariantsAndAbis: Boolean
): NativeModule? {
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

private val androidArtifactSuffixes = listOf("", "unitTest", "androidTest", "screenshotTest")

/** Kotlin related models that are fetched when importing Android projects. */
internal data class AllKotlinModels(val kotlinModel: KotlinGradleModel?, val kaptModel: KaptGradleModel?)

internal fun BuildController.findKotlinModelsForAndroidProject(root: Model, variantName: String): AllKotlinModels {
  val kotlinModel = findModel(root, KotlinGradleModel::class.java, ModelBuilderService.Parameter::class.java) {
    it.value = androidArtifactSuffixes.joinToString(separator = ",") { artifactSuffix -> variantName.appendCapitalized(artifactSuffix) }
  }
  val kaptModel = findModel(root, KaptGradleModel::class.java, ModelBuilderService.Parameter::class.java) {
    it.value = androidArtifactSuffixes.joinToString(separator = ",") { artifactSuffix -> variantName.appendCapitalized(artifactSuffix) }
  }
  return AllKotlinModels(kotlinModel, kaptModel)
}

sealed class VariantDependenciesCompat(
  val mainArtifact: ArtifactDependenciesCompat,
  val deviceTestArtifacts: Map<IdeArtifactName, ArtifactDependenciesCompat>,
  val hostTestArtifacts: Map<IdeArtifactName, ArtifactDependenciesCompat>,
  val testFixturesArtifact: ArtifactDependenciesCompat?,
  val libraries: Map<String, Library>,
) {
  class AdjacencyList(variantDependencies: VariantDependenciesAdjacencyList, modelVersions: ModelVersions) : VariantDependenciesCompat(
    ArtifactDependenciesCompat.AdjacencyList(variantDependencies.mainArtifact),
    if (modelVersions[ModelFeature.HAS_SCREENSHOT_TESTS_SUPPORT]) {
      variantDependencies.deviceTestArtifacts.map { (k, v) ->
        convertArtifactName(k) to ArtifactDependenciesCompat.AdjacencyList(v)
      }.toMap()
    } else {
      variantDependencies.androidTestArtifact?.let {
        mapOf(IdeArtifactName.ANDROID_TEST to ArtifactDependenciesCompat.AdjacencyList(it))
                                                   } ?: emptyMap()
    },
    if (modelVersions[ModelFeature.HAS_SCREENSHOT_TESTS_SUPPORT]) {
      variantDependencies.hostTestArtifacts.map { (k, v) ->
        convertArtifactName(k) to ArtifactDependenciesCompat.AdjacencyList(v)
      }.toMap()
    } else {
      variantDependencies.unitTestArtifact?.let {
        mapOf( IdeArtifactName.UNIT_TEST to ArtifactDependenciesCompat.AdjacencyList(it))
      }?: emptyMap()
    },
    variantDependencies.testFixturesArtifact?.let { ArtifactDependenciesCompat.AdjacencyList(it) },
    variantDependencies.libraries
  )
  class GraphItemList(variantDependencies: VariantDependencies, modelVersions: ModelVersions) : VariantDependenciesCompat(
    ArtifactDependenciesCompat.GraphItemList(variantDependencies.mainArtifact),
    if (modelVersions[ModelFeature.HAS_SCREENSHOT_TESTS_SUPPORT]) {
      variantDependencies.deviceTestArtifacts.map { (k, v) ->
        convertArtifactName(k) to ArtifactDependenciesCompat.GraphItemList(v)
      }.toMap()
    } else {
      variantDependencies.androidTestArtifact?.let {
        mapOf(IdeArtifactName.ANDROID_TEST to ArtifactDependenciesCompat.GraphItemList(it))
      } ?: emptyMap()

    },
    if (modelVersions[ModelFeature.HAS_SCREENSHOT_TESTS_SUPPORT]) {
      variantDependencies.hostTestArtifacts.map { (k, v) ->
        convertArtifactName(k) to ArtifactDependenciesCompat.GraphItemList(v)
      }.toMap()
    } else {
      variantDependencies.unitTestArtifact?.let {
        mapOf( IdeArtifactName.UNIT_TEST to ArtifactDependenciesCompat.GraphItemList(it))
      }?: emptyMap()
    },
    variantDependencies.testFixturesArtifact?.let { ArtifactDependenciesCompat.GraphItemList(it) },
    variantDependencies.libraries,
  )
}

sealed class ArtifactDependenciesCompat(val compileDependencies: DependencyGraphCompat,
                                        val runtimeDependencies: DependencyGraphCompat?,
                                        val unresolvedDependencies: List<UnresolvedDependency>) {
  class AdjacencyList(artifactDependencies: ArtifactDependenciesAdjacencyList) : ArtifactDependenciesCompat(
    DependencyGraphCompat.AdjacencyList(artifactDependencies.compileDependencies),
    artifactDependencies.runtimeDependencies?.let {DependencyGraphCompat.AdjacencyList(it)},
    artifactDependencies.unresolvedDependencies
  )
  class GraphItemList(artifactDependencies: ArtifactDependencies) : ArtifactDependenciesCompat(
    DependencyGraphCompat.GraphItemList(artifactDependencies.compileDependencies),
    artifactDependencies.runtimeDependencies?.let {DependencyGraphCompat.GraphItemList(it)},
    artifactDependencies.unresolvedDependencies
  )
}

sealed class DependencyGraphCompat {
  data class AdjacencyList(val edges: List<Edge>) : DependencyGraphCompat()
  data class GraphItemList(val graphItems: List<GraphItem>) : DependencyGraphCompat()
}