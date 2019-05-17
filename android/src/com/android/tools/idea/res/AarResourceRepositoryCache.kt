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

import com.android.projectmodel.ExternalLibrary
import com.android.projectmodel.ResourceFolder
import com.android.tools.idea.resources.aar.AarProtoResourceRepository
import com.android.tools.idea.resources.aar.AarResourceRepository
import com.android.tools.idea.resources.aar.AarSourceResourceRepository
import com.android.utils.concurrency.getAndUnwrap
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Path

/**
 * Cache of AAR resource repositories. This class is thread-safe.
 */
class AarResourceRepositoryCache private constructor() {
  private val myProtoRepositories = CacheBuilder.newBuilder().softValues().build<Path, AarProtoResourceRepository>()
  private val mySourceRepositories = CacheBuilder.newBuilder().softValues().build<ResourceFolder, AarSourceResourceRepository>()

  /**
   * Returns a cached or a newly created source resource repository.
   *
   * @param library the AAR library
   * @return the resource repository
   * @throws IllegalArgumentException if `library` doesn't contain resources or its resource folder doesn't point
   *     to a local file system directory
   */
  fun getSourceRepository(library: ExternalLibrary): AarSourceResourceRepository {
    val libraryName = library.address
    val resFolder = library.resFolder ?: throw IllegalArgumentException("No resource for $libraryName")

    if (resFolder.root.toPath() == null) {
      throw IllegalArgumentException("Cannot find resource directory ${resFolder.root} for $libraryName")
    }
    return getRepository(resFolder, libraryName, mySourceRepositories) {
      AarSourceResourceRepository.create(resFolder, libraryName, null)
    }
  }

  /**
   * Returns a cached or a newly created proto resource repository.
   *
   * @param library the AAR library
   * @return the resource repository
   * @throws IllegalArgumentException if `library` doesn't contain res.apk or its res.apk isn't a file on the local file system
   */
  fun getProtoRepository(library: ExternalLibrary): AarProtoResourceRepository {
    val libraryName = library.address
    val resApkPath = library.resApkFile ?: throw IllegalArgumentException("No res.apk for $libraryName")

    val resApkFile = resApkPath.toPath() ?: throw IllegalArgumentException("Cannot find $resApkPath for $libraryName")

    return getRepository(resApkFile, libraryName, myProtoRepositories, { AarProtoResourceRepository.create(resApkFile, libraryName) })
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

  companion object {
    /**
     * Returns the cache.
     */
    @JvmStatic
    val instance: AarResourceRepositoryCache
        get() = ServiceManager.getService(AarResourceRepositoryCache::class.java)

    private fun <K, T : AarResourceRepository> getRepository(key: K, libraryName: String, cache: Cache<K, T>, factory: () -> T): T {
      val aarRepository = cache.getAndUnwrap(key, { factory() })

      if (libraryName != aarRepository.libraryName) {
        assert(false) { "Library name mismatch: $libraryName vs ${aarRepository.libraryName}" }
        val logger = Logger.getInstance(AarResourceRepositoryCache::class.java)
        logger.error(Exception("Library name mismatch: $libraryName vs ${aarRepository.libraryName}"))
      }

      return aarRepository
    }
  }
}
