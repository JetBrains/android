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
import com.android.ide.common.util.toPathString
import com.android.projectmodel.SelectiveResourceFolder
import com.android.tools.idea.projectsystem.FilenameConstants
import com.android.tools.idea.resources.aar.AarSourceResourceRepository
import com.android.tools.idea.util.toVirtualFile
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.nio.file.Paths
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

const val AAR_LIBRARY_NAME = "com.test:test-library:1.0.0"
const val AAR_PACKAGE_NAME = "com.test.testlibrary"

fun createTestAppResourceRepository(facet: AndroidFacet): LocalResourceRepository {
  val moduleResources = createTestModuleRepository(facet, emptyList())
  val projectResources = ProjectResourceRepository.createForTest(facet, listOf(moduleResources))
  val appResources = AppResourceRepository.createForTest(facet, listOf<LocalResourceRepository>(projectResources), emptyList())
  val aar = getTestAarRepository()
  appResources.updateRoots(listOf(projectResources), listOf(aar))
  return appResources
}

@JvmOverloads
fun getTestAarRepository(libraryDirName: String = "my_aar_lib"): AarSourceResourceRepository {
 return AarSourceResourceRepository.create(
    Paths.get(AndroidTestBase.getTestDataPath(), "rendering", FilenameConstants.EXPLODED_AAR, libraryDirName, "res"),
    AAR_LIBRARY_NAME
  )
}

fun getTestAarRepositoryWithResourceFolders(libraryDirName: String, vararg resources: String): AarSourceResourceRepository {
  val root = Paths.get(AndroidTestBase.getTestDataPath(), "rendering", FilenameConstants.EXPLODED_AAR, libraryDirName, "res").toPathString()
  return AarSourceResourceRepository.create(
    SelectiveResourceFolder(root, resources.map { resource -> root.resolve(resource) }),
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
 * Adds a library dependency to the given module and runs the given function to add resources to it.
 *
 * [ResourceRepositoryManager] will find the newly added library and create a separate repository for it when
 * [ResourceRepositoryManager.getExistingAppResources] is called.
 *
 * @param module module to add the dependency to.
 * @param libraryName name of the newly created [LibraryOrderEntry].
 * @param packageName package name to be put in the library manifest.
 * @param createResources code that will be invoked on the library resources folder, to add desired resources. VFS will be refreshed after
 *                        the function is done.
 */
fun addAarDependency(
  module: Module,
  libraryName: String,
  packageName: String,
  createResources: (File) -> Unit
) {
  val aarDir = FileUtil.createTempDirectory(libraryName, "_exploded")

  // Create a manifest file in the right place, so that files inside aarDir are considered resource files.
  // See AndroidResourceUtil#isResourceDirectory which is called from ResourcesDomFileDescription#isResourcesFile.
  aarDir.resolve(SdkConstants.FN_ANDROID_MANIFEST_XML).writeText(
    // language=xml
    """
      <manifest package="$packageName">
      </manifest>
    """.trimIndent()
  )

  val resDir = aarDir.resolve(SdkConstants.FD_RES)
  resDir.mkdir()

  resDir.resolve(SdkConstants.FD_RES_VALUES).mkdir()

  val classesJar = aarDir.resolve(SdkConstants.FN_CLASSES_JAR)
  JarOutputStream(classesJar.outputStream()).use {
    it.putNextEntry(JarEntry("META-INF/empty"))
  }

  // See ResourceFolderManager.isAarDependency for what this library must look like to be considered an AAR.
  val library = PsiTestUtil.addProjectLibrary(
    module,
    "$libraryName.aar",
    listOf(
      resDir.toVirtualFile(refresh = true),
      classesJar.toVirtualFile(refresh = true)
    ),
    emptyList()
  )
  ModuleRootModificationUtil.addDependency(module, library)

  createResources(resDir)
  VfsUtil.markDirtyAndRefresh(false, true, true, aarDir.toVirtualFile(refresh = true))
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

/**
 * Exposes protected method [LocalResourceRepository.isScanPending] for usage in tests.
 */
fun checkIfScanPending(repository: LocalResourceRepository, psiFile: PsiFile) = repository.isScanPending(psiFile)