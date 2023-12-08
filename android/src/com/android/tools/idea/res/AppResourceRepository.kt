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
import com.android.tools.idea.res.ResourceClassRegistry.Companion.get
import com.android.tools.rendering.classloading.ModuleClassLoaderManager.Companion.get
import com.android.tools.res.LocalResourceRepository
import com.android.tools.res.ids.ResourceIdManager.Companion.get
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly

/**
 * @see StudioResourceRepositoryManager.getAppResources
 */
internal class AppResourceRepository private constructor(
  private val myFacet: AndroidFacet,
  localResources: List<LocalResourceRepository<VirtualFile>>,
  libraryResources: Collection<AarResourceRepository>
) : MemoryTrackingMultiResourceRepository(myFacet.module.name + " with modules and libraries") {
  private val RESOURCE_MAP_LOCK = Any()

  /**
   * Resource directories. Computed lazily.
   */
  private var myResourceDirs: Collection<VirtualFile>? = null

  val allResourceDirs: Collection<VirtualFile>
    get() {
      synchronized(RESOURCE_MAP_LOCK) {
        if (myResourceDirs == null) {
          val result = ImmutableList.builder<VirtualFile>()
          for (resourceRepository in localResources) {
            result.addAll(resourceRepository.resourceDirs)
          }
          myResourceDirs = result.build()
        }
        return myResourceDirs!!
      }
    }

  init {
    setChildren(localResources, libraryResources, ImmutableList.of(PredefinedSampleDataResourceRepository.getInstance()))
  }

  fun updateRoots(
    libraryResources: Collection<AarResourceRepository?>,
    sampleDataResourceRepository: LocalResourceRepository<VirtualFile>
  ) {
    val localResources = computeLocalRepositories(myFacet, sampleDataResourceRepository)
    updateRoots(localResources, libraryResources)
  }

  @VisibleForTesting
  fun updateRoots(
    localResources: List<LocalResourceRepository<VirtualFile>>,
    libraryResources: Collection<AarResourceRepository?>
  ) {
    synchronized(RESOURCE_MAP_LOCK) {
      myResourceDirs = null
    }
    invalidateResourceDirs()
    setChildren(localResources, libraryResources, ImmutableList.of(PredefinedSampleDataResourceRepository.getInstance()))

    // Clear the fake R class cache and the ModuleClassLoader cache.
    val module = myFacet.module
    get(module).resetDynamicIds()
    get(module.project).clearCache()
    get().clearCache(module)
  }

  companion object {
    fun create(
      facet: AndroidFacet, libraryRepositories: Collection<AarResourceRepository>,
      sampleDataResourceRepository: LocalResourceRepository<VirtualFile>
    ): AppResourceRepository {
      val repository =
        AppResourceRepository(facet, computeLocalRepositories(facet, sampleDataResourceRepository), libraryRepositories)
      AndroidProjectRootListener.ensureSubscribed(facet.module.project)

      return repository
    }

    private fun computeLocalRepositories(
      facet: AndroidFacet, sampleDataResourceRepository: LocalResourceRepository<VirtualFile>
    ): List<LocalResourceRepository<VirtualFile>> {
      return ImmutableList.of(StudioResourceRepositoryManager.getProjectResources(facet), sampleDataResourceRepository)
    }

    @TestOnly
    fun createForTest(
      facet: AndroidFacet,
      modules: List<LocalResourceRepository<VirtualFile>>,
      libraries: Collection<AarResourceRepository>
    ): AppResourceRepository {
      val repository = AppResourceRepository(facet, modules, libraries)
      Disposer.register(facet, repository)
      return repository
    }
  }
}
