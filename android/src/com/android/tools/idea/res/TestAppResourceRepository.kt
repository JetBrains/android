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
import com.google.common.collect.ImmutableList
import org.jetbrains.android.facet.AndroidFacet

class TestAppResourceRepository private constructor(
  facet: AndroidFacet,
  localResources: List<LocalResourceRepository>,
  libraryResources: Collection<AarResourceRepository>
) : MultiResourceRepository(facet.module.name) {

  init {
    setChildren(localResources, libraryResources, ImmutableList.of())
  }

  companion object {
    @JvmStatic
    @Slow
    fun create(
      facet: AndroidFacet,
      moduleTestResources: LocalResourceRepository
    ): TestAppResourceRepository {
      val localRepositories = mutableListOf(moduleTestResources)
      val androidModuleSystem = facet.getModuleSystem()
      localRepositories.addAll(
        androidModuleSystem
          .getAndroidTestDirectResourceModuleDependencies()
          //
          .filter { it.getHolderModule() != facet.holderModule }
          .mapNotNull { it.androidFacet }
          .map { StudioResourceRepositoryManager.getModuleResources(it) }
      )

      val aarCache = AarResourceRepositoryCache.instance
      val libraryRepositories: Collection<AarResourceRepository> = androidModuleSystem
        .getAndroidLibraryDependencies(DependencyScopeType.ANDROID_TEST)
        .map { aarCache.getSourceRepository(it) }
        .toList()

      if (facet.configuration.isLibraryProject) {
        // In library projects, there's only one APK when testing and the test R class contains all resources.
        localRepositories += StudioResourceRepositoryManager.getAppResources(facet)
      }

      return TestAppResourceRepository(facet, localRepositories, libraryRepositories)
    }
  }
}
