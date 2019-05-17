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
import com.android.tools.idea.resources.aar.CachingData
import com.android.tools.idea.resources.aar.FrameworkResourceRepository
import com.android.tools.idea.resources.aar.RESOURCE_CACHE_DIRECTORY
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.hash.Hashing
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ServiceManager
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Application service for caching and reusing instances of [FrameworkResourceRepository].
 */
class FrameworkResourceRepositoryManager {
  companion object {
    @JvmStatic fun getInstance() = ServiceManager.getService(FrameworkResourceRepositoryManager::class.java)!!
  }

  private data class Key(val resFolderOrJar: File, val needLocales: Boolean)

  private val cache: LoadingCache<Key, FrameworkResourceRepository> = CacheBuilder.newBuilder()
    .softValues()
    .build(CacheLoader.from { key ->
      val resFolderOrJar = key!!.resFolderOrJar
      val withLocales = key.needLocales
      FrameworkResourceRepository.create(resFolderOrJar.toPath(), key.needLocales, createCachingData(resFolderOrJar.toPath(), withLocales))
    })

  /**
   * Returns a [FrameworkResourceRepository] for the given `resFolder`. The `needLocales` argument is used to indicate if locale-specific
   * information is needed, which makes computing the repository much slower. Even if `needLocales` is false, a repository with locale
   * information may be returned if it has been computed earlier and is available.
   */
  fun getFrameworkResources(resFolderOrJar: File, needLocales: Boolean): FrameworkResourceRepository {
    return if (needLocales) {
      cache.get(Key(resFolderOrJar, true)).also { cache.invalidate(Key(resFolderOrJar, false)) }
    } else {
      cache.getIfPresent(Key(resFolderOrJar, true)) ?: cache.get(Key(resFolderOrJar, false))
    }
  }

  private fun createCachingData(resFolderOrJar: Path, withLocaleResources: Boolean): CachingData? {
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
    val filename = String.format("%s_%s_%s.bin", prefix, if (withLocaleResources) "full" else "light", pathHash)
    val cacheFile = Paths.get(PathManager.getSystemPath(), RESOURCE_CACHE_DIRECTORY, filename)
    return CachingData(cacheFile, contentVersion, codeVersion, AndroidIoManager.getInstance().getBackgroundDiskIoExecutor())
  }
}
