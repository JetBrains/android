/*
 * Copyright (C) 2020 The Android Open Source Project
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
@file:JvmName("AndroidModelSourceProviderUtils")

package com.android.tools.idea.gradle.project.model

import com.android.builder.model.BaseArtifact
import com.android.builder.model.BuildTypeContainer
import com.android.builder.model.ProductFlavorContainer
import com.android.builder.model.SourceProvider
import com.android.builder.model.SourceProviderContainer
import com.android.ide.common.gradle.model.IdeVariant
import com.android.projectmodel.ARTIFACT_NAME_ANDROID_TEST
import com.android.projectmodel.ARTIFACT_NAME_MAIN
import com.android.projectmodel.ARTIFACT_NAME_UNIT_TEST
import com.android.tools.idea.gradle.project.model.ArtifactSelector.ANDROID_TEST
import com.android.tools.idea.gradle.project.model.ArtifactSelector.MAIN
import com.android.tools.idea.gradle.project.model.ArtifactSelector.UNIT_TEST
import com.intellij.util.containers.addIfNotNull

/**
 * Usage: with(selector) {
 *   variant.selectArtifact()
 *   buildTypeContainer.selectProvider()
 *   productFlavorContainer.selectProvider()
 * }
 */
private enum class ArtifactSelector(val selector: IdeVariant.() -> BaseArtifact?, val artifactName: String) {
  MAIN({ mainArtifact }, ARTIFACT_NAME_MAIN),
  UNIT_TEST({ unitTestArtifact }, ARTIFACT_NAME_UNIT_TEST),
  ANDROID_TEST({ androidTestArtifact }, ARTIFACT_NAME_ANDROID_TEST);

  fun IdeVariant.selectArtifact(): BaseArtifact? = selector()
  fun BuildTypeContainer.selectProvider() = providerBy({ sourceProvider }, { extraSourceProviders })
  fun ProductFlavorContainer.selectProvider() = providerBy({ sourceProvider }, { extraSourceProviders })

  private fun <T> T.providerBy(main: T.() -> SourceProvider, extra: T.() -> Collection<SourceProviderContainer>) =
    when (artifactName) {
      ARTIFACT_NAME_MAIN -> main()
      else -> extra().singleOrNull { it.artifactName == artifactName }?.sourceProvider
    }
}

internal fun AndroidModuleModel.collectMainSourceProviders(variant: IdeVariant) = collectCurrentProvidersFor(variant, MAIN)
internal fun AndroidModuleModel.collectUnitTestSourceProviders(variant: IdeVariant) = collectCurrentProvidersFor(variant, UNIT_TEST)
internal fun AndroidModuleModel.collectAndroidTestSourceProviders(variant: IdeVariant) = collectCurrentProvidersFor(variant, ANDROID_TEST)

internal fun AndroidModuleModel.collectTestSourceProviders(variant: IdeVariant) =
  collectUnitTestSourceProviders(variant) + collectAndroidTestSourceProviders(variant)

internal fun AndroidModuleModel.collectAllSourceProviders(): List<SourceProvider> = collectAllProvidersFor(MAIN)
internal fun AndroidModuleModel.collectAllUnitTestSourceProviders(): List<SourceProvider> = collectAllProvidersFor(UNIT_TEST)
internal fun AndroidModuleModel.collectAllAndroidTestSourceProviders(): List<SourceProvider> = collectAllProvidersFor(ANDROID_TEST)

private fun AndroidModuleModel.collectCurrentProvidersFor(variant: IdeVariant, artifactSelector: ArtifactSelector): List<SourceProvider> =
  mutableListOf<SourceProvider>().apply {
    with(artifactSelector) {
      addIfNotNull(androidProject.defaultConfig.selectProvider())
      val artifact = variant.selectArtifact()
      // TODO(solodkyy): Reverse order as the correct application order is from the last dimenssion to the first.
      addAll(variant.productFlavors.mapNotNull { findProductFlavor(it)?.selectProvider() })
      addIfNotNull(artifact?.multiFlavorSourceProvider)
      addIfNotNull(findBuildType(variant.buildType)?.selectProvider())
      addIfNotNull(artifact?.variantSourceProvider)

    }
  }

private fun AndroidModuleModel.collectAllProvidersFor(artifactSelector: ArtifactSelector): List<SourceProvider> {
  val variants = androidProject.variants.filterIsInstance<IdeVariant>()
  return mutableListOf<SourceProvider>().apply {
    with(artifactSelector) {
      addIfNotNull(androidProject.defaultConfig.selectProvider())
      addAll(androidProject.productFlavors.mapNotNull { it.selectProvider() })
      addAll(variants.mapNotNull { it.selectArtifact()?.multiFlavorSourceProvider })
      addAll(androidProject.buildTypes.mapNotNull { it.selectProvider() })
      addAll(variants.mapNotNull { it.selectArtifact()?.variantSourceProvider })
    }
  }
}

internal fun Iterable<SourceProviderContainer>.getSourceProvidersForArtifacts(vararg artifactNames: String): Collection<SourceProvider> =
  mapNotNull { container -> container.takeIf { it.artifactName in artifactNames }?.sourceProvider }.toSet()

private fun validateTestArtifactNames(testArtifactNames: Array<out String>) =
  testArtifactNames.firstOrNull { it !in AndroidModuleModel.TEST_ARTIFACT_NAMES }?.let {
    throw IllegalArgumentException("'$it' is not a test artifact")
  }
