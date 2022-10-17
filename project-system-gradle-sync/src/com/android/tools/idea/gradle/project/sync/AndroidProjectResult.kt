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
import com.android.builder.model.SyncIssue
import com.android.builder.model.v2.ide.BasicVariant
import com.android.builder.model.v2.ide.Variant
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.Versions
import com.android.ide.gradle.model.LegacyApplicationIdModel
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeVariantAbi
import org.gradle.tooling.BuildController

sealed class AndroidProjectResult {
  class V1Project(
    val modelCache: ModelCache.V1,
    rootBuildId: BuildId,
    buildId: BuildId,
    override val buildName: String,
    projectPath: String,
    androidProject: AndroidProject,
    override val legacyApplicationIdModel: LegacyApplicationIdModel?,
  ) : AndroidProjectResult() {
    override val agpVersion: String = safeGet(androidProject::getModelVersion, "")
    override val ideAndroidProject: IdeAndroidProjectImpl =
      modelCache.androidProjectFrom(rootBuildId, buildId, buildName, projectPath, androidProject, legacyApplicationIdModel)
    override val allVariantNames: Set<String> = safeGet(androidProject::getVariantNames, null).orEmpty().toSet()
    override val defaultVariantName: String? = safeGet(androidProject::getDefaultVariant, null)
                                               ?: allVariantNames.getDefaultOrFirstItem("debug")
    val syncIssues: Collection<SyncIssue>? = @Suppress("DEPRECATION") (safeGet(androidProject::getSyncIssues, null))
    val ndkVersion: String? = safeGet(androidProject::getNdkVersion, null)

    override fun createVariantFetcher(): IdeVariantFetcher = v1VariantFetcher(modelCache, legacyApplicationIdModel)
  }

  class V2Project(
    val modelCache: ModelCache.V2,
    rootBuildId: BuildId,
    buildId: BuildId,
    basicAndroidProject: BasicAndroidProject,
    androidProject: com.android.builder.model.v2.models.AndroidProject,
    modelVersions: Versions,
    androidDsl: AndroidDsl,
    override val legacyApplicationIdModel: LegacyApplicationIdModel?,
  ) : AndroidProjectResult() {
    override val buildName: String = basicAndroidProject.buildName
    override val agpVersion: String = modelVersions.agp
    override val ideAndroidProject: IdeAndroidProjectImpl =
      modelCache.androidProjectFrom(
        rootBuildId = rootBuildId,
        buildId = buildId,
        basicProject = basicAndroidProject,
        project = androidProject,
        androidVersion = modelVersions,
        androidDsl = androidDsl,
        legacyApplicationIdModel = legacyApplicationIdModel
      )
    private val basicVariants: List<BasicVariant> = basicAndroidProject.variants.toList()

    private val v2Variants: List<IdeVariantCoreImpl> = let {
      val v2Variants: List<Variant> = androidProject.variants.toList()
      val basicVariantMap = basicVariants.associateBy { it.name }

      v2Variants.map {
        modelCache.variantFrom(
          androidProject = ideAndroidProject,
          basicVariant = basicVariantMap[it.name] ?: error("BasicVariant not found. Name: ${it.name}"),
          variant = it,
          legacyApplicationIdModel = legacyApplicationIdModel
        )
      }
    }

    override val allVariantNames: Set<String> = basicVariants.map { it.name }.toSet()
    override val defaultVariantName: String? =
      // Try to get the default variant based on default BuildTypes and productFlavors, otherwise get first one in the list.
      basicVariants.getDefaultVariant(androidDsl.buildTypes, androidDsl.productFlavors)
    val androidVariantResolver: AndroidVariantResolver = buildVariantNameResolver(ideAndroidProject, v2Variants)

    override fun createVariantFetcher(): IdeVariantFetcher = v2VariantFetcher(modelCache, v2Variants)
  }

  abstract val buildName: String
  abstract val agpVersion: String
  abstract val ideAndroidProject: IdeAndroidProjectImpl
  abstract val allVariantNames: Set<String>
  abstract val defaultVariantName: String?
  abstract fun createVariantFetcher(): IdeVariantFetcher
  abstract val legacyApplicationIdModel: LegacyApplicationIdModel?
}

sealed class NativeVariantAbiResult {
  class V1(val variantAbi: IdeNativeVariantAbi) : NativeVariantAbiResult()
  class V2(val selectedAbiName: String) : NativeVariantAbiResult()
  object None : NativeVariantAbiResult()

  val abi: String?
    get() = when (this) {
      is V1 -> variantAbi.abi
      is V2 -> selectedAbiName
      None -> null
    }
}

// Keep fetchers outside of AndroidProjectResult to avoid accidental references on larger builder models.
private fun v1VariantFetcher(modelCache: ModelCache.V1, legacyApplicationIdModel: LegacyApplicationIdModel?): IdeVariantFetcher {
  return fun(
    controller: BuildController,
    androidProjectPathResolver: AndroidProjectPathResolver,
    module: AndroidModule,
    configuration: ModuleConfiguration
  ): IdeVariantWithPostProcessor? {
    val androidModuleId = module.gradleProject.toModuleId()
    val adjustedVariantName = module.adjustForTestFixturesSuffix(configuration.variant)
    val variant = controller.findVariantModel(module, adjustedVariantName) ?: return null
    return modelCache.variantFrom(module.androidProject, variant, legacyApplicationIdModel, module.agpVersion, androidModuleId)
  }
}

// Keep fetchers outside of AndroidProjectResult to avoid accidental references on larger builder models.
private fun v2VariantFetcher(modelCache: ModelCache.V2, v2Variants: List<IdeVariantCoreImpl>): IdeVariantFetcher {
  return fun(
    controller: BuildController,
    androidProjectPathResolver: AndroidProjectPathResolver,
    module: AndroidModule,
    configuration: ModuleConfiguration
  ): IdeVariantWithPostProcessor? {
    // In V2, we get the variants from AndroidModule.v2Variants.
    val variant = v2Variants.firstOrNull { it.name == configuration.variant }
      ?: error("Resolved variant '${configuration.variant}' does not exist.")

    // Request VariantDependencies model for the variant's dependencies.
    val variantDependencies = controller.findVariantDependenciesV2Model(module.gradleProject, configuration.variant) ?: return null
    return modelCache.variantFrom(
      BuildId(module.gradleProject.projectIdentifier.buildIdentifier.rootDir),
      module.gradleProject.projectIdentifier.projectPath,
      variant,
      variantDependencies,
      androidProjectPathResolver,
      module.buildNameMap
    )
  }
}

private fun AndroidModule.adjustForTestFixturesSuffix(variantName: String): String {
  val allVariantNames = allVariantNames.orEmpty()
  val variantNameWithoutSuffix = variantName.removeSuffix("TestFixtures")
  return if (!allVariantNames.contains(variantName) && allVariantNames.contains(variantNameWithoutSuffix)) variantNameWithoutSuffix
  else variantName
}
