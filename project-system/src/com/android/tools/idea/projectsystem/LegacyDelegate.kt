/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.android.SdkConstants
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

/** [IdeaSourceProvider] for legacy Android projects without [SourceProvider].  */
@Suppress("DEPRECATION")
class LegacyDelegate constructor(private val facet: AndroidFacet) : NamedIdeaSourceProvider {

  override val name: String = ""

  override val scopeType: ScopeType = ScopeType.MAIN

  private val manifestFileUrl: String get() = manifestFile?.url ?: let {
    val moduleDirPath: String = File(facet.module.moduleFilePath).parent
    VfsUtilCore.pathToUrl(
      FileUtil.toSystemIndependentName(
        moduleDirPath + facet.properties.MANIFEST_FILE_RELATIVE_PATH))
  }

  private val manifestDirectory: VirtualFile?
    get() = VirtualFileManager.getInstance().findFileByUrl(manifestDirectoryUrl)

  private val manifestDirectoryUrl: String
    get() = VfsUtil.getParentDir(manifestFileUrl) ?: error("Invalid manifestFileUrl: $manifestFileUrl")

  private val manifestFile: VirtualFile?
    // Not calling AndroidRootUtil.getMainContentRoot(myFacet) because that method can
    // recurse into this same method if it can't find a content root. (This scenario
    // applies when we're looking for manifests in for example a temporary file system,
    // as tested by ResourceTypeInspectionTest#testLibraryRevocablePermission)
    get() {
      val module = facet.module
      val file = AndroidProjectRootUtil.getFileByRelativeModulePath(
        module,
        facet.properties.MANIFEST_FILE_RELATIVE_PATH,
        false
      )
      if (file != null) {
        return file
      }
      val contentRoots = ModuleRootManager.getInstance(module).contentRoots
      if (contentRoots.size == 1) {
        return contentRoots[0].findChild(SdkConstants.ANDROID_MANIFEST_XML)
      }
      return null
    }

  override val manifestFileUrls: Collection<String>
    get() = listOf(manifestFileUrl)

  override val manifestFiles: Collection<VirtualFile>
    get() = listOfNotNull(manifestFile)

  override val manifestDirectoryUrls: Collection<String>
    get() = listOf(manifestDirectoryUrl)

  override val manifestDirectories: Collection<VirtualFile>
    get() = listOfNotNull(manifestDirectory)

  override val javaDirectoryUrls: Collection<String> get() = ModuleRootManager.getInstance(
    facet.module).contentRootUrls.toSet()
  override val javaDirectories: Collection<VirtualFile> get() = ModuleRootManager.getInstance(
    facet.module).contentRoots.toSet()

  override val kotlinDirectoryUrls: Collection<String> = emptySet()
  override val kotlinDirectories: Iterable<VirtualFile> = emptySet()

  override val resourcesDirectoryUrls: Collection<String> get() = emptySet()
  override val resourcesDirectories: Collection<VirtualFile> get() = emptySet()

  override val aidlDirectoryUrls: Collection<String> get() = listOfNotNull(
    AndroidProjectRootUtil.getAidlGenSourceRootPath(facet)?.convertToUrl())
  override val aidlDirectories: Collection<VirtualFile> get() = listOfNotNull(
    AndroidProjectRootUtil.getAidlGenDir(facet))

  override val renderscriptDirectoryUrls: Collection<String> get() = listOfNotNull(
    AndroidProjectRootUtil.getRenderscriptGenSourceRootPath(facet)?.convertToUrl())
  override val renderscriptDirectories: Collection<VirtualFile> get() = listOfNotNull(
    AndroidProjectRootUtil.getRenderscriptGenDir(facet))

  override val jniLibsDirectoryUrls: Collection<String> get() = emptySet()
  override val jniLibsDirectories: Collection<VirtualFile> get() = emptySet()

  override val resDirectoryUrls: Collection<String> get() = resDirectories.map { it.url }
  override val resDirectories: Collection<VirtualFile>
    get() {
      val resRelPath = facet.properties.RES_FOLDER_RELATIVE_PATH
      return listOfNotNull(
        AndroidProjectRootUtil.getFileByRelativeModulePath(facet.module, resRelPath,
                                                           false))
    }

  override val assetsDirectoryUrls: Collection<String> get() = assetsDirectories.map { it.url }
  override val assetsDirectories: Collection<VirtualFile>
    get() {
      val dir = AndroidProjectRootUtil.getAssetsDir(facet)
      return listOfNotNull(dir)
    }

  override val shadersDirectoryUrls: Collection<String> get() = emptySet()
  override val shadersDirectories: Collection<VirtualFile> get() = emptySet()

  override val mlModelsDirectoryUrls: Collection<String> get() = emptySet()
  override val mlModelsDirectories: Collection<VirtualFile> get() = emptySet()

  override val custom: Map<String, IdeaSourceProvider.Custom> get() = emptyMap()

  override val baselineProfileDirectoryUrls: Iterable<String> get() = emptySet()
  override val baselineProfileDirectories: Iterable<VirtualFile> get() = emptySet()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false

    val that = other as LegacyDelegate?
    return facet == that!!.facet
  }

  override fun hashCode(): Int = facet.hashCode()
}

private fun String.convertToUrl() = VfsUtil.pathToUrl(this)

fun createSourceProvidersForLegacyModule(facet: AndroidFacet): SourceProviders {
  val mainSourceProvider = LegacyDelegate(facet)
  return SourceProvidersImpl(
    mainIdeaSourceProvider = mainSourceProvider,
    currentSourceProviders = listOf(mainSourceProvider),
    currentUnitTestSourceProviders = emptyList(),
    currentAndroidTestSourceProviders = emptyList(),
    currentTestFixturesSourceProviders = emptyList(),
    currentAndSomeFrequentlyUsedInactiveSourceProviders = listOf(mainSourceProvider),
    mainAndFlavorSourceProviders = listOf(mainSourceProvider),
    generatedSources = createMergedSourceProvider(ScopeType.MAIN, emptyList()),
    generatedUnitTestSources = createMergedSourceProvider(ScopeType.UNIT_TEST, emptyList()),
    generatedAndroidTestSources = createMergedSourceProvider(ScopeType.ANDROID_TEST, emptyList()),
    generatedTestFixturesSources = createMergedSourceProvider(ScopeType.TEST_FIXTURES, emptyList())
  )
}
