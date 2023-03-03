/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceType
import com.android.resources.getTestAarRepositoryFromExplodedAar
import com.android.tools.idea.testing.Facets
import com.android.tools.idea.util.toPathString
import com.android.tools.idea.util.toVirtualFile
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.util.function.Predicate
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

fun createTestAppResourceRepository(facet: AndroidFacet): LocalResourceRepository {
  val moduleResources = createTestModuleRepository(facet, emptyList())
  val projectResources = ProjectResourceRepository.createForTest(facet, listOf(moduleResources))
  val appResources = AppResourceRepository.createForTest(facet, listOf<LocalResourceRepository>(projectResources), emptyList())
  val aar = getTestAarRepositoryFromExplodedAar()
  appResources.updateRoots(listOf(projectResources), listOf(aar))
  return appResources
}

@JvmOverloads
fun createTestModuleRepository(
  facet: AndroidFacet,
  resourceDirectories: Collection<VirtualFile>,
  namespace: ResourceNamespace = ResourceNamespace.RES_AUTO,
  dynamicRepo: DynamicValueResourceRepository? = null
): LocalResourceRepository {
  return ModuleResourceRepository.createForTest(facet, resourceDirectories, namespace, dynamicRepo)
}

/**
 * Creates and adds an Android Module to the given project.
 * The module file would be located under [Project.getBasePath] + "/[moduleName]/[moduleName].iml"
 *
 * Runs the given [function][createResources] to add resources to the module.
 *
 * @param moduleName name given to the new module.
 * @param project current working project.
 * @param packageName the module's package name (this will be recorded in its Android manifest)
 * @param createResources code that will be invoked on the module resources folder, to add desired resources. VFS will be refreshed after
 *                        the function is done.
 * @return The instance of the created module added to the project.
 */
fun addAndroidModule(moduleName: String, project: Project, packageName: String, createResources: (moduleResDir: File) -> Unit): Module {
  val root = project.basePath
  val moduleDir = File(FileUtil.toSystemDependentName(root!!), moduleName)
  val moduleFilePath = File(moduleDir, moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION)

  createAndroidManifest(moduleDir, packageName)
  val module = runWriteAction { ModuleManager.getInstance(project).newModule(moduleFilePath.path, ModuleTypeId.JAVA_MODULE) }
  Facets.createAndAddAndroidFacet(module)

  val moduleResDir = moduleDir.resolve(SdkConstants.FD_RES)
  moduleResDir.mkdir()

  createResources(moduleResDir)
  VfsUtil.markDirtyAndRefresh(false, true, true, moduleDir.toVirtualFile(refresh = true))
  return module
}

/**
 * Creates a minimal AndroidManifest.xml with the given [packageName] in the given [dir].
 */
private fun createAndroidManifest(dir: File, packageName: String) {
  dir.mkdirs()
  dir.resolve(SdkConstants.FN_ANDROID_MANIFEST_XML).writeText(
    // language=xml
    """
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$packageName">
      </manifest>
    """.trimIndent()
  )
}

/**
 * Adds a library dependency to the given module and runs the given function to add resources to it.
 *
 * [StudioResourceRepositoryManager] will find the newly added library and create a separate repository for it when
 * [StudioResourceRepositoryManager.getCachedAppResources] is called.
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
  // See AndroidResourcesIdeUtil#isResourceDirectory which is called from ResourcesDomFileDescription#isResourcesFile.
  createAndroidManifest(aarDir, packageName)

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

fun getSingleItem(repository: LocalResourceRepository, type: ResourceType, key: String): ResourceItem {
  val list = repository.getResources(ResourceNamespace.RES_AUTO, type, key)
  assertThat(list).hasSize(1)
  return list[0]
}

fun getSingleItem(repository: LocalResourceRepository, type: ResourceType, key: String, filter: Predicate<ResourceItem>): ResourceItem {
  val list = repository.getResources(ResourceNamespace.RES_AUTO, type, key)
  var found: ResourceItem? = null
  for (item in list) {
    if (filter.test(item)) {
      assertThat(found).isNull()
      found = item
    }
  }
  return found!!
}

class DefinedInOrUnder internal constructor(fileOrDirectory: VirtualFile) : Predicate<ResourceItem> {
  private val myFileOrDirectory = fileOrDirectory.toPathString()

  override fun test(item: ResourceItem): Boolean {
    return item.source!!.startsWith(myFileOrDirectory)
  }
}
