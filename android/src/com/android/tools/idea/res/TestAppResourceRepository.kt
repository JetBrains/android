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

import com.android.annotations.concurrency.Slow
import com.android.resources.aar.AarResourceRepository
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.getHolderModule
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.util.androidFacet
import com.android.tools.res.LocalResourceRepository
import com.google.common.collect.ImmutableList
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet

class TestAppResourceRepository
private constructor(
  facet: AndroidFacet,
  localResources: List<LocalResourceRepository<VirtualFile>>,
  libraryResources: Collection<AarResourceRepository>
) : MemoryTrackingMultiResourceRepository(facet.module.name) {

  init {
    setChildren(localResources, libraryResources, ImmutableList.of())
  }

  fun updateRoots(facet: AndroidFacet, moduleTestResources: LocalResourceRepository<VirtualFile>) {
    invalidateResourceDirs()
    setChildren(
      computeLocalRepositories(facet, moduleTestResources),
      computeLibraryRepositories(facet),
      ImmutableList.of()
    )
  }

  companion object {
    @JvmStatic
    @Slow
    fun create(facet: AndroidFacet, moduleTestResources: LocalResourceRepository<VirtualFile>) =
      TestAppResourceRepository(
        facet,
        computeLocalRepositories(facet, moduleTestResources),
        computeLibraryRepositories(facet)
      )

    private fun computeLocalRepositories(
      facet: AndroidFacet,
      moduleTestResources: LocalResourceRepository<VirtualFile>
    ): List<LocalResourceRepository<VirtualFile>> {
      val localRepositories = mutableListOf(moduleTestResources)
      val androidModuleSystem = facet.getModuleSystem()
      localRepositories.addAll(
        androidModuleSystem
          .getAndroidTestDirectResourceModuleDependencies()
          .filter { it.getHolderModule() != facet.holderModule }
          .mapNotNull { it.androidFacet }
          .map { StudioResourceRepositoryManager.getModuleResources(it) }
      )

      if (facet.configuration.isLibraryProject) {
        // In library projects, there's only one APK when testing and the test R class contains all
        // resources.
        facet.mainModule.androidFacet?.let {
          localRepositories += StudioResourceRepositoryManager.getAppResources(it)
        }
      }

      return localRepositories
    }

    private fun computeLibraryRepositories(facet: AndroidFacet): List<AarResourceRepository> {
      val androidModuleSystem = facet.getModuleSystem()
      val aarCache = AarResourceRepositoryCache.instance

      return androidModuleSystem
        .getAndroidLibraryDependencies(DependencyScopeType.ANDROID_TEST)
        .map { aarCache.getSourceRepository(it) }
    }
  }
}
