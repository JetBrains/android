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
@file:JvmName("ResourcesTestsUtil")

package com.android.tools.idea.res

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.idea.projectsystem.FilenameConstants
import com.android.tools.idea.res.aar.AarSourceResourceRepository
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.nio.file.Paths

const val AAR_LIBRARY_NAME = "com.test:test-library:1.0.0"
const val AAR_PACKAGE_NAME = "com.test.testlibrary"

fun createTestAppResourceRepository(facet: AndroidFacet): LocalResourceRepository {
  val moduleResources = createTestModuleRepository(facet, emptyList())
  val projectResources = ProjectResourceRepository.createForTest(facet, listOf(moduleResources))
  val appResources = AppResourceRepository.createForTest(facet, listOf<LocalResourceRepository>(projectResources), emptyList())
  val aar = getTestAarRepository()
  appResources.updateRoots(listOf(projectResources, aar), mutableListOf(aar))
  return appResources
}


fun getTestAarRepository(): AarSourceResourceRepository {
  return AarSourceResourceRepository.create(
    Paths.get(AndroidTestBase.getTestDataPath(), "rendering", FilenameConstants.EXPLODED_AAR, "my_aar_lib", "res").toFile(),
    AAR_LIBRARY_NAME
  )
}

@JvmOverloads
fun createTestModuleRepository(
  facet: AndroidFacet,
  resourceDirectories: Collection<VirtualFile>,
  namespace: ResourceNamespace = ResourceNamespace.RES_AUTO,
  dynamicRepo: DynamicResourceValueRepository? = null
): LocalResourceRepository {
  return ModuleResourceRepository.createForTest(facet, resourceDirectories, namespace, dynamicRepo)
}

/**
 * Adds a library dependency to the given module and returns the resources directory which should be filled with content.
 *
 * [ResourceRepositoryManager] will find the newly added library and create a separate repository for it when
 * [ResourceRepositoryManager.getAppResources] is called.
 */
fun addAarDependency(module: Module, libraryName: String): File {
  val aarDir = FileUtil.createTempDirectory(libraryName, "_exploded")

  // Create a manifest file in the right place, so that files inside aarDir are considered resource files.
  // See AndroidResourceUtil#isResourceDirectory which is called from ResourcesDomFileDescription#isResourcesFile.
  File(aarDir, SdkConstants.FN_ANDROID_MANIFEST_XML).createNewFile()

  val resDir = File(aarDir, SdkConstants.FD_RES)
  resDir.mkdir()

  val classesDir = File(aarDir, "classes")
  classesDir.mkdir()

  // See AndroidMavenUtil.isMavenAarDependency for what this library must look like to be considered an AAR.
  ModuleRootModificationUtil.addModuleLibrary(
    module,
    "$libraryName.aar",
    listOf(
      VfsUtil.getUrlForLibraryRoot(resDir),
      VfsUtil.getUrlForLibraryRoot(classesDir)
    ),
    emptyList<String>(),
    DependencyScope.COMPILE
  )

  return resDir
}

/**
 * Adds an AARv2 library dependency to the given module. The library uses the checked-in example res.apk file which uses
 * `com.example.mylibrary` package name and contains a single resource, `@string/my_aar_string`.
 */
fun addBinaryAarDependency(module: Module) {
  // See org.jetbrains.android.facet.ResourceFolderManager#isAarDependency
  PsiTestUtil.addLibrary(
    module,
    "mylibrary.aar",
    "${AndroidTestBase.getTestDataPath()}/dom/layout/myaar-v2",
    "classes.jar",
    "res.apk"
  )
}
