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
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.loaders.NewProjectJsonLoader
import org.apache.commons.io.FilenameUtils
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.GradleProject

import java.io.File
import java.io.IOException
import java.nio.file.Paths

private const val DEBUG_VARIANT_NAME = "debug"

private fun locationToNormalizedFile(location: String): File {
  return File(FilenameUtils.normalize(Paths.get(location).toAbsolutePath().toString()))
}

// TODO(qumeric): get rid of it
private fun getConverter(rootDir: File, moduleName: String, sdkDir: String): PathConverter {
  return PathConverter(File(rootDir, moduleName), File(sdkDir),
                       locationToNormalizedFile("../../adt/idea/android/testData"),
                       locationToNormalizedFile("../../adt/idea/android/testData"))
}

@Throws(IOException::class)
internal fun doFetchShippedModels(extraInfo: NewProjectExtraInfo): SyncProjectModels {
  val buildId = BuildIdentifier{ File(extraInfo.projectLocation) }
  val syncActionOptions = SyncActionOptions()

  val projectCacheDir = getProjectCacheDir(extraInfo.activityTemplateName, extraInfo.minApi, extraInfo.targetApi)

  // TODO remove .. by integrating ShippedSync generation into the build system (see b/111785663)
  val projectCachePath = Paths.get("../../adt/idea/android/testData").resolve(NPW_GENERATED_PROJECTS_DIR)
  val moduleCachePath = projectCachePath.resolve(projectCacheDir)

  val projectName = projectNameFromProjectLocation(extraInfo.projectLocation)

  val projectConverter = getConverter(buildId.rootDir, projectName, extraInfo.sdkDir)
  val moduleConverter = getConverter(buildId.rootDir, extraInfo.mobileProjectName, extraInfo.sdkDir)

  val newProjectLoader = NewProjectJsonLoader(projectCachePath, projectConverter, extraInfo)
  val newModuleLoader = NewProjectJsonLoader(moduleCachePath, moduleConverter, extraInfo)

  val globalLibraryMap = newProjectLoader.loadGlobalLibraryMap()
  val rootGradleProject = newProjectLoader.loadGradleProject()

  val moduleGradleProject = newModuleLoader.loadGradleProject()
  rootGradleProject.addChild(moduleGradleProject)

  val moduleAndroidProject = newModuleLoader.loadAndroidProject(DEBUG_VARIANT_NAME)

  val rootModuleModels = SyncModuleModels(rootGradleProject, buildId, setOf(), setOf(), syncActionOptions)
  rootModuleModels.addModel(GradleProject::class.java, rootGradleProject)
  val moduleModuleModels = SyncModuleModels(moduleGradleProject, buildId, setOf(), setOf(), syncActionOptions)
  moduleModuleModels.addModel(GradleProject::class.java, moduleGradleProject)
  moduleModuleModels.addModel(AndroidProject::class.java, moduleAndroidProject)

  return object : SyncProjectModels(setOf(), setOf(), syncActionOptions) {
    override fun getModuleModels(): List<SyncModuleModels> = listOf(rootModuleModels, moduleModuleModels)
    override fun getGlobalLibraryMap(): List<GlobalLibraryMap> = listOf(globalLibraryMap)
    override fun getRootBuildId(): BuildIdentifier = buildId
  }
}
