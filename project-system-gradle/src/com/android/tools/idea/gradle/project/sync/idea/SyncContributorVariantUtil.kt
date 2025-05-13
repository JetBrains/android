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
package com.android.tools.idea.gradle.project.sync.idea

import com.android.builder.model.v2.ide.AndroidArtifact
import com.android.builder.model.v2.ide.BasicArtifact
import com.android.builder.model.v2.ide.JavaArtifact
import com.android.builder.model.v2.ide.SourceProvider
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeArtifactName.Companion.toWellKnownSourceSet
import com.android.tools.idea.gradle.project.sync.ModelFeature
import com.android.tools.idea.gradle.project.sync.ModelVersions
import com.android.tools.idea.gradle.project.sync.Modules
import com.android.tools.idea.gradle.project.sync.SingleVariantSyncActionOptions
import com.android.tools.idea.gradle.project.sync.SyncActionOptions
import com.android.tools.idea.gradle.project.sync.convertArtifactName
import com.android.tools.idea.gradle.project.sync.getDefaultVariant
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import org.jetbrains.plugins.gradle.model.GradleLightProject

/** Returns all source sets (main and for a selected variant) for a given gradle project. */
internal fun SyncContributorGradleProjectContext.getAllSourceSetsFromModels(): List<SourceSetData> {
  val variantName = getVariantName(
    syncOptions,
    projectModel,
    basicAndroidProject,
    androidDsl
  ) ?: return emptyList()

  val (buildType, flavors) = basicAndroidProject.variants
    .singleOrNull { it.name == variantName }
    .let { (it?.buildType) to it?.productFlavors.orEmpty() }

  // TODO(b/384022658): Handle test fixtures and any other potentially relevant sources
  return getSourceSetDataForBasicAndroidProject(variantName, buildType, flavors) +
         getSourceSetDataForAndroidProject(variantName)
}


@Suppress("DEPRECATION") // Need to be backwards compatible here
internal fun SyncContributorGradleProjectContext.getSourceSetDataForBasicAndroidProject(
  variantName: String,
  buildTypeForVariant: String?,
  productFlavorsForVariant: List<String>): List<SourceSetData> {

  val sourceSets = mutableListOf<SourceSetData>()
  val containers =
    basicAndroidProject.mainSourceSet?.let { listOf(it) }.orEmpty() +
    basicAndroidProject.buildTypeSourceSets
      .filter { it.sourceProvider?.name == buildTypeForVariant } +
    basicAndroidProject.productFlavorSourceSets
      .filter { it.sourceProvider?.name in productFlavorsForVariant }

  fun processBasicArtifact(artifact: BasicArtifact, name: IdeArtifactName, isProduction: Boolean) {
    artifact.variantSourceProvider?.let { sourceSets += createSourceSetDataForSourceProvider(name, it, isProduction, versions) }
    artifact.multiFlavorSourceProvider?.let { sourceSets += createSourceSetDataForSourceProvider(name, it, isProduction, versions) }
  }

  basicAndroidProject.variants
    .filter { it.name == variantName }
    .forEach {
      processBasicArtifact(it.mainArtifact, IdeArtifactName.MAIN, isProduction = true)
      containers.forEach {
        it.sourceProvider?.let { sourceSets += createSourceSetDataForSourceProvider(IdeArtifactName.MAIN, it, isProduction = true, versions) }
      }

      if (useContainer) {
        (it.deviceTestArtifacts + it.hostTestArtifacts).entries.forEach { (name, artifact) ->
          val artifactName = convertArtifactName(name)
          processBasicArtifact(artifact, artifactName, isProduction = false)
          containers.forEach {
            (it.deviceTestSourceProviders + it.hostTestSourceProviders)[name]?.let {
              sourceSets += createSourceSetDataForSourceProvider(convertArtifactName(name), it, isProduction = false, versions)
            }
          }
        }
      } else {
        it.androidTestArtifact?.let {
          processBasicArtifact(it, IdeArtifactName.UNIT_TEST, isProduction = false)
          containers.forEach {
            it.androidTestSourceProvider?.let {
              sourceSets += createSourceSetDataForSourceProvider(IdeArtifactName.ANDROID_TEST, it, isProduction = false, versions)
            }
          }
        }
        it.unitTestArtifact?.let {
          processBasicArtifact(it, IdeArtifactName.UNIT_TEST, isProduction = false)
          containers.forEach {
            it.unitTestSourceProvider?.let {
              sourceSets += createSourceSetDataForSourceProvider(IdeArtifactName.UNIT_TEST, it, isProduction = false, versions)
            }
          }

        }
      }
    }

  return sourceSets
}

