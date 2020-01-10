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

import com.android.builder.model.SourceProvider
import com.android.builder.model.SourceProviderContainer
import com.android.builder.model.Variant
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.addIfNotNull

private val LOG = Logger.getInstance(AndroidModuleModel::class.java)

internal fun AndroidModuleModel.collectMainSourceProviders(variantName: String): List<SourceProvider> {
  val variant = myVariantsByName[variantName] ?: run {
    LOG.error("Unknown variant name '$variantName' found in the module '${this@collectMainSourceProviders.moduleName}'")
    return listOf()
  }
  return mutableListOf<SourceProvider>().apply {
    add(defaultSourceProvider)
    addAll(variant.productFlavors.mapNotNull { findProductFlavor(it)?.sourceProvider })
    addIfNotNull(variant.mainArtifact.multiFlavorSourceProvider)
    addIfNotNull(findBuildType(variant.buildType)?.sourceProvider)
    addIfNotNull(variant.mainArtifact.variantSourceProvider)
  }
}

internal fun AndroidModuleModel.collectTestSourceProviders(
  variantName: String,
  vararg testArtifactNames: String
): List<SourceProvider> {
  validateTestArtifactNames(testArtifactNames)
  val variant: Variant = myVariantsByName[variantName]!!
  return mutableListOf<SourceProvider>().apply {
    addAll(androidProject.defaultConfig.extraSourceProviders.getSourceProvidersForArtifacts(*testArtifactNames))
    addAll(variant.productFlavors.flatMap {
      findProductFlavor(it)?.extraSourceProviders?.getSourceProvidersForArtifacts(*testArtifactNames).orEmpty()
    })
    addAll(findBuildType(variant.buildType)!!.extraSourceProviders.getSourceProvidersForArtifacts(
      *testArtifactNames))
    // TODO: Does it make sense to add multi-flavor test source providers?
    // TODO: Does it make sense to add variant test source providers?
  }
}

internal fun AndroidModuleModel.collectAllSourceProviders(): List<SourceProvider> {
  val variants = androidProject.variants
  return mutableListOf<SourceProvider>().apply {
    add(defaultSourceProvider)
    addAll(androidProject.productFlavors.map { it.sourceProvider })
    addAll(variants.mapNotNull { it.mainArtifact.multiFlavorSourceProvider })
    addAll(androidProject.buildTypes.map { it.sourceProvider })
    addAll(variants.mapNotNull { it.mainArtifact.variantSourceProvider })
  }
}

internal fun Iterable<SourceProviderContainer>.getSourceProvidersForArtifacts(vararg artifactNames: String): Collection<SourceProvider> =
  mapNotNull { container -> container.takeIf { it.artifactName in artifactNames }?.sourceProvider }.toSet()

private fun validateTestArtifactNames(testArtifactNames: Array<out String>) =
  testArtifactNames.firstOrNull { it !in AndroidModuleModel.TEST_ARTIFACT_NAMES }?.let {
    throw IllegalArgumentException("'$it' is not a test artifact")
  }

