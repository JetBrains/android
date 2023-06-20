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
package com.android.tools.idea.appinspection.ide.resolver.http

import com.android.repository.api.ConsoleProgressIndicator
import com.android.repository.api.Downloader
import com.android.tools.idea.appinspection.ide.resolver.AppInspectorArtifactPaths
import com.android.tools.idea.appinspection.ide.resolver.INSPECTOR_JAR
import com.android.tools.idea.appinspection.ide.resolver.createRandomTempDir
import com.android.tools.idea.appinspection.ide.resolver.extractZipIfNeeded
import com.android.tools.idea.appinspection.ide.resolver.resolveExistsOrNull
import com.android.tools.idea.appinspection.inspector.api.AppInspectionArtifactNotFoundException
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.ide.resolver.ArtifactResolver
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.io.FileService
import com.android.tools.idea.sdk.StudioDownloader
import com.intellij.util.io.createDirectories
import java.io.IOException
import java.net.URL
import java.nio.file.Path
import kotlinx.coroutines.withContext

class HttpArtifactResolver(
  private val fileService: FileService,
  private val artifactPaths: AppInspectorArtifactPaths,
  private val downloader: Downloader = StudioDownloader()
) : ArtifactResolver {
  override suspend fun resolveArtifact(artifactCoordinate: ArtifactCoordinate) =
    artifactPaths.getInspectorArchive(artifactCoordinate)
      ?: run {
        val tmpArtifactDir = fileService.createRandomTempDir()
        val downloadDir = tmpArtifactDir.resolve("download").createDirectories()
        val downloadedLibraryPath = downloadLibrary(downloadDir, artifactCoordinate)
        val unzipDir = tmpArtifactDir.resolve("unzip").createDirectories()
        extractInspector(unzipDir, downloadedLibraryPath, artifactCoordinate).also {
          artifactPaths.populateInspectorArchive(artifactCoordinate, it)
        }
      }

  private suspend fun downloadLibrary(targetDir: Path, artifactCoordinate: ArtifactCoordinate) =
    withContext(AndroidDispatchers.diskIoThread) {
      try {
        val targetPath = targetDir.resolve(artifactCoordinate.fileName)
        downloader.downloadFullyWithCaching(
          artifactCoordinate.toGMavenUrl(),
          targetPath,
          null,
          ConsoleProgressIndicator()
        )
        targetPath
      } catch (e: IOException) {
        throw throw AppInspectionArtifactNotFoundException(
          "Artifact $artifactCoordinate could not be resolved on maven.google.com.",
          artifactCoordinate,
          e
        )
      }
    }

  private suspend fun extractInspector(
    targetDir: Path,
    libraryPath: Path,
    artifactCoordinate: ArtifactCoordinate
  ): Path {
    val artifactDir =
      try {
        extractZipIfNeeded(targetDir, libraryPath)
      } catch (e: IOException) {
        throw throw AppInspectionArtifactNotFoundException(
          "Error happened while unzipping $libraryPath to $targetDir",
          artifactCoordinate,
          e
        )
      }
    return artifactDir.resolveExistsOrNull(INSPECTOR_JAR)
      ?: throw throw AppInspectionArtifactNotFoundException(
        "inspector.jar was not found in $artifactDir",
        artifactCoordinate
      )
  }

  /** The file name of the artifact in question. */
  private val ArtifactCoordinate.fileName
    get() = "${artifactId}-${version}.${type}"

  private fun ArtifactCoordinate.toGMavenUrl() =
    URL("http://maven.google.com/${groupId.replace('.', '/')}/${artifactId}/${version}/${fileName}")
}