@Suppress("DEPRECATION") // Need to be backwards compatible here
internal fun SyncContributorGradleProjectContext.getSourceSetDataForAndroidProject(selectedVariantName: String): List<SourceSetData>{
  val sourceSets = mutableListOf<SourceSetData>()

  androidProject.variants
    .filter { it.name == selectedVariantName }
    .forEach { variant ->
      sourceSets += createSourceSetDataForAndroidArtifact(IdeArtifactName.MAIN, variant.mainArtifact, isProduction = true)
      if (useContainer) {
        variant.deviceTestArtifacts.entries.forEach { (name, artifact) ->
          sourceSets += createSourceSetDataForAndroidArtifact(convertArtifactName(name), artifact, isProduction = false)
        }
        variant.hostTestArtifacts.entries.forEach { (name, artifact) ->
          sourceSets += createSourceSetDataForTestJavaArtifact(convertArtifactName(name), artifact)
        }
      }
      else {
        variant.androidTestArtifact?.let {
          sourceSets += createSourceSetDataForAndroidArtifact(IdeArtifactName.ANDROID_TEST, it, isProduction = false)
        }
        variant.unitTestArtifact?.let {
          sourceSets += createSourceSetDataForTestJavaArtifact(IdeArtifactName.UNIT_TEST, it)
        }
      }
    }
  return sourceSets
}

internal fun getVariantName(
  syncOptions: SyncActionOptions,
  gradleProject: GradleLightProject,
  basicAndroidProject: BasicAndroidProject,
  androidDsl: AndroidDsl
): String? =
  when (syncOptions) {
    is SingleVariantSyncActionOptions ->
      syncOptions.switchVariantRequest.takeIf { it?.moduleId == gradleProject.moduleId() }?.variantName // newly user-selected variant
      ?: syncOptions.selectedVariants.getSelectedVariant(gradleProject.moduleId()) // variants selected by the last sync
    else -> null
  } ?: basicAndroidProject.variants.toList().getDefaultVariant(androidDsl.buildTypes, androidDsl.productFlavors) // default variant




private fun createSourceSetDataForSourceProvider(name: IdeArtifactName,
                                                 provider: SourceProvider,
                                                 isProduction: Boolean,
                                                 versions: ModelVersions): List<SourceSetData> {
  val sourceDirectories = (
    provider.javaDirectories +
    provider.kotlinDirectories +
    provider.aidlDirectories.orEmpty() +
    provider.renderscriptDirectories.orEmpty() +
    provider.shadersDirectories.orEmpty()).toSet()

  // TODO(b/384022658): Handle custom directories
  val resourceDirectories =
    provider.resourcesDirectories.toSet() +
    provider.resDirectories.orEmpty() +
    provider.mlModelsDirectories.orEmpty() +
    provider.assetsDirectories.orEmpty() + (
      if (versions[ModelFeature.HAS_BASELINE_PROFILE_DIRECTORIES])
        provider.baselineProfileDirectories.orEmpty()
      else
        emptySet()
                                           ) - sourceDirectories // exclude source directories in case they are shared

  val sourceSetName = name.toWellKnownSourceSet().sourceSetName
  return  listOf(
    sourceSetName to mapOf(
      (if (isProduction) ExternalSystemSourceType.SOURCE else ExternalSystemSourceType.TEST)
        to sourceDirectories,
      (if (isProduction) ExternalSystemSourceType.RESOURCE else ExternalSystemSourceType.TEST_RESOURCE)
        to resourceDirectories,
    ) +  provider.manifestFile?.parentFile?.let { mapOf(null to setOf(it)) }.orEmpty()
  )
}

private fun createSourceSetDataForAndroidArtifact(name: IdeArtifactName, artifact: AndroidArtifact, isProduction: Boolean): List<SourceSetData> {
  val sourceSetName = name.toWellKnownSourceSet().sourceSetName
  return artifact.generatedSourceFolders.map {
    sourceSetName to mapOf(
      (if (isProduction) ExternalSystemSourceType.SOURCE else ExternalSystemSourceType.TEST) to setOf(it)
    )
  } + artifact.generatedResourceFolders.map {
    sourceSetName to mapOf(
      (if (isProduction) ExternalSystemSourceType.RESOURCE else ExternalSystemSourceType.TEST_RESOURCE) to setOf(it)
    )
  }
}

private fun createSourceSetDataForTestJavaArtifact(name: IdeArtifactName, artifact: JavaArtifact): List<SourceSetData> {
  val sourceSetName = name.toWellKnownSourceSet().sourceSetName
  return artifact.generatedSourceFolders.map {
    sourceSetName to mapOf(
      ExternalSystemSourceType.TEST to setOf(it)
    )
  }
}

private fun GradleLightProject.moduleId() = Modules.createUniqueModuleId(projectIdentifier.buildIdentifier.rootDir, path)