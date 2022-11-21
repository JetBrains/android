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
import com.android.tools.idea.appinspection.ide.resolver.ModuleSystemArtifactFinder
import com.android.tools.idea.appinspection.ide.resolver.createRandomTempDir
import com.android.tools.idea.appinspection.ide.resolver.extractZipIfNeeded
import com.android.tools.idea.appinspection.ide.resolver.resolveExistsOrNull
import com.android.tools.idea.appinspection.inspector.api.AppInspectionArtifactNotFoundException
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.ide.resolver.ArtifactResolver
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.io.FileService
import kotlinx.coroutines.withContext
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
class BlazeArtifactResolver constructor(
  private val fileService: FileService,
  private val moduleSystemArtifactFinder: ModuleSystemArtifactFinder
) : ArtifactResolver {
  override suspend fun resolveArtifact(artifactCoordinate: ArtifactCoordinate): Path = withContext(AndroidDispatchers.diskIoThread) {
    moduleSystemArtifactFinder.findLibrary(artifactCoordinate)?.let { libraryPath ->
      val unzippedDir = extractZipIfNeeded(fileService.createRandomTempDir(), libraryPath)
      unzippedDir.resolveExistsOrNull(INSPECTOR_JAR) ?: unzippedDir.resolveExistsOrNull(artifactCoordinate.blazeFileName)
    } ?: throw AppInspectionArtifactNotFoundException("Artifact not found in blaze module system.", artifactCoordinate)
  }
}