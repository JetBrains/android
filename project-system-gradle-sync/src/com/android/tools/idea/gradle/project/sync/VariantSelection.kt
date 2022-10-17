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

import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.tools.idea.gradle.model.IdePreResolvedModuleLibrary
import com.android.tools.idea.gradle.model.IdeUnresolvedDependency
import com.android.tools.idea.gradle.model.LibraryReference

internal sealed class SyncVariantResultCore(
  val moduleConfiguration: ModuleConfiguration,
  val module: AndroidModule
)

internal class SyncVariantResultCoreSuccess(
  moduleConfiguration: ModuleConfiguration,
  module: AndroidModule,
  val ideVariant: IdeVariantWithPostProcessor,
  val nativeVariantAbi: NativeVariantAbiResult,
  val unresolvedDependencies: List<IdeUnresolvedDependency>
) : SyncVariantResultCore(moduleConfiguration, module)

internal class SyncVariantResultCoreFailure(
  moduleConfiguration: ModuleConfiguration,
  module: AndroidModule,
) : SyncVariantResultCore(moduleConfiguration, module)

internal sealed class SyncVariantResult(
  val core: SyncVariantResultCore,
) {
  val moduleConfiguration: ModuleConfiguration get() = core.moduleConfiguration
  val module: AndroidModule get() = core.module
}

internal class SyncVariantResultSuccess(
  val coreSuccess: SyncVariantResultCoreSuccess,
  val moduleDependencies: List<ModuleConfiguration>
) : SyncVariantResult(coreSuccess) {
  val ideVariant: IdeVariantWithPostProcessor get() = coreSuccess.ideVariant
  val nativeVariantAbi: NativeVariantAbiResult get() = coreSuccess.nativeVariantAbi
  val unresolvedDependencies: List<IdeUnresolvedDependency> get() = coreSuccess.unresolvedDependencies
}

internal class SyncVariantResultFailure(
  core: SyncVariantResultCoreFailure,
) : SyncVariantResult(core)

internal fun SyncVariantResultCoreSuccess.getModuleDependencyConfigurations(
  selectedVariants: SelectedVariants,
  androidModulesById: Map<String, AndroidModule>,
  libraryResolver: (LibraryReference) -> IdeLibrary
): List<ModuleConfiguration> {
  val selectedVariantDetails = selectedVariants.selectedVariants[moduleConfiguration.id]?.details

  // Regardless of the current selection in the IDE we try to select the same ABI in all modules the "top" module depends on even
  // when intermediate modules do not have native code.
  val abiToPropagate = nativeVariantAbi.abi ?: moduleConfiguration.abi

  val newlySelectedVariantDetails = createVariantDetailsFrom(module.androidProject.flavorDimensions, ideVariant.variant, nativeVariantAbi.abi)
  val variantDiffChange =
    VariantSelectionChange.extractVariantSelectionChange(to = newlySelectedVariantDetails, from = selectedVariantDetails)


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

  fun generateDirectModuleDependencies(libraryResolver: (LibraryReference) -> IdeLibrary): List<ModuleConfiguration> {
    return (ideVariant.mainArtifact.compileClasspathCore.dependencies
      + ideVariant.unitTestArtifact?.compileClasspathCore?.dependencies.orEmpty()
      + ideVariant.androidTestArtifact?.compileClasspathCore?.dependencies.orEmpty()
      + ideVariant.testFixturesArtifact?.compileClasspathCore?.dependencies.orEmpty()
      )
      .distinct()
      .mapNotNull{ libraryResolver(it.target) as? IdePreResolvedModuleLibrary }
      .mapNotNull { moduleDependency ->
        val dependencyProject = moduleDependency.projectPath
        val dependencyModuleId = Modules.createUniqueModuleId(moduleDependency.buildId, dependencyProject)
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

  return generateDirectModuleDependencies(libraryResolver) + generateDynamicFeatureDependencies()
}
