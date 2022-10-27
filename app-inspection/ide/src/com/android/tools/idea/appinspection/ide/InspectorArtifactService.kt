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
package com.android.tools.idea.appinspection.ide

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.appinspection.ide.resolver.AppInspectorArtifactPaths
import com.android.tools.idea.appinspection.ide.resolver.ArtifactResolverFactory
import com.android.tools.idea.appinspection.ide.resolver.INSPECTOR_JAR
import com.android.tools.idea.appinspection.inspector.api.AppInspectionArtifactNotFoundException
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.io.FileService
import com.android.tools.idea.io.IdeFileService
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.io.ZipUtil
import com.intellij.util.io.createDirectories
import java.io.IOException
import java.nio.file.Path
import com.android.tools.idea.appinspection.inspector.ide.resolver.ArtifactResolverFactory as ArtifactResolverFactoryBase

/**
 * An application service that exposes functionality to find, retrieve and
 * manipulate inspector artifacts.
 */
interface InspectorArtifactService {
  /**
   * Gets the cached inspector artifact if it exists, otherwise try to resolve it.
   *
   * Returns null if artifact can't be resolved.
   */
  suspend fun getOrResolveInspectorArtifact(artifactCoordinate: ArtifactCoordinate, project: Project): Path

  companion object {
    val instance
      get() = service<InspectorArtifactService>()
  }
}

/**
 * A helper function that returns an [AppInspectorJar] directly instead of a path.
 */
suspend fun InspectorArtifactService.getOrResolveInspectorJar(project: Project, coordinate: ArtifactCoordinate): AppInspectorJar {
  val inspectorPath = getOrResolveInspectorArtifact(coordinate, project)
  return AppInspectorJar(inspectorPath.fileName.toString(), inspectorPath.parent.toString(), inspectorPath.parent.toString())
}

class InspectorArtifactServiceImpl @NonInjectable @VisibleForTesting constructor(
  private val fileService: FileService,
  private val artifactResolverFactory: ArtifactResolverFactoryBase = ArtifactResolverFactory(fileService),
  private val jarPaths: AppInspectorArtifactPaths = AppInspectorArtifactPaths(fileService)
) : InspectorArtifactService {
  // Called using reflection by intellij service framework
  constructor() : this(IdeFileService("app-inspection"))

  private val unzipDir = fileService.getOrCreateTempDir("unzip")

  @WorkerThread
  override suspend fun getOrResolveInspectorArtifact(artifactCoordinate: ArtifactCoordinate, project: Project): Path {
    // Ignore the cache when searching snapshots, as we can't assume that a library with the same version is actually the same one.
    // A developer could have built a new snapshot since last time we checked.
    return jarPaths.getInspectorArchive(artifactCoordinate).takeUnless { StudioFlags.APP_INSPECTION_USE_SNAPSHOT_JAR.get() } ?: run {
      val artifactZip = artifactResolverFactory.getArtifactResolver(project).resolveArtifact(artifactCoordinate)
      try {
        val inspectorJar = if (artifactZip.fileName.toString() == INSPECTOR_JAR) artifactZip else {
          val targetDir = unzipDir.resolve(artifactCoordinate.getTmpDirName())
          targetDir.createDirectories()
          extraInspectorJarFromLibrary(targetDir, artifactZip)
        }
        jarPaths.populateInspectorArchive(artifactCoordinate, inspectorJar)
        jarPaths.getInspectorArchive(artifactCoordinate)!!
      } catch (e: IOException) {
        throw AppInspectionArtifactNotFoundException("Error encountered while extracting inspector $artifactCoordinate",
                                                     artifactCoordinate, e)
      }
    }
  }

  private fun ArtifactCoordinate.getTmpDirName(): String = "${groupId}-${artifactId}-${version}"

  /**
   * Unzips the library to a temporary scratch directory.
   *
   * Returns the resulting inspector jar's path.
   */
  @WorkerThread
  private fun extraInspectorJarFromLibrary(targetDir: Path, libraryPath: Path): Path {
    ZipUtil.extract(libraryPath, targetDir) { _, name -> name == INSPECTOR_JAR }
    return targetDir.resolve(INSPECTOR_JAR)
  }
}