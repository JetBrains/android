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
package com.android.tools.idea.logcat.gradle

import com.android.tools.idea.gradle.model.IdeAndroidArtifactCore
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.logcat.LogcatR8MappingsToken
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.openapi.module.ModuleManager
import java.nio.file.Path

class LogcatR8MappingsGradleToken : LogcatR8MappingsToken<GradleProjectSystem>, GradleToken {
  override fun getR8TextMappings(projectSystem: GradleProjectSystem): List<Path> {
    return projectSystem.getMainArtifacts().mapNotNull { it.mappingR8TextFile?.toPath() }
  }

  override fun getR8PartitionMappings(projectSystem: GradleProjectSystem): List<Path> {
    return projectSystem.getMainArtifacts().mapNotNull { it.mappingR8PartitionFile?.toPath() }
  }

  private fun GradleProjectSystem.getMainArtifacts(): List<IdeAndroidArtifactCore> =
    ModuleManager.getInstance(project)
      .modules
      .asSequence()
      .mapNotNull { GradleAndroidModel.get(it) }
      .filter { it.androidProject.projectType == IdeAndroidProjectType.PROJECT_TYPE_APP }
      .filter { it.features.isBuildOutputFileSupported }
      .flatMap { gradleModel -> gradleModel.androidProject.coreVariants.map { it.mainArtifact } }
      .distinct()
      .toList()
}
