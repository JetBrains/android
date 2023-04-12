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

import com.android.ide.common.repository.AgpVersion
import com.android.ide.gradle.model.AdditionalClassifierArtifactsModelParameter
import com.android.ide.gradle.model.ArtifactIdentifier
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel
import com.android.tools.idea.gradle.model.IdeUnresolvedLibrary
import com.android.tools.idea.gradle.model.LibraryReference
import org.gradle.tooling.BuildController

internal fun getAdditionalClassifierArtifactsModel(
  actionRunner: GradleInjectedSyncActionRunner,
  inputModules: List<AndroidModule>,
  libraryResolver: (LibraryReference) -> IdeUnresolvedLibrary,
  cachedLibraries: Collection<String>,
  downloadAndroidxUISamplesSources: Boolean,
  useMultiVariantAdditionalArtifactSupport: Boolean,
) {
  actionRunner.runActions(
    inputModules.map { module ->
      ActionToRun(fun(controller: BuildController) {
        if (module.agpVersion?.isAtLeast(3, 5, 0) != true) return
        // In later versions of AGP the information contained within the AdditionalClassifierArtifactsModel has been moved
        // to the individual libraries.
        val agp810a08 = AgpVersion.parse("8.1.0-alpha08")
        if (module.agpVersion >= agp810a08 && useMultiVariantAdditionalArtifactSupport) return

        // Collect the library identifiers to download sources and javadoc for, and filter out the cached ones and local jar/aars.
        val identifiers = module.getLibraryDependencies(libraryResolver).filter {
          !cachedLibraries.contains(idToString(it)) && it.version != "unspecified"
        }

        // Query for AdditionalClassifierArtifactsModel model.
        // Since we operate on one module at a time it is safe to run on multiple threads.
        module.additionalClassifierArtifacts =
          controller.findModel(
            module.findModelRoot,
            AdditionalClassifierArtifactsModel::class.java,
            AdditionalClassifierArtifactsModelParameter::class.java
          ) { parameter ->
            parameter.artifactIdentifiers = identifiers
            parameter.downloadAndroidxUISamplesSources = downloadAndroidxUISamplesSources
          }
      })  // No known incompatibilities if Gradle is compatible.
    }
  )
}

fun idToString(identifier: ArtifactIdentifier): String {
  return identifier.groupId + ":" + identifier.artifactId + ":" + identifier.version
}
