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

import com.android.builder.model.v2.ide.AbstractArtifact
import com.android.builder.model.v2.ide.AndroidArtifact
import com.android.builder.model.v2.ide.BasicArtifact
import com.android.builder.model.v2.ide.JavaArtifact
import com.android.builder.model.v2.ide.ProjectType
import com.android.builder.model.v2.ide.SourceProvider
import com.android.tools.idea.gradle.model.ARTIFACT_NAME_ANDROID_TEST
import com.android.tools.idea.gradle.model.ARTIFACT_NAME_SCREENSHOT_TEST
import com.android.tools.idea.gradle.model.ARTIFACT_NAME_UNIT_TEST
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeArtifactName.Companion.toPrintableName
import com.android.tools.idea.gradle.model.impl.IdeTestSuiteSourceImpl
import com.android.tools.idea.gradle.project.sync.ModelFeature
import com.android.tools.idea.gradle.project.sync.ModelVersions
import com.android.tools.idea.gradle.project.sync.Modules
import com.android.tools.idea.gradle.project.sync.convertArtifactName
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil.isAaptGeneratedSourcesFolder
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil.isDataBindingGeneratedBaseClassesFolder
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil.isSafeArgGeneratedSourcesFolder
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import java.io.File
import org.jetbrains.plugins.gradle.model.GradleLightProject

/** Returns all source sets (main and for a selected variant) for a given gradle project. */
internal fun SyncContributorAndroidProjectContext.getAllSourceSetsFromModels(): Set<SourceSetData> {
  val (buildType, flavors) = basicAndroidProject.variants
    .singleOrNull { it.name == variantName }
    .let { (it?.buildType) to it?.productFlavors.orEmpty() }

  // TODO(b/384022658): Handle test fixtures and any other potentially relevant sources
  return (
    getSourceSetDataForBasicAndroidProject(variantName, buildType, flavors) +
    getSourceSetDataForAndroidProject(variantName)
  ).deduplicate().onEach { addSourceSetToIndex(it) }
}


@Suppress("DEPRECATION") // Need to be backwards compatible here
internal fun SyncContributorAndroidProjectContext.getSourceSetDataForBasicAndroidProject(
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
      (basicAndroidProject.projectType != ProjectType.TEST).let { mainIsProduction ->
        processBasicArtifact(it.mainArtifact, IdeArtifactName.MAIN, isProduction = mainIsProduction)
        containers.forEach {
          it.sourceProvider?.let {
            sourceSets += createSourceSetDataForSourceProvider(IdeArtifactName.MAIN, it, isProduction = mainIsProduction, versions)
          }
        }
      }

      if (testArtifactsAndSourceSetsInMaps) {
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
      it.testFixturesArtifact?.let {
        processBasicArtifact(it, IdeArtifactName.TEST_FIXTURES, isProduction = false)
        containers.forEach {
          it.testFixturesSourceProvider?.let {
            sourceSets += createSourceSetDataForSourceProvider(IdeArtifactName.TEST_FIXTURES, it, isProduction = false, versions)
          }
        }
      }
    }

  return sourceSets
}

internal fun SyncContributorAndroidProjectContext.getTestSuiteSourceSetDataForBasicAndroidProject(
  sources: List<IdeTestSuiteSourceImpl>
): Map<out ExternalSystemSourceType?, Set<File>> {
  return sources
    .map(::createTestSuiteSourceSetData) // Create a map for each source
    .flatMap { it.entries } // Flatten into a single list of all map entries
    .groupBy({ it.key }, { it.value }) // Group entries by source type
    .mapValues { (_, fileSets) -> fileSets.flatten().toSet() } // Merge the sets of files for each source type
}

