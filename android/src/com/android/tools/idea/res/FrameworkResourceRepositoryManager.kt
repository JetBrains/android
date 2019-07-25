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
package com.android.tools.idea.res

import com.android.SdkConstants.DOT_JAR
import com.android.tools.idea.concurrency.AndroidIoManager
import com.android.tools.idea.layoutlib.LayoutLibrary
import com.android.tools.idea.resources.aar.CachingData
import com.android.tools.idea.resources.aar.FrameworkResourceRepository
import com.android.tools.idea.resources.aar.RESOURCE_CACHE_DIRECTORY
import com.google.common.hash.Hashing
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ServiceManager
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * Application service for caching and reusing instances of [FrameworkResourceRepository].
 */
class FrameworkResourceRepositoryManager {
  companion object {
    @JvmStatic fun getInstance() = ServiceManager.getService(FrameworkResourceRepositoryManager::class.java)!!
  }

  private val cache = ConcurrentHashMap<Path, FrameworkResourceRepository>()

  /**
   * Returns a [FrameworkResourceRepository] for the given "res" directory or a jar file. The `languages` parameter
   * determines a subset of framework resources to be loaded. The returned repository is guaranteed to contain
   * resources for the given set of languages plus the language-neutral ones, but may contain resources for more
   * languages than was requested. The repository loads faster if the set of languages is smaller.
   *
   * @param resourceDirectoryOrFile the res directory or a jar file containing resources of the Android framework
   * @param languages the set of ISO 639 language codes
   */
  fun getFrameworkResources(resourceDirectoryOrFile: File, languages: Set<String>): FrameworkResourceRepository {
    val path = resourceDirectoryOrFile.toPath()
    val cachingData = createCachingData(path)
    val cached = cache.computeIfAbsent(path) {
       FrameworkResourceRepository.create(it, languages, cachingData, LayoutLibrary.isNative())
    }
    if (languages.isEmpty()) {
      return cached
    }

    val repository = cached.loadMissingLanguages(languages, cachingData)
    if (repository !== cached) {
      cache[path] = repository
    }
    return repository
  }

  private fun createCachingData(resFolderOrJar: Path): CachingData? {
    if (resFolderOrJar.fileName.toString().endsWith(DOT_JAR, ignoreCase = true)) {
      return null // Caching data is not used when loading framework resources from a JAR.
    }
    val codeVersion = getAndroidPluginVersion() ?: return null
    val contentVersion = try {
      Files.getLastModifiedTime(resFolderOrJar.resolve("../../package.xml")).toString()
    }
    catch (e: NoSuchFileException) {
      ""
    }

    val pathHash = Hashing.farmHashFingerprint64().hashUnencodedChars(resFolderOrJar.toString()).toString()
    val prefix = resFolderOrJar.parent?.parent?.fileName.toString() ?: "framework"
    val filename = String.format("%s_%s.dat", prefix, pathHash)
    val cacheFile = Paths.get(PathManager.getSystemPath(), RESOURCE_CACHE_DIRECTORY, filename)
    return CachingData(cacheFile, contentVersion, codeVersion, AndroidIoManager.getInstance().getBackgroundDiskIoExecutor())
  }

  @TestOnly
  fun clearCache() {
    cache.clear()
  }
}
