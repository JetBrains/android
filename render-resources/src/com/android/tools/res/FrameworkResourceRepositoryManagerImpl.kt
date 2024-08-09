/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.res

import com.android.SdkConstants.DOT_JAR
import com.android.annotations.TestOnly
import com.android.annotations.concurrency.Slow
import com.android.ide.common.resources.ResourceRepository
import com.android.resources.aar.CachingData
import com.android.resources.aar.FrameworkResourceRepository
import com.android.resources.aar.RESOURCE_CACHE_DIRECTORY
import com.google.common.hash.Hashing
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * Class for caching and reusing instances of [FrameworkResourceRepository].
 */
open class FrameworkResourceRepositoryManagerImpl(
  private val systemCachePath: String = "", // Empty path means no caching
  private val diskIoExecutor: Executor,
) : FrameworkResourceRepositoryManager {
  private data class CacheKey(val path: Path, val overlayName: String, val useCompiled9Patches: Boolean)

  private val cache = ConcurrentHashMap<CacheKey, FrameworkResourceRepository>()

  /**
   * Returns a [FrameworkResourceRepository] for the given "res" directory or a jar file. The [languages] parameter
   * determines a subset of framework resources to be loaded. The returned repository is guaranteed to contain
   * resources for the given set of languages plus the language-neutral ones, but may contain resources for more
   * languages than was requested. The repository loads faster if the set of languages is smaller.
   *
   * @param resourceJarFile the jar file containing resources of the Android framework
   * @param useCompiled9Patches whether the created directory should use compiled 9-patch files
   * @param languages a set of ISO 639 language codes that the repository should contain. The returned repository
   *     may contain data for more languages.
   * @param overlays a list of overlays to add to the base framework resources
   * @return the repository of Android framework resources
   */
  @Slow
  override fun getFrameworkResources(
    resourceJarFile: Path,
    useCompiled9Patches: Boolean,
    languages: Set<String>,
    overlays: List<FrameworkOverlay>,
  ): ResourceRepository {
    val path = resourceJarFile
    val cachingData = createCachingData(path)
    val baseRepository = getFrameworkRepository(path, "", useCompiled9Patches, languages, cachingData)
    if (overlays.isEmpty()) {
      return baseRepository
    }

    val overlayRepositories = overlays.map { getFrameworkRepository(path, it.overlayName, useCompiled9Patches, languages, cachingData) }
    return FrameworkWithOverlaysResourceRepository(baseRepository, overlayRepositories)
  }

  private fun getFrameworkRepository(
    path: Path,
    overlay: String,
    useCompiled9Patches: Boolean,
    languages: Set<String>,
    cachingData: CachingData?
  ): FrameworkResourceRepository {
    val cacheKey = CacheKey(path, overlay, useCompiled9Patches)
    val cached = cache.computeIfAbsent(cacheKey) {
      if (overlay.isEmpty()) {
        FrameworkResourceRepository.create(path, languages, cachingData, useCompiled9Patches)
      } else {
        FrameworkResourceRepository.createForOverlay(path, overlay, languages, cachingData, useCompiled9Patches)
      }
    }
    if (languages.isEmpty()) {
      return cached
    }

    val repository = cached.loadMissingLanguages(languages, cachingData)
    if (repository !== cached) {
      cache[cacheKey] = repository
    }
    return repository
  }

  private fun createCachingData(resFolderOrJar: Path): CachingData? {
    if (resFolderOrJar.fileName.toString().endsWith(DOT_JAR, ignoreCase = true)) {
      return null // Caching data is not used when loading framework resources from a JAR.
    }
    if (systemCachePath.isEmpty()) {
      return null
    }
    val codeVersion = CodeVersionAdapter.getCodeVersion() ?: return null
    val contentVersion = try {
      Files.getLastModifiedTime(resFolderOrJar.resolve("../../package.xml")).toString()
    }
    catch (e: NoSuchFileException) {
      ""
    }

    val pathHash = Hashing.farmHashFingerprint64().hashUnencodedChars(resFolderOrJar.toString()).toString()
    val prefix = resFolderOrJar.parent?.parent?.fileName?.toString() ?: "framework"
    val filename = String.format("%s_%s.dat", prefix, pathHash)
    val cacheFile = Paths.get(systemCachePath, RESOURCE_CACHE_DIRECTORY, filename)
    return CachingData(cacheFile, contentVersion, codeVersion, diskIoExecutor)
  }

  @TestOnly
  fun clearCache() {
    cache.clear()
  }
}