@Suppress("DEPRECATION") // Need to be backwards compatible here
internal fun SyncContributorAndroidProjectContext.getSourceSetDataForAndroidProject(selectedVariantName: String): List<SourceSetData>{
  val sourceSets = mutableListOf<SourceSetData>()

  androidProject.variants
    .filter { it.name == selectedVariantName }
    .forEach { variant ->
      sourceSets += createSourceSetDataForAndroidArtifact(IdeArtifactName.MAIN, variant.mainArtifact, isProduction = basicAndroidProject.projectType != ProjectType.TEST)
      if (testArtifactsAndSourceSetsInMaps) {
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
      variant.testFixturesArtifact?.let {
        sourceSets += createSourceSetDataForAndroidArtifact(IdeArtifactName.TEST_FIXTURES, it, isProduction = false)
      }
    }
  return sourceSets
}

internal fun SyncContributorAndroidProjectContext.getSelectedVariantArtifact(sourceSetArtifactName: IdeArtifactName): AbstractArtifact? {
  val selectedVariant = androidProject.variants.singleOrNull { it.name == variantName } ?: error("Can't determine the selected variant")
  return when(sourceSetArtifactName) {
    IdeArtifactName.MAIN -> selectedVariant.mainArtifact
    IdeArtifactName.TEST_FIXTURES -> selectedVariant.testFixturesArtifact
    IdeArtifactName.UNIT_TEST -> if (testArtifactsAndSourceSetsInMaps) selectedVariant.hostTestArtifacts[ARTIFACT_NAME_UNIT_TEST] else selectedVariant.unitTestArtifact
    IdeArtifactName.ANDROID_TEST -> if (testArtifactsAndSourceSetsInMaps) selectedVariant.deviceTestArtifacts[ARTIFACT_NAME_ANDROID_TEST] else selectedVariant.androidTestArtifact
    IdeArtifactName.SCREENSHOT_TEST -> if (testArtifactsAndSourceSetsInMaps) selectedVariant.hostTestArtifacts[ARTIFACT_NAME_SCREENSHOT_TEST] else error("ScreenshotTest are not available")
  }
}

private fun SyncContributorAndroidProjectContext.createTestSuiteSourceSetData(
  testSuiteSource: IdeTestSuiteSourceImpl
): Map<out ExternalSystemSourceType?, Set<File>> {
  val sourceSetData = mutableMapOf<ExternalSystemSourceType?, MutableSet<File>>()
  testSuiteSource.sourceProvider.processAll(forTest = true) { path, sourceType ->
    sourceSetData.getOrPut(sourceType) { mutableSetOf() }.add(File(path))
  }
  return sourceSetData
}

private fun SyncContributorAndroidProjectContext.createSourceSetDataForSourceProvider(name: IdeArtifactName,
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

  return listOf(
    name to mapOf(
      (if (isProduction) ExternalSystemSourceType.SOURCE else ExternalSystemSourceType.TEST)
        to sourceDirectories,
      (if (isProduction) ExternalSystemSourceType.RESOURCE else ExternalSystemSourceType.TEST_RESOURCE)
        to resourceDirectories,
    ) +  provider.manifestFile?.parentFile?.takeUnless { it.path == projectModel.projectDirectory.path }?.let { mapOf(null to setOf(it)) }.orEmpty()
  )
}

private fun SyncContributorAndroidProjectContext.createSourceSetDataForAndroidArtifact(
  name: IdeArtifactName,
  artifact: AndroidArtifact,
  isProduction: Boolean
): List<SourceSetData> {
  return generatedSourceFoldersToUseForArtifact(name, artifact, basicAndroidProject.buildFolder).map {
    name to mapOf(
      (if (isProduction) ExternalSystemSourceType.SOURCE_GENERATED else ExternalSystemSourceType.TEST_GENERATED) to setOf(it)
    )
  } + artifact.generatedResourceFolders.map {
    name to mapOf(
      (if (isProduction) ExternalSystemSourceType.RESOURCE_GENERATED else ExternalSystemSourceType.TEST_RESOURCE_GENERATED) to setOf(it)
    )
  }
}

private fun SyncContributorAndroidProjectContext.createSourceSetDataForTestJavaArtifact(name: IdeArtifactName, artifact: JavaArtifact):
  List<SourceSetData> {
  return generatedSourceFoldersToUseForArtifact(name, artifact, basicAndroidProject.buildFolder).map {
    name to mapOf(
      ExternalSystemSourceType.TEST_GENERATED to setOf(it)
    )
  }
}

private fun SyncContributorAndroidProjectContext.generatedSourceFoldersToUseForArtifact(name: IdeArtifactName, artifact: AbstractArtifact, buildFolder: File): Set<File> =
  artifact.generatedSourceFolders.filter {
    !isAaptGeneratedSourcesFolder(it, buildFolder) &&
    !isDataBindingGeneratedBaseClassesFolder(it, buildFolder) &&
    !isSafeArgGeneratedSourcesFolder(it, buildFolder)
  }.toSet() + findGeneratedSourcesForKapt(name)

private fun SyncContributorAndroidProjectContext.findGeneratedSourcesForKapt(name: IdeArtifactName): Set<File> {
  if (kaptGradleModel == null || !kaptGradleModel.isEnabled) return emptySet()

  val suffix = if (name == IdeArtifactName.MAIN) {
    ""
  } else {
    name.toPrintableName()
  }
  val sourceSet = kaptGradleModel.sourceSets.find { it.sourceSetName == variantName + suffix } ?: return emptySet()
  return setOfNotNull(sourceSet.generatedSourcesDirFile, sourceSet.generatedKotlinSourcesDirFile)
}

/** Sometimes the same source set can be provided via multiple means, so deduplicating is necessary */
private fun List<SourceSetData>.deduplicate(): Set<SourceSetData> {
  val seen = mutableSetOf<File>()
  return this.map {(artifactName, filesMap) ->
    // Remove all but first occurrence of each file and then filter out any elements with empty maps
    artifactName to filesMap.mapValues { (_, files) ->
      files.minus(seen).also {
        seen.addAll(files)
      }
    }.filterValues { it.isNotEmpty() }
  }.filter { it.second.isNotEmpty() }.toSet()
}

internal fun GradleLightProject.moduleId() = Modules.createUniqueModuleId(projectIdentifier.buildIdentifier.rootDir, path)