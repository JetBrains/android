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
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.service.FileService
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.ZipUtil
import com.intellij.util.io.exists
import org.jetbrains.kotlin.utils.ThreadSafe
import java.io.FilenameFilter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

@VisibleForTesting
const val INSPECTOR_JARS_DIR = "inspector-jars"

private const val INSPECTOR_JAR = "inspector.jar"

/**
 * This class exposes functionality that allows user to access and populate inspector jars on a simple file system cache.
 *
 * Note the inspector jars are keyed by their respective library's [ArtifactCoordinate].
 *
 * The directory structure follows the following scheme:
 *   $cache_dir/<group_id>/<artifact_id>/<version>/inspector.jar
 */
@ThreadSafe
class AppInspectorJarPaths(private val fileService: FileService) {
  /**
   * Temporary directory in which to store downloaded artifacts before moving them to the cache directory.
   */
  private val scratchDir = fileService.getOrCreateTempDir(INSPECTOR_JARS_DIR)

  /**
   * In memory representation of the cached inspector jars that have been accessed or populated during the life of the application. At the
   * beginning, this is empty. But it's populated over time as jars are accessed and or stored.
   *
   * Concurrent data structure is used here because it could be accessed by multiple threads at the same time.
   */
  private val jars = ConcurrentHashMap<ArtifactCoordinate, AppInspectorJar>()

  /**
   * Gets the cached inspector jar based on the provided coordinate. Null if it's not in cache.
   */
  fun getInspectorJar(inspector: ArtifactCoordinate): AppInspectorJar? {
    if (!jars.containsKey(inspector)) {
      val cachePath = fileService.getOrCreateCacheDir(INSPECTOR_JARS_DIR)
      val jarPath = Paths.get(cachePath.toString(), inspector.groupId, inspector.artifactId, inspector.version, INSPECTOR_JAR)
      if (jarPath.exists()) {
        jars[inspector] = AppInspectorJar(INSPECTOR_JAR, jarPath.parent.toString(), jarPath.parent.toString())
      }
    }
    return jars[inspector]
  }

  /**
   * Extracts the inspector jar from the provided library and adds it to the file cache.
   */
  @WorkerThread
  fun populateJars(inspectorJars: Map<ArtifactCoordinate, Path>) {
    inspectorJars.forEach { (url, path) ->
      try {
        val jarPath = unzipInspectorJarFromLibrary(url, path)
        jars[url] = AppInspectorJar(jarPath.fileName.toString(), jarPath.parent.toString(),
                                    jarPath.parent.toString())
      }
      catch (e: IOException) {
        Logger.getInstance(AppInspectorJarPaths::class.java).error(e)
      }
    }
  }

  /**
   * Unzips the library to a temporary scratch directory and then copy the inspector jar to the cache directory.
   *
   * The directory structure of the cache contains the coordinate information of the artifact in question:
   *   $cache_dir/<group_id>/<artifact_id>/<version>/inspector.jar
   *
   * Returns the resulting inspector jar's path.
   */
  @WorkerThread
  private fun unzipInspectorJarFromLibrary(url: ArtifactCoordinate, libraryPath: Path): Path {
    ZipUtil.extract(libraryPath.toFile(), scratchDir.toFile(),
                    FilenameFilter { dir, name -> dir.name == "META-INF" && name == INSPECTOR_JAR })

    val srcFile = scratchDir.resolve("META-INF").resolve(INSPECTOR_JAR)
    val destDir = fileService.getOrCreateCacheDir(INSPECTOR_JARS_DIR).resolve(url.groupId).resolve(url.artifactId).resolve(url.version)
    Files.createDirectories(destDir)
    val destFile = destDir.resolve(INSPECTOR_JAR)

    Files.move(srcFile, destFile, StandardCopyOption.REPLACE_EXISTING)
    return destFile
  }
}