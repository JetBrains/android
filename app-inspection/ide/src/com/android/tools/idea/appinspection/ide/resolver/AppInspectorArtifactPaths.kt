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
package com.android.tools.idea.appinspection.ide.resolver

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.io.FileService
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.exists
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.kotlin.utils.ThreadSafe

@VisibleForTesting const val INSPECTOR_JARS_DIR = "inspector-jars"

/** The name for the inspector artifact located inside libraries. */
const val INSPECTOR_JAR = "inspector.jar"

/**
 * This class supports file system cache functionality so the framework can query for, as well as
 * populate new inspector archives.
 *
 * It is an internal class not meant to be exposed to users.
 *
 * Note the inspector jars are keyed by their respective library's [ArtifactCoordinate].
 *
 * The directory structure follows the following scheme:
 * $cache_dir/<group_id>/<artifact_id>/<version>/inspector.jar
 */
@ThreadSafe
class AppInspectorArtifactPaths(private val fileService: FileService) {

  /**
   * In memory representation of the cached inspector jars that have been accessed or populated
   * during the life of the application. At the beginning, this is empty. But it's populated over
   * time as jars are accessed and or stored.
   *
   * Concurrent data structure is used here because it could be accessed by multiple threads at the
   * same time.
   */
  private val jars = ConcurrentHashMap<ArtifactCoordinate, Path>()

  /** Gets the cached inspector jar based on the provided coordinate. Null if it's not in cache. */
  fun getInspectorArchive(inspector: ArtifactCoordinate): Path? {
    if (!jars.containsKey(inspector)) {
      val cachePath = fileService.getOrCreateCacheDir(INSPECTOR_JARS_DIR)
      val jarPath =
        Paths.get(
          cachePath.toString(),
          inspector.groupId,
          inspector.artifactId,
          inspector.version,
          inspector.inspectorJarFileName()
        )
      if (jarPath.exists()) {
        jars[inspector] = jarPath
      }
    }
    return jars[inspector]
  }

  /**
   * Given an inspector archive, insert it into the cache.
   *
   * This method has the side effect of copying the inspector archive from wherever it is to this
   * class's internal cache location.
   *
   * The directory structure of the cache contains the coordinate information of the artifact in
   * question:
   * $cache_dir/<group_id>/<artifact_id>/<version>/<group_id>-<artifact_id>-<version>-inspector.jar
   */
  @WorkerThread
  fun populateInspectorArchive(artifactCoordinate: ArtifactCoordinate, archive: Path) {
    try {
      val destDir =
        fileService
          .getOrCreateCacheDir(INSPECTOR_JARS_DIR)
          .resolve(artifactCoordinate.groupId)
          .resolve(artifactCoordinate.artifactId)
          .resolve(artifactCoordinate.version)
      Files.createDirectories(destDir)
      val destFile = destDir.resolve(artifactCoordinate.inspectorJarFileName())
      FileUtils.copyFile(archive, destFile, StandardCopyOption.REPLACE_EXISTING)
      jars[artifactCoordinate] = destFile
    } catch (e: IOException) {
      Logger.getInstance(AppInspectorArtifactPaths::class.java).error(e)
    }
  }

  private fun ArtifactCoordinate.inspectorJarFileName() =
    "${groupId}-${artifactId}-${version}-inspector.jar"
}
