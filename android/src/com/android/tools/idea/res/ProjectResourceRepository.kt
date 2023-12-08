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

import com.android.tools.res.LocalResourceRepository
import com.google.common.collect.ImmutableList
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly

/**
 * @see StudioResourceRepositoryManager.getProjectResources
 */
internal class ProjectResourceRepository private constructor(
  private val myFacet: AndroidFacet,
  localResources: List<LocalResourceRepository<VirtualFile>>
) : MemoryTrackingMultiResourceRepository(
  myFacet.module.name + " with modules"
) {
  init {
    setChildren(localResources, ImmutableList.of(), ImmutableList.of())
  }

  fun updateRoots() {
    val repositories = computeRepositories(myFacet)
    invalidateResourceDirs()
    setChildren(repositories, ImmutableList.of(), ImmutableList.of())
  }

  companion object {
    @JvmStatic
    fun create(facet: AndroidFacet): ProjectResourceRepository {
      val resources = computeRepositories(facet)
      return ProjectResourceRepository(facet, resources)
    }

    private fun computeRepositories(facet: AndroidFacet): List<LocalResourceRepository<VirtualFile>> {
      val main = StudioResourceRepositoryManager.getModuleResources(facet)

      // List of module facets the given module depends on.
      val dependencies = AndroidDependenciesCache.getAndroidResourceDependencies(facet.module)
      if (dependencies.isEmpty()) {
        return listOf(main)
      }

      val resources: MutableList<LocalResourceRepository<VirtualFile>> = ArrayList(dependencies.size + 1)
      resources.add(main)
      for (dependency in dependencies) {
        resources.add(StudioResourceRepositoryManager.getModuleResources(dependency))
      }

      return resources
    }

    @TestOnly
    fun createForTest(facet: AndroidFacet, modules: List<LocalResourceRepository<VirtualFile>>): ProjectResourceRepository {
      val repository = ProjectResourceRepository(facet, modules)
      Disposer.register(facet, repository)
      return repository
    }
  }
}
