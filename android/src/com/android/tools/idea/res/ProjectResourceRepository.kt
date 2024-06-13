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
import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

/** @see StudioResourceRepositoryManager.getProjectResources */
@VisibleForTesting
class ProjectResourceRepository
private constructor(
  private val facet: AndroidFacet,
  parentDisposable: Disposable,
  localResources: List<LocalResourceRepository<VirtualFile>>? = null,
) : MemoryTrackingMultiResourceRepository(parentDisposable, facet.module.name + " with modules") {
  init {
    setChildren(localResources ?: computeRepositories(facet), emptyList(), emptyList())
  }

  override fun refreshChildren() {
    val repositories = computeRepositories(facet)
    invalidateResourceDirs()
    setChildren(repositories, emptyList(), emptyList())
  }

  companion object {
    @JvmStatic
    fun create(facet: AndroidFacet, parentDisposable: Disposable) =
      ProjectResourceRepository(facet, parentDisposable)

    private fun computeRepositories(
      facet: AndroidFacet
    ): List<LocalResourceRepository<VirtualFile>> {
      val main = StudioResourceRepositoryManager.getModuleResources(facet)

      // List of module facets the given module depends on.
      val dependencies = AndroidDependenciesCache.getAndroidResourceDependencies(facet.module)
      if (dependencies.isEmpty()) {
        return listOf(main)
      }

      val resources: MutableList<LocalResourceRepository<VirtualFile>> =
        ArrayList(dependencies.size + 1)
      resources.add(main)
      for (dependency in dependencies) {
        resources.add(StudioResourceRepositoryManager.getModuleResources(dependency))
      }

      return resources
    }

    @TestOnly
    @JvmStatic
    fun createForTest(facet: AndroidFacet, modules: List<LocalResourceRepository<VirtualFile>>) =
      ProjectResourceRepository(facet, parentDisposable = facet, modules)
  }
}
