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
@file:JvmName("SourceProviderUtil")
package org.jetbrains.android.facet

import com.android.SdkConstants
import com.android.builder.model.SourceProvider
import com.android.tools.idea.projectsystem.IdeaSourceProvider
import com.android.tools.idea.projectsystem.IdeaSourceProviderImpl
import com.android.tools.idea.projectsystem.SourceProviders
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.io.File

fun createIdeaSourceProviderFromModelSourceProvider(it: SourceProvider): IdeaSourceProviderImpl {
  return IdeaSourceProviderImpl(
    it.name,
    VfsUtil.fileToUrl(it.manifestFile),
    javaDirectoryUrls = convertToUrlSet(it.javaDirectories),
    resourcesDirectoryUrls = convertToUrlSet(it.resourcesDirectories),
    aidlDirectoryUrls = convertToUrlSet(it.aidlDirectories),
    renderscriptDirectoryUrls = convertToUrlSet(it.renderscriptDirectories),
    // Even though the model has separate methods to get the C and Cpp directories,
    // they both return the same set of folders. So we combine them here.
    jniDirectoryUrls = convertToUrlSet(it.cDirectories + it.cppDirectories).toSet(),
    jniLibsDirectoryUrls = convertToUrlSet(it.jniLibsDirectories),
    resDirectoryUrls = convertToUrlSet(it.resDirectories),
    assetsDirectoryUrls = convertToUrlSet(it.assetsDirectories),
    shadersDirectoryUrls = convertToUrlSet(it.shadersDirectories)
  )
}

fun createSourceProvidersForLegacyModule(facet: AndroidFacet): SourceProviders {
  val mainSourceProvider = LegacyDelegate(facet)
  return SourceProvidersImpl(
    mainIdeaSourceProvider = mainSourceProvider,
    currentSourceProviders = listOf(mainSourceProvider),
    currentTestSourceProviders = emptyList(),
    allSourceProviders = listOf(mainSourceProvider),
    mainAndFlavorSourceProviders = listOf(mainSourceProvider)
  )
}

/** [IdeaSourceProvider] for legacy Android projects without [SourceProvider].  */
@Suppress("DEPRECATION")
private class LegacyDelegate constructor(private val facet: AndroidFacet) : IdeaSourceProvider {

  override val name: String = ""

  override val manifestFileUrl: String get() = manifestFile?.url ?: let {
    val contentRoots = ModuleRootManager.getInstance(facet.module).contentRoots
    return if (contentRoots.isNotEmpty()) {
      contentRoots[0].url + "/" + SdkConstants.ANDROID_MANIFEST_XML
    } else {
      throw IllegalStateException("Content root is required to determine manifestFileUrl")
    }
  }

  override val manifestDirectory: VirtualFile?
    get() = VirtualFileManager.getInstance().findFileByUrl(manifestDirectoryUrl)

  override val manifestDirectoryUrl: String
    get() = VfsUtil.getParentDir(manifestFileUrl) ?: error("Invalid manifestFileUrl: $manifestFileUrl")

  override val manifestFile: VirtualFile?
    // Not calling AndroidRootUtil.getMainContentRoot(myFacet) because that method can
    // recurse into this same method if it can't find a content root. (This scenario
    // applies when we're looking for manifests in for example a temporary file system,
    // as tested by ResourceTypeInspectionTest#testLibraryRevocablePermission)
    get() {
      val module = facet.module
      val file = AndroidRootUtil.getFileByRelativeModulePath(module,
                                                             facet.properties.MANIFEST_FILE_RELATIVE_PATH, true)
      if (file != null) {
        return file
      }
      val contentRoots = ModuleRootManager.getInstance(module).contentRoots
      if (contentRoots.size == 1) {
        return contentRoots[0].findChild(SdkConstants.ANDROID_MANIFEST_XML)
      }
      return null
    }

  override val javaDirectoryUrls: Collection<String> get() = ModuleRootManager.getInstance(facet.module).contentRootUrls.toSet()
  override val javaDirectories: Collection<VirtualFile> get() = ModuleRootManager.getInstance(
    facet.module).contentRoots.toSet()

  override val resourcesDirectoryUrls: Collection<String> get() = emptySet()
  override val resourcesDirectories: Collection<VirtualFile> get() = emptySet()

  override val aidlDirectoryUrls: Collection<String> get() = listOfNotNull(
    AndroidRootUtil.getAidlGenSourceRootPath(facet)?.convertToUrl())
  override val aidlDirectories: Collection<VirtualFile> get() = listOfNotNull(
    AndroidRootUtil.getAidlGenDir(facet))

  override val renderscriptDirectoryUrls: Collection<String> get() = listOfNotNull(
    AndroidRootUtil.getRenderscriptGenSourceRootPath(facet)?.convertToUrl())
  override val renderscriptDirectories: Collection<VirtualFile> get() = listOfNotNull(
    AndroidRootUtil.getRenderscriptGenDir(facet))

  override val jniDirectoryUrls: Collection<String> get() = emptySet()
  override val jniDirectories: Collection<VirtualFile> get() = emptySet()

  override val jniLibsDirectoryUrls: Collection<String> get() = emptySet()
  override val jniLibsDirectories: Collection<VirtualFile> get() = emptySet()

  override val resDirectoryUrls: Collection<String> get() = resDirectories.map { it.url }
  override val resDirectories: Collection<VirtualFile>
    get() {
      val resRelPath = facet.properties.RES_FOLDER_RELATIVE_PATH
      return listOfNotNull(AndroidRootUtil.getFileByRelativeModulePath(facet.module, resRelPath, true))
    }

  override val assetsDirectoryUrls: Collection<String> get() = assetsDirectories.map { it.url }
  override val assetsDirectories: Collection<VirtualFile>
    get() {
      val dir = AndroidRootUtil.getAssetsDir(facet)
      return listOfNotNull(dir)
    }

  override val shadersDirectoryUrls: Collection<String> get() = emptySet()
  override val shadersDirectories: Collection<VirtualFile> get() = emptySet()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false

    val that = other as LegacyDelegate?
    return facet == that!!.facet
  }

  override fun hashCode(): Int = facet.hashCode()
}

/** Convert a set of IO files into a set of IDEA file urls referring to equivalent virtual files  */
private fun convertToUrlSet(fileSet: Collection<File>): Collection<String> = fileSet.map { VfsUtil.fileToUrl(it) }

private fun String.convertToUrl() = VfsUtil.pathToUrl(this)

