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

import com.android.ide.gradle.model.AdditionalClassifierArtifactsModelParameter
import com.android.ide.gradle.model.ArtifactIdentifier
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

    // Collect the library identifiers to download sources and javadoc for, and filter out the cached ones and local jar/aars.
    val identifiers = module.getLibraryDependencies().filter { !cachedLibraries.contains(idToString(it)) && it.version != "unspecified" }

    // Query for AdditionalClassifierArtifactsModel model.
    if (identifiers.isNotEmpty()) {
      module.additionalClassifierArtifacts =
        controller.findModel(
          module.findModelRoot,
          AdditionalClassifierArtifactsModel::class.java,
          AdditionalClassifierArtifactsModelParameter::class.java
        ) { parameter ->
          parameter.artifactIdentifiers = identifiers
          parameter.downloadAndroidxUISamplesSources = downloadAndroidxUISamplesSources
      }
    }
  }
}

@UsedInBuildAction
fun idToString(identifier: ArtifactIdentifier): String {
  return identifier.groupId + ":" + identifier.artifactId + ":" + identifier.version
}
