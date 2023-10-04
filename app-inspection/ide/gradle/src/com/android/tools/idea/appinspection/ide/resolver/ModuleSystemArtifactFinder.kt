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
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import java.nio.file.Path
import org.jetbrains.annotations.TestOnly

class ModuleSystemArtifactFinder(
  project: Project,
  @TestOnly
  private val findArtifact: (ArtifactCoordinate) -> Path? = { artifactCoordinate ->
    project.findLibrary(artifactCoordinate)
  }
) {
  /**
   * Finds the location of the library's aar specified by [artifactCoordinate].
   *
   * The resulting location could point to a zip (JAR or AAR) or an unzipped directory.
   */
  fun findLibrary(artifactCoordinate: ArtifactCoordinate) = findArtifact(artifactCoordinate)

  companion object {
    private fun Project.findLibrary(artifactCoordinate: ArtifactCoordinate) =
      modules.asList().firstNotNullOfOrNull { module ->
        getProjectSystem()
          .getModuleSystem(module)
          .getDependencyPath(artifactCoordinate.toGradleCoordinate())
      }
  }
}

private fun ArtifactCoordinate.toGradleCoordinate(): GradleCoordinate =
  GradleCoordinate(groupId, artifactId, version)
