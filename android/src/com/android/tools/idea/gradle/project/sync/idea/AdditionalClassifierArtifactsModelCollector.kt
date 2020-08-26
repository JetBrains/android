/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.builder.model.BaseArtifact
import com.android.builder.model.Library
import com.android.builder.model.Variant
import com.android.ide.gradle.model.AdditionalClassifierArtifactsModelParameter
import com.android.ide.gradle.model.ArtifactIdentifier
import com.android.ide.gradle.model.ArtifactIdentifierImpl
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel
import com.android.tools.idea.gradle.project.sync.idea.svs.AndroidModule
import org.gradle.tooling.BuildController

@UsedInBuildAction
fun getAdditionalClassifierArtifactsModel(
  controller: BuildController,
  inputModules: List<AndroidModule>,
  cachedLibraries: Collection<String>,
  downloadAndroidxUISamplesSources: Boolean
) {
  inputModules.forEach { module ->
    if (module.modelVersion?.isAtLeast(3, 5, 0) != true) return@forEach

    // Get variants from AndroidProject if it's not empty, otherwise get from VariantGroup.
    // The first case indicates full-variants sync and the later single-variant sync.
    val variants = if (module.androidProject.variants.isNotEmpty()) module.androidProject.variants else module.variantGroup.variants
    // Collect the library identifiers to download sources and javadoc for, and filter out the cached ones and local jar/aars.
    val identifiers = collectIdentifiers(variants).filter { !cachedLibraries.contains(idToString(it)) && it.version != "unspecified" }

    // Query for AdditionalClassifierArtifactsModel model.
    if (identifiers.isNotEmpty()) {
      module.additionalClassifierArtifacts = controller.findModel(module.findModelRoot, AdditionalClassifierArtifactsModel::class.java,
                           AdditionalClassifierArtifactsModelParameter::class.java) { parameter ->
        parameter.artifactIdentifiers = identifiers
        parameter.downloadAndroidxUISamplesSources = downloadAndroidxUISamplesSources
      }
    }
  }
}

@UsedInBuildAction
private fun collectIdentifiers(
  variants: Collection<Variant>
): List<ArtifactIdentifier> {
  val libraries = mutableListOf<Library>()
  // Collect libraries from all artifacts of all variants.
  @Suppress("DEPRECATION")
  variants.forEach { variant ->
    val artifacts = mutableListOf<BaseArtifact>(variant.mainArtifact)
    artifacts.addAll(variant.extraAndroidArtifacts)
    artifacts.addAll(variant.extraJavaArtifacts)
    artifacts.forEach {
      libraries.addAll(it.compileDependencies.javaLibraries)
      libraries.addAll(it.compileDependencies.libraries)
    }
  }

  return libraries.filter { it.project == null }.map { it.resolvedCoordinates }.map {
    ArtifactIdentifierImpl(it.groupId, it.artifactId, it.version)
  }.distinct()
}

@UsedInBuildAction
fun idToString(identifier: ArtifactIdentifier): String {
  return identifier.groupId + ":" + identifier.artifactId + ":" + identifier.version
}
