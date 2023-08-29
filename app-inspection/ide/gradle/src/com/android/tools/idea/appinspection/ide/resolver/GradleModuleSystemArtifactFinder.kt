/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.appinspection.ide.resolver

import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.appinspection.inspector.api.launch.RunningArtifactCoordinate
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.openapi.project.modules

class GradleModuleSystemArtifactFinder(private val projectSystem: GradleProjectSystem) {
  /**
   * Finds the location of the library's aar specified by [artifactCoordinate].
   *
   * The resulting location could point to a zip (JAR or AAR) or an unzipped directory.
   */
  fun findLibrary(artifactCoordinate: RunningArtifactCoordinate) =
    projectSystem.project.modules.asList().firstNotNullOfOrNull { module ->
      projectSystem
        .getModuleSystem(module)
        .getDependencyPath(artifactCoordinate.toGradleCoordinate())
    }
}

private fun RunningArtifactCoordinate.toGradleCoordinate(): GradleCoordinate =
  GradleCoordinate(groupId, artifactId, version)
