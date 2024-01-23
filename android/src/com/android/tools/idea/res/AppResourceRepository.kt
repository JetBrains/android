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
import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

/** @see StudioResourceRepositoryManager.getAppResources */
@VisibleForTesting
class AppResourceRepository
private constructor(
  private val facet: AndroidFacet,
  parentDisposable: Disposable,
  localResources: List<LocalResourceRepository<VirtualFile>>? = null,
  libraryResources: Collection<AarResourceRepository>? = null,
) :
  MemoryTrackingMultiResourceRepository(
    parentDisposable,
    facet.module.name + " with modules and libraries",
  ) {
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
    val manager = StudioResourceRepositoryManager.getInstance(facet)
    setChildren(
      localResources ?: computeLocalRepositories(manager),
      libraryResources ?: computeLibraryResources(manager),
      listOf(PredefinedSampleDataResourceRepository.getInstance()),
    )
  }

  override fun refreshChildren() {
    val manager = StudioResourceRepositoryManager.getInstance(facet)
    refreshChildren(computeLocalRepositories(manager), computeLibraryResources(manager))
  }

  @VisibleForTesting
  fun refreshChildren(
    localResources: List<LocalResourceRepository<VirtualFile>>,
    libraryResources: Collection<AarResourceRepository>,
  ) {
    synchronized(resourceMapLock) { resourceDirs = null }
    invalidateResourceDirs()
    setChildren(
      localResources,
      libraryResources,
      listOf(PredefinedSampleDataResourceRepository.getInstance()),
    )

    // Clear the fake R class cache and the ModuleClassLoader cache.
    val module = facet.module
    StudioResourceIdManager.get(module).resetDynamicIds()
    ResourceClassRegistry.get(module.project).clearCache()
    ModuleClassLoaderManager.get().clearCache(module)
  }

  companion object {
    @JvmStatic
    fun create(facet: AndroidFacet, parentDisposable: Disposable): AppResourceRepository {
      val repository = AppResourceRepository(facet, parentDisposable)
      AndroidProjectRootListener.ensureSubscribed(facet.module.project)

      return repository
    }

    private fun computeLocalRepositories(manager: StudioResourceRepositoryManager) =
      listOf(manager.projectResources, manager.sampleDataResources)

    private fun computeLibraryResources(
      manager: StudioResourceRepositoryManager
    ): Collection<AarResourceRepository> = manager.libraryResources

    @TestOnly
    @JvmStatic
    fun createForTest(
      facet: AndroidFacet,
      modules: List<LocalResourceRepository<VirtualFile>>,
      libraries: Collection<AarResourceRepository>,
    ) = AppResourceRepository(facet, parentDisposable = facet, modules, libraries)
  }
}
