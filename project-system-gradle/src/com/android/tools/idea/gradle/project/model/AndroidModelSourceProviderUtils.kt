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

import com.android.projectmodel.ARTIFACT_NAME_ANDROID_TEST
import com.android.projectmodel.ARTIFACT_NAME_MAIN
import com.android.projectmodel.ARTIFACT_NAME_TEST_FIXTURES
import com.android.projectmodel.ARTIFACT_NAME_UNIT_TEST
import com.android.sdklib.AndroidVersion
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.gradle.model.IdeApiVersion
import com.android.tools.idea.gradle.model.IdeBaseArtifactCore
import com.android.tools.idea.gradle.model.IdeSourceProvider
import com.android.tools.idea.gradle.model.IdeExtraSourceProvider
import com.android.tools.idea.gradle.model.IdeSourceProviderContainer
import com.android.tools.idea.gradle.model.IdeVariantCore
import com.android.tools.idea.gradle.project.model.ArtifactSelector.ANDROID_TEST
import com.android.tools.idea.gradle.project.model.ArtifactSelector.MAIN
import com.android.tools.idea.gradle.project.model.ArtifactSelector.TEST_FIXTURES
import com.android.tools.idea.gradle.project.model.ArtifactSelector.UNIT_TEST
import com.intellij.util.containers.addIfNotNull

/**
 * Usage: with(selector) {
 *   variant.selectArtifact()
 *   buildTypeContainer.selectProvider()
 *   productFlavorContainer.selectProvider()
 * }
 */
private enum class ArtifactSelector(val selector: IdeVariantCore.() -> IdeBaseArtifactCore?, val artifactName: String) {
  MAIN({ mainArtifact }, ARTIFACT_NAME_MAIN),
  UNIT_TEST({ unitTestArtifact }, ARTIFACT_NAME_UNIT_TEST),
  ANDROID_TEST({ androidTestArtifact }, ARTIFACT_NAME_ANDROID_TEST),
  TEST_FIXTURES({ testFixturesArtifact }, ARTIFACT_NAME_TEST_FIXTURES);

  fun IdeVariantCore.selectArtifact(): IdeBaseArtifactCore? = selector()
  fun IdeSourceProviderContainer.selectProvider() = providerBy({ sourceProvider }, { extraSourceProviders })

  private fun <T> T.providerBy(main: T.() -> IdeSourceProvider?, extra: T.() -> Collection<IdeExtraSourceProvider>) =
    when (artifactName) {
      ARTIFACT_NAME_MAIN -> main()
      else -> extra().singleOrNull { it.artifactName == artifactName }?.sourceProvider
    }
}

private fun GradleAndroidModelData.collectMainSourceProviders(variant: IdeVariantCore) = collectCurrentProvidersFor(variant, MAIN)
private fun GradleAndroidModelData.collectUnitTestSourceProviders(variant: IdeVariantCore) = collectCurrentProvidersFor(variant, UNIT_TEST)
private fun GradleAndroidModelData.collectAndroidTestSourceProviders(variant: IdeVariantCore) =
  if (variant.androidTestArtifact != null) collectCurrentProvidersFor(variant, ANDROID_TEST)
  else emptyList()
private fun GradleAndroidModelData.collectTestFixturesSourceProviders(variant: IdeVariantCore) =
  if (variant.testFixturesArtifact != null) collectCurrentProvidersFor(variant, TEST_FIXTURES)
  else emptyList()

private fun GradleAndroidModelData.collectAllSourceProviders(): List<IdeSourceProvider> = collectAllProvidersFor(MAIN)
private fun GradleAndroidModelData.collectAllUnitTestSourceProviders(): List<IdeSourceProvider> = collectAllProvidersFor(UNIT_TEST)
private fun GradleAndroidModelData.collectAllAndroidTestSourceProviders(): List<IdeSourceProvider> = collectAllProvidersFor(ANDROID_TEST)
private fun GradleAndroidModelData.collectAllTestFixturesSourceProviders(): List<IdeSourceProvider> = collectAllProvidersFor(TEST_FIXTURES)

