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

import com.android.projectmodel.ExternalAndroidLibrary
import com.android.projectmodel.ResourceFolder
import com.android.resources.aar.AarProtoResourceRepository
import com.android.resources.aar.AarResourceRepository
import com.android.resources.aar.AarSourceResourceRepository
import com.android.resources.aar.CachingData
import com.android.resources.aar.RESOURCE_CACHE_DIRECTORY
import com.android.tools.concurrency.AndroidIoManager
import com.android.tools.res.CodeVersionAdapter
import com.android.utils.concurrency.getAndUnwrap
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.hash.Hashing
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.kotlin.utils.ThreadSafe
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executor

/** Cache of AAR resource repositories. */
@ThreadSafe
open class AarResourceRepositoryCache protected constructor() {
  private val myProtoRepositories =
    CacheBuilder.newBuilder().softValues().build<Path, AarProtoResourceRepository>()
  private val mySourceRepositories =
    CacheBuilder.newBuilder().softValues().build<ResourceFolder, AarSourceResourceRepository>()

  /**
   * Returns a cached or a newly created source resource repository.
   *
   * @param library the AAR library
   * @return the resource repository
   * @throws IllegalArgumentException if `library` doesn't contain resources or its resource folder
   *   doesn't point to a local file system directory
   */
  fun getSourceRepository(library: ExternalAndroidLibrary): AarSourceResourceRepository {
    val resFolder =
      library.resFolder
        ?: throw IllegalArgumentException("No resources for ${library.libraryName()}")

    if (resFolder.root.toPath() == null) {
      throw IllegalArgumentException(
        "Cannot find resource directory ${resFolder.root} for ${library.libraryName()}"
      )
    }
    return getRepository(resFolder, mySourceRepositories) {
      AarSourceResourceRepository.create(
        resFolder.root,
        resFolder.resources,
        library.libraryName(),
        createCachingData(library),
      )
    }
  }

  /**
   * Returns a cached or a newly created proto resource repository.
   *
   * @param library the AAR library
   * @return the resource repository
   * @throws IllegalArgumentException if `library` doesn't contain res.apk or its res.apk isn't a
   *   file on the local file system
   */
  fun getProtoRepository(library: ExternalAndroidLibrary): AarProtoResourceRepository {
    val resApkPath =
      library.resApkFile
        ?: throw IllegalArgumentException("No res.apk for ${library.libraryName()}")

    val resApkFile =
      resApkPath.toPath()
        ?: throw IllegalArgumentException("Cannot find $resApkPath for ${library.libraryName()}")

    return getRepository(resApkFile, myProtoRepositories) {
      AarProtoResourceRepository.create(resApkFile, library.libraryName())
    }
  }

  fun removeProtoRepository(resApkFile: Path) {
    myProtoRepositories.invalidate(resApkFile)
  }

  fun removeSourceRepository(resourceFolder: ResourceFolder) {
    mySourceRepositories.invalidate(resourceFolder)
  }

  fun clear() {
    myProtoRepositories.invalidateAll()
    mySourceRepositories.invalidateAll()
  }

  private fun createCachingData(library: ExternalAndroidLibrary): CachingData? {
    val resFolder = library.resFolder
    if (resFolder == null || resFolder.resources != null) {
      return null // No caching if the library contains no resources or the list of resource files
      // is specified explicitly.
    }
    // Compute content version as a maximum of the modification times of the res directory and the
    // .aar file itself.
    var modificationTime =
      try {
        Files.getLastModifiedTime(resFolder.root.toPath()!!)
      } catch (e: NoSuchFileException) {
        return null // No caching if the resource directory doesn't exist.
      }
    library.location?.let {
      try {
        val libraryPath = it.toPath()
        if (libraryPath == null) {
          thisLogger().error("Library ${library.libraryName()} has an invalid location: \"$it\"")
        } else {
          modificationTime = modificationTime.coerceAtLeast(Files.getLastModifiedTime(libraryPath))
        }
      } catch (ignore: NoSuchFileException) {}
    }
    val contentVersion = modificationTime.toString()

    val codeVersion = CodeVersionAdapter.getCodeVersion() ?: return null

    val path = resFolder.root
    val pathHash = Hashing.farmHashFingerprint64().hashUnencodedChars(path.portablePath).toString()
    val filename = String.format("%s_%s.dat", library.location?.fileName ?: "", pathHash)
    val cacheFile = Paths.get(PathManager.getSystemPath(), RESOURCE_CACHE_DIRECTORY, filename)
    // Don't create a persistent cache in tests to avoid unnecessary overhead.
    val executor =
      if (ApplicationManager.getApplication().isUnitTestMode) Executor {}
      else AndroidIoManager.getInstance().getBackgroundDiskIoExecutor()
    return CachingData(cacheFile, contentVersion, codeVersion, executor)
  }

  companion object {
    /** Returns the cache. */
    @JvmStatic
    val instance: AarResourceRepositoryCache
      get() = ApplicationManager.getApplication().getService(AarResourceRepositoryCache::class.java)

    private fun <K, T : AarResourceRepository> getRepository(
      key: K,
      cache: Cache<K, T>,
      factory: () -> T,
    ): T {
      return cache.getAndUnwrap(key) { factory() }
    }
  }
}
