/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.appinspection.api.toGradleCoordinate
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.io.FileService
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.util.io.ZipUtil
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly

/**
 * Unzips the library to a temporary scratch directory if it's a zip.
 *
 * Returns the resulting inspector jar's path.
 */
suspend fun extractZipIfNeeded(targetDir: Path, libraryPath: Path) =
  withContext(AndroidDispatchers.diskIoThread) {
    if (libraryPath.isDirectory()) {
      libraryPath
    } else {
      ZipUtil.extract(libraryPath, targetDir) { _, name -> name == INSPECTOR_JAR }
      targetDir
    }
  }

fun Path.resolveExistsOrNull(path: String) = resolve(path).takeIf { it.exists() }

fun FileService.createRandomTempDir() = getOrCreateTempDir(UUID.randomUUID().toString())

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
