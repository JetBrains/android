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
import com.android.ide.common.util.PathString
import com.android.projectmodel.ExternalLibraryImpl
import com.android.projectmodel.RecursiveResourceFolder
import com.android.resources.aar.AarResourceRepository
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.util.GradleUtil
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
      moduleTestResources: LocalResourceRepository,
      model: AndroidModuleModel
    ): TestAppResourceRepository {
      val project = facet.module.project
      val localRepositories = mutableListOf(moduleTestResources)

      val dependencies = model.selectedAndroidTestCompileDependencies
      if (dependencies != null) {
        val thisModuleGradlePath = GradleUtil.getGradlePath(facet.module)

        localRepositories.addAll(
          dependencies.moduleDependencies.asSequence()
            .mapNotNull {
              it.projectPath.takeIf { path ->
                // This needs to be fixed properly in the model, see http://b/149078408.
                //
                // Fixing http://b/115334911 adds dependency from androidTest to tested (main) variant. This check is to
                // filter that out. Note that this androidTest compile classpath still contains too many things
                // because compile classpath extends tested compile classpath. While this is OK for classes,
                // it is not correct for resources.
                thisModuleGradlePath == null || path != thisModuleGradlePath
              }
            }
            .mapNotNull { GradleUtil.findModuleByGradlePath(project, it) }
            .mapNotNull { it.androidFacet }
            .map { ResourceRepositoryManager.getModuleResources(it) }
        )
      }

      val aarCache = AarResourceRepositoryCache.instance
      val libraryRepositories: Collection<AarResourceRepository> = dependencies?.androidLibraries.orEmpty().asSequence()
        .map {
          aarCache.getSourceRepository(
            ExternalLibraryImpl(address = it.artifactAddress,
                                location = PathString(it.artifact),
                                resFolder = RecursiveResourceFolder(PathString(it.resFolder))))
        }
        .toList()

      if (facet.configuration.isLibraryProject) {
        // In library projects, there's only one APK when testing and the test R class contains all resources.
        localRepositories += ResourceRepositoryManager.getAppResources(facet)
      }

      return TestAppResourceRepository(facet, localRepositories, libraryRepositories)
    }
  }
}