private fun GradleAndroidModelData.collectCurrentProvidersFor(variant: IdeVariantCore, artifactSelector: ArtifactSelector): List<IdeSourceProvider> {
  val productFlavors = this.androidProject.multiVariantData?.productFlavors.orEmpty().associateBy { it.productFlavor.name }
  val buildTypes = this.androidProject.multiVariantData?.buildTypes.orEmpty().associateBy { it.buildType.name }
  return mutableListOf<IdeSourceProvider>().apply {
    with(artifactSelector) {
      addIfNotNull(androidProject.defaultSourceProvider.selectProvider())
      val artifact = variant.selectArtifact()
      // TODO(solodkyy): Reverse order as the correct application order is from the last dimension to the first.
      addAll(variant.productFlavors.mapNotNull { productFlavors[it]?.selectProvider() })
      addIfNotNull(artifact?.multiFlavorSourceProvider)
      addIfNotNull(buildTypes[variant.buildType]?.selectProvider())
      addIfNotNull(artifact?.variantSourceProvider)
    }
  }
}

private fun GradleAndroidModelData.collectAllProvidersFor(artifactSelector: ArtifactSelector): List<IdeSourceProvider> {
  val variants = variants
  return mutableListOf<IdeSourceProvider>().apply {
    with(artifactSelector) {
      addIfNotNull(androidProject.defaultSourceProvider.selectProvider())
      addAll(androidProject.multiVariantData?.productFlavors.orEmpty().mapNotNull { it.selectProvider() })
      addAll(variants.mapNotNull { it.selectArtifact()?.multiFlavorSourceProvider })
      addAll(androidProject.multiVariantData?.buildTypes.orEmpty().mapNotNull { it.selectProvider() })
      addAll(variants.mapNotNull { it.selectArtifact()?.variantSourceProvider })
    }
  }
}

/**
  * Convert an [IdeApiVersion] to an [AndroidVersion]. The chief problem here is that the [IdeApiVersion],
  * when using a codename, will not encode the corresponding API level (it just reflects the string
  * entered by the user in the gradle file) so we perform a search here (since lint really wants
  * to know the actual numeric API level)
  *
  * @param api the api version to convert
  * @param targets if known, the installed targets (used to resolve platform codenames, only
  * needed to resolve platforms newer than the tools since [IAndroidTarget] knows the rest)
  * @return the corresponding version
  */
fun convertVersion(
  api: IdeApiVersion,
  targets: Array<IAndroidTarget>?
): AndroidVersion {
  val codename = api.codename
  if (codename != null) {
    val version = SdkVersionInfo.getVersion(codename, targets)
    return version ?: AndroidVersion(api.apiLevel, codename)
  }
  return AndroidVersion(api.apiLevel, null)
}

val GradleAndroidModelData.activeSourceProviders: List<IdeSourceProvider>
  get() = collectMainSourceProviders(selectedVariantCore)
val GradleAndroidModelData.unitTestSourceProviders: List<IdeSourceProvider>
  get() = collectUnitTestSourceProviders(selectedVariantCore)
val GradleAndroidModelData.androidTestSourceProviders: List<IdeSourceProvider>
  get() = collectAndroidTestSourceProviders(selectedVariantCore)
val GradleAndroidModelData.testFixturesSourceProviders: List<IdeSourceProvider>
  get() = collectTestFixturesSourceProviders(selectedVariantCore)

val GradleAndroidModelData.allSourceProviders: List<IdeSourceProvider>
  get() = collectAllSourceProviders()
val GradleAndroidModelData.allUnitTestSourceProviders: List<IdeSourceProvider>
  get() = collectAllUnitTestSourceProviders()
val GradleAndroidModelData.allAndroidTestSourceProviders: List<IdeSourceProvider>
  get() = collectAllAndroidTestSourceProviders()
val GradleAndroidModelData.allTestFixturesSourceProviders: List<IdeSourceProvider>
  get() = collectAllTestFixturesSourceProviders()
