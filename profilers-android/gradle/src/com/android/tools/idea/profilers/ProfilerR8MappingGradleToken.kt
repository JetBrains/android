/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.profilers

import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.profilers.ProfilerR8MappingToken.R8Mapping
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.openapi.module.ModuleManager
import com.intellij.util.concurrency.annotations.RequiresReadLock

class ProfilerR8MappingGradleToken : ProfilerR8MappingToken<GradleProjectSystem>, GradleToken {
  @RequiresReadLock
  override fun getR8Mappings(projectSystem: GradleProjectSystem): List<R8Mapping> =
    projectSystem.getAppModels().flatMap { it.getR8Mappings() }.distinct().toList()

  /** Returns all Android application modules in the project. */
  @RequiresReadLock
  private fun GradleProjectSystem.getAppModels(): Sequence<GradleAndroidModel> =
    ModuleManager.getInstance(project)
      .modules
      .asSequence()
      .filter { !it.isDisposed }
      .mapNotNull { GradleAndroidModel.get(it) }
      .filter { it.androidProject.projectType == IdeAndroidProjectType.PROJECT_TYPE_APP }
      .filter { it.features.isBuildOutputFileSupported }

  /** Finds mapping files (mapping.txt) for each build variant of this module, marking the currently selected variant. */
  private fun GradleAndroidModel.getR8Mappings(): List<R8Mapping> {
    val selectedVariant = selectedVariantName
    return androidProject.coreVariants.mapNotNull { variant ->
      val textFile = variant.mainArtifact.mappingR8TextFile ?: return@mapNotNull null
      R8Mapping(
        text = textFile.toPath(),
        applicationId =
          variant.mainArtifact.applicationId?.takeUnless {
            it.isEmpty() || it == AndroidModel.UNINITIALIZED_APPLICATION_ID
          },
        isSelected = (variant.name == selectedVariant),
      )
    }
  }
}
