/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.resources.aar.AarResourceRepository
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.res.LocalResourceRepository
import com.android.tools.res.ids.ResourceIdManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

/** @see StudioResourceRepositoryManager.getAppResources */
@VisibleForTesting
class AppResourceRepository
private constructor(
  private val facet: AndroidFacet,
  localResources: List<LocalResourceRepository<VirtualFile>>,
  libraryResources: Collection<AarResourceRepository>
) : MemoryTrackingMultiResourceRepository(facet.module.name + " with modules and libraries") {
  private val resourceMapLock = Any()

  /** Resource directories. Computed lazily. */
  private var resourceDirs: List<VirtualFile>? = null

  val allResourceDirs: Collection<VirtualFile>
    get() {
      synchronized(resourceMapLock) {
        if (resourceDirs == null) {
          resourceDirs = localResources.flatMap { it.resourceDirs }
        }
        return requireNotNull(resourceDirs)
      }
    }

  init {
    setChildren(
      localResources,
      libraryResources,
      listOf(PredefinedSampleDataResourceRepository.getInstance())
    )
  }

  fun updateRoots(
    libraryResources: Collection<AarResourceRepository>,
    sampleDataResourceRepository: LocalResourceRepository<VirtualFile>
  ) {
    val localResources = computeLocalRepositories(facet, sampleDataResourceRepository)
    updateRoots(localResources, libraryResources)
  }

  @VisibleForTesting
  fun updateRoots(
    localResources: List<LocalResourceRepository<VirtualFile>>,
    libraryResources: Collection<AarResourceRepository>
  ) {
    synchronized(resourceMapLock) { resourceDirs = null }
    invalidateResourceDirs()
    setChildren(
      localResources,
      libraryResources,
      listOf(PredefinedSampleDataResourceRepository.getInstance())
    )

    // Clear the fake R class cache and the ModuleClassLoader cache.
    val module = facet.module
    ResourceIdManager.get(module).resetDynamicIds()
    ResourceClassRegistry.get(module.project).clearCache()
    ModuleClassLoaderManager.get().clearCache(module)
  }

  companion object {
    @JvmStatic
    fun create(
      facet: AndroidFacet,
      libraryRepositories: Collection<AarResourceRepository>,
      sampleDataResourceRepository: LocalResourceRepository<VirtualFile>
    ): AppResourceRepository {
      val repository =
        AppResourceRepository(
          facet,
          computeLocalRepositories(facet, sampleDataResourceRepository),
          libraryRepositories
        )
      AndroidProjectRootListener.ensureSubscribed(facet.module.project)

      return repository
    }

    private fun computeLocalRepositories(
      facet: AndroidFacet,
      sampleDataResourceRepository: LocalResourceRepository<VirtualFile>
    ) =
      listOf(
        StudioResourceRepositoryManager.getProjectResources(facet),
        sampleDataResourceRepository
      )

    @TestOnly
    @JvmStatic
    fun createForTest(
      facet: AndroidFacet,
      modules: List<LocalResourceRepository<VirtualFile>>,
      libraries: Collection<AarResourceRepository>
    ) = AppResourceRepository(facet, modules, libraries).also { Disposer.register(facet, it) }
  }
}
