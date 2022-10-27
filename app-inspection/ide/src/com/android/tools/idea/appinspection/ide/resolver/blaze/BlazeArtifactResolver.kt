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
package com.android.tools.idea.appinspection.ide.resolver.blaze

import com.android.tools.idea.appinspection.api.blazeFileName
import com.android.tools.idea.appinspection.ide.resolver.INSPECTOR_JAR
import com.android.tools.idea.appinspection.ide.resolver.http.HttpArtifactResolver
import com.android.tools.idea.appinspection.ide.resolver.moduleSystem.ModuleSystemArtifactResolver
import com.android.tools.idea.appinspection.inspector.api.AppInspectionArtifactNotFoundException
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.ide.resolver.ArtifactResolver
import com.android.tools.idea.io.FileService
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import java.nio.file.Path

/**
 * Special handling for blaze projects:
 *
 * Because androidx libraries are released in google3 before they are released on GMaven,
 * there exists a window of time in which http resolver will fail to resolve the library
 * against GMaven.
 *
 * When that happens, the code below will try to resolve the library using
 * BlazeModuleSystem, which will track down the dependency in the blaze BUILD system
 * (if it exists) and match it with a target label. The label is then mapped to a
 * path that is in google3.
 *
 * For example, assuming the blaze project depends on:
 *   //third_party/java/androidx/work/runtime:runtime
 *
 * Attempting to resolve androidx.work:work-runtime:1.0.0 will yield the path:
 *   ${WORKSPACE_ROOT}/third_party/java/androidx/work/runtime
 *
 * The file name is then computed from the maven coordinate, yielding:
 *   ${WORKSPACE_ROOT}/third_party/java/androidx/work/runtime/work-runtime.aar
 */
class BlazeArtifactResolver @VisibleForTesting constructor(
  private val httpArtifactResolver: ArtifactResolver,
  private val moduleSystemArtifactResolver: ArtifactResolver
) : ArtifactResolver {
  constructor(
    fileService: FileService,
    project: Project
  ) : this(HttpArtifactResolver(fileService), ModuleSystemArtifactResolver(project))

  override suspend fun resolveArtifact(artifactCoordinate: ArtifactCoordinate): Path {
    return try {
      val artifactDir = moduleSystemArtifactResolver.resolveArtifact(artifactCoordinate)
      artifactDir.resolve(INSPECTOR_JAR).takeIf { it.exists() } ?: artifactDir.resolve(
        artifactCoordinate.blazeFileName).takeIf { it.exists() } ?: throw AppInspectionArtifactNotFoundException(
        "Artifact not found in blaze module system.", artifactCoordinate)
    }
    catch (e: AppInspectionArtifactNotFoundException) {
      try {
        httpArtifactResolver.resolveArtifact(artifactCoordinate)
      }
      catch (e: AppInspectionArtifactNotFoundException) {
        throw AppInspectionArtifactNotFoundException("Artifact $artifactCoordinate not found in blaze module system and on maven.",
                                                     artifactCoordinate)
      }
    }
  }
}