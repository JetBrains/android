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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc

import com.android.builder.model.AndroidProject
import com.android.builder.model.level2.GlobalLibraryMap
import com.android.tools.idea.gradle.project.sync.ng.SyncActionOptions
import com.android.tools.idea.gradle.project.sync.ng.SyncModuleModels
import com.android.tools.idea.gradle.project.sync.ng.SyncProjectModels
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.loaders.LoaderConstructor
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.loaders.SimpleJsonLoader
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.gradleproject.NewGradleProject
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.GradleProject
import java.io.File
import java.io.IOException

private const val DEBUG_VARIANT_NAME = "debug"
const val SHIPPED_SYNC_DIR = ".sync"

class ShippedSyncProvider(
  private val syncDir: File,
  private val rootProjectDir: File,
  private val sdkDir: File,
  private val offlineRepoDir: File,
  private val bundleDir: File) {

  val syncModuleModelsList = mutableListOf<SyncModuleModels>()

  @Throws(IOException::class)
  fun doFetchShippedModels(loaderConstructor: LoaderConstructor): SyncProjectModels {
    // Fills syncModuleModelsList
    loadSyncRecursively(loaderConstructor)

    val glmLoader = SimpleJsonLoader(syncDir.toPath(), getConverter(rootProjectDir))

    return object: SyncProjectModels(setOf(), setOf(), SyncActionOptions()) {
      override fun getModuleModels(): List<SyncModuleModels> = syncModuleModelsList
      override fun getGlobalLibraryMap(): List<GlobalLibraryMap> = listOf(glmLoader.loadGlobalLibraryMap())
      override fun getRootBuildId() = BuildIdentifier { rootProjectDir }
    }
  }

  private fun getConverter(moduleDir: File) = PathConverter(moduleDir, sdkDir, offlineRepoDir, bundleDir)

  private fun loadSyncRecursively(loaderConstructor: LoaderConstructor, relativePath: File = File("")): SyncModuleModels {
    val moduleDir = rootProjectDir.resolve(relativePath)
    val converter = getConverter(moduleDir)

    val cacheDir = syncDir.resolve(relativePath)

    val loader = loaderConstructor(cacheDir.toPath(), converter)

    val gradleProject = loader.loadGradleProject()

    val buildId = BuildIdentifier { moduleDir }
    val moduleModels = SyncModuleModels(gradleProject, buildId, setOf(), setOf(), SyncActionOptions()).apply {
      addModel(GradleProject::class.java, gradleProject)

      if (cacheDir.resolve(ANDROID_PROJECT_CACHE_PATH).exists()) {
        addModel(AndroidProject::class.java, loader.loadAndroidProject(DEBUG_VARIANT_NAME)) // TODO(qumeric): which variant?
      }
    }

    syncModuleModelsList.add(moduleModels)

    for (childName in gradleProject.childrenNames) {
      val child = loadSyncRecursively(loaderConstructor, relativePath.resolve(childName))
      gradleProject.addChild(child.findModel(GradleProject::class.java)!! as NewGradleProject)
    }

    return moduleModels
  }
}


