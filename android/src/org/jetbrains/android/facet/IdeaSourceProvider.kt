/*
 * Copyright (C) 2013 The Android Open Source Project
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
package org.jetbrains.android.facet

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.builder.model.AndroidProject
import com.android.builder.model.SourceProvider
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.model.AndroidModel
import com.google.common.collect.Lists
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil.filesEqual
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import com.intellij.openapi.vfs.VfsUtilCore.isAncestor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil.flatten
import org.jetbrains.android.facet.AndroidRootUtil.getAidlGenDir
import org.jetbrains.android.facet.AndroidRootUtil.getAidlGenSourceRootPath
import org.jetbrains.android.facet.AndroidRootUtil.getAssetsDir
import org.jetbrains.android.facet.AndroidRootUtil.getFileByRelativeModulePath
import org.jetbrains.android.facet.AndroidRootUtil.getRenderscriptGenDir
import org.jetbrains.android.facet.AndroidRootUtil.getRenderscriptGenSourceRootPath
import java.io.File

/**
 * Like [SourceProvider], but for IntelliJ, which means it provides
 * [VirtualFile] and IDEA's url references rather than [File] references.
 *
 * Note: VirtualFile versions may return fewer items or a null manifest where the url versions return a non-empty url(s) if the
         file referred to does not exist in the VFS.
 *
 * @see VirtualFile.getUrl
 */
interface IdeaSourceProvider {

  val name: String

  val manifestFileUrl: String
  val manifestFile: VirtualFile?

  val javaDirectoryUrls: Collection<String>
  val javaDirectories: Collection<VirtualFile>

  val resourcesDirectoryUrls: Collection<String>
  val resourcesDirectories: Collection<VirtualFile>

  val aidlDirectoryUrls: Collection<String>
  val aidlDirectories: Collection<VirtualFile>

  val renderscriptDirectoryUrls: Collection<String>
  val renderscriptDirectories: Collection<VirtualFile>

  val jniDirectoryUrls: Collection<String>
  val jniDirectories: Collection<VirtualFile>

  val jniLibsDirectoryUrls: Collection<String>
  val jniLibsDirectories: Collection<VirtualFile>

  val resDirectoryUrls: Collection<String>
  val resDirectories: Collection<VirtualFile>

  val assetsDirectoryUrls: Collection<String>
  val assetsDirectories: Collection<VirtualFile>

  val shadersDirectoryUrls: Collection<String>
  val shadersDirectories: Collection<VirtualFile>

  companion object {

    /**
     * Returns an [IdeaSourceProvider] wrapping the given [SourceProvider].
     */
    @JvmStatic
    fun toIdeaProvider(sourceProvider: SourceProvider): IdeaSourceProvider = Delegate(sourceProvider)

    private fun List<SourceProvider>.toIdeaProviders(): List<IdeaSourceProvider> = map(::toIdeaProvider)

    /**
     * Returns an [IdeaSourceProvider] for legacy android projects that do not require [AndroidProject]
     * by extracting source set information from the given [AndroidFacet].
     * For android projects with [AndroidProject] support see [IdeaSourceProvider.toIdeaProvider].
     */
    @JvmStatic
    fun createForLegacyProject(facet: AndroidFacet): IdeaSourceProvider = LegacyDelegate(facet)

    /**
     * Returns a list of source providers, in the overlay order (meaning that later providers
     * override earlier providers when they redefine resources) for the currently selected variant.
     *
     * Note that the list will never be empty; there is always at least one source provider.
     *
     * The overlay source order is defined by the underlying build system.
     */
    @JvmStatic
    fun getCurrentSourceProviders(facet: AndroidFacet): List<IdeaSourceProvider> {
      return if (!AndroidModel.isRequired(facet)) {
        listOf(SourceProviderManager.getInstance(facet).mainIdeaSourceProvider)
      }
      else {
        @Suppress("DEPRECATION")
        AndroidModel.get(facet)?.activeSourceProviders?.toIdeaProviders().orEmpty()
      }
    }

    /**
     * Returns a list of source providers which includes the main source provider and
     * product flavor specific source providers.
     *
     * DEPRECATED: This is method is added here to support android-kotlin-extensions which
     * for compatibility reasons require this particular subset of source providers.
     */
    @Deprecated("Do not use. This is unlikely to be what anybody needs.")
    @JvmStatic
    fun getMainAndFlavorSourceProviders(facet: AndroidFacet): List<IdeaSourceProvider> {
      if (!AndroidModel.isRequired(facet)) {
        return listOf(SourceProviderManager.getInstance(facet).mainIdeaSourceProvider)
      }
      else {
        val androidModel = AndroidModuleModel.get(facet)
        if (androidModel != null) {
          val result = mutableListOf<IdeaSourceProvider>()
          result.add(SourceProviderManager.getInstance(facet).mainIdeaSourceProvider)
          result.addAll(androidModel.flavorSourceProviders.toIdeaProviders())
          return result
        }
        else {
          return emptyList()
        }
      }
    }

    /**
     * Returns a list of source providers for all test artifacts (e.g. both `test/` and `androidTest/` source sets), in increasing
     * precedence order.
     *
     * @see getCurrentSourceProviders
     */
    @JvmStatic
    fun getCurrentTestSourceProviders(facet: AndroidFacet): List<IdeaSourceProvider> {
      return if (!AndroidModel.isRequired(facet)) {
        emptyList()
      }
      else {
        @Suppress("DEPRECATION")
        AndroidModel.get(facet)?.testSourceProviders?.toIdeaProviders().orEmpty()
      }
    }

    /**
     * Returns true if this SourceProvider has one or more source folders contained by (or equal to)
     * the given folder.
     */
    @JvmStatic
    fun isContainedBy(provider: IdeaSourceProvider, targetFolder: VirtualFile): Boolean {
      val srcDirectories = provider.allSourceFolders
      for (container in srcDirectories) {
        if (isAncestor(targetFolder, container, false)) {
          return true
        }

        if (!container.exists()) {
          continue
        }

        if (isAncestor(targetFolder, container, false /* allow them to be the same */)) {
          return true
        }
      }
      return false
    }

    /**
     * Returns true iff this SourceProvider provides the source folder that contains the given file.
     */
    @JvmStatic
    fun containsFile(provider: IdeaSourceProvider, file: VirtualFile): Boolean {
      val srcDirectories = provider.allSourceFolders
      if (provider.manifestFile == file) {
        return true
      }

      for (container in srcDirectories) {
        // Check the flavor root directories
        val parent = container.parent
        if (parent != null && parent.isDirectory && parent == file) {
          return true
        }

        // Don't do ancestry checking if this file doesn't exist
        if (!container.exists()) {
          continue
        }

        if (isAncestor(container, file, false /* allow them to be the same */)) {
          return true
        }
      }
      return false
    }

    /**
     * Returns a list of all IDEA source providers, for the given facet, in the overlay order
     * (meaning that later providers override earlier providers when they redefine resources.)
     *
     *
     * Note that the list will never be empty; there is always at least one source provider.
     *
     *
     * The overlay source order is defined by the underlying build system.
     *
     * This method should be used when only on-disk source sets are required. It will return
     * empty source sets for all other source providers (since VirtualFiles MUST exist on disk).
     */
    @JvmStatic
    fun getAllIdeaSourceProviders(facet: AndroidFacet): List<IdeaSourceProvider> {
      return if (!AndroidModel.isRequired(facet) || AndroidModel.get(facet) == null) {
        listOf(SourceProviderManager.getInstance(facet).mainIdeaSourceProvider)
      }
      else {
        AndroidModel.get(facet)!!.allSourceProviders.toIdeaProviders()
      }
    }

    /**
     * Returns a list of all source providers that contain, or are contained by, the given file.
     * For example, with the file structure:
     *
     * ```
     * src
     *   main
     *     aidl
     *       myfile.aidl
     *   free
     *     aidl
     *       myoverlay.aidl
     * ```
     *
     * With target file == "myoverlay.aidl" the returned list would be ['free'], but if target file == "src",
     * the returned list would be ['main', 'free'] since both of those source providers have source folders which
     * are descendants of "src."
     */
    @JvmStatic
    fun getSourceProvidersForFile(
      facet: AndroidFacet,
      targetFolder: VirtualFile?,
      defaultSourceProvider: IdeaSourceProvider?
    ): List<IdeaSourceProvider> {
      val sourceProviderList =
        if (targetFolder != null) {
          // Add source providers that contain the file (if any) and any that have files under the given folder
          getAllIdeaSourceProviders(facet)
            .filter { provider -> containsFile(provider, targetFolder) || isContainedBy(provider, targetFolder) }
            .takeUnless { it.isEmpty() }
        }
        else null

      return sourceProviderList ?: listOfNotNull(defaultSourceProvider)
    }

    @JvmStatic
    fun isTestFile(facet: AndroidFacet, candidate: VirtualFile): Boolean {
      return getCurrentTestSourceProviders(facet).any { containsFile(it, candidate) }
    }

    /** Returns true if the given candidate file is a manifest file in the given module  */
    @JvmStatic
    fun isManifestFile(facet: AndroidFacet, candidate: VirtualFile): Boolean {
      return if (AndroidModel.isRequired(facet)) {
        getCurrentSourceProviders(facet).any { candidate == it.manifestFile }
      }
      else {
        candidate == SourceProviderManager.getInstance(facet).mainIdeaSourceProvider.manifestFile
      }
    }

    /** Returns the manifest files in the given module  */
    @JvmStatic
    fun getManifestFiles(facet: AndroidFacet): List<VirtualFile> {
      val main = SourceProviderManager.getInstance(facet).mainIdeaSourceProvider.manifestFile
      if (!AndroidModel.isRequired(facet)) {
        return if (main != null) listOf(main) else emptyList()
      }

      return getCurrentSourceProviders(facet).mapNotNull { it.manifestFile }
    }
  }
}

/** [IdeaSourceProvider] wrapping a [SourceProvider].  */
private class Delegate constructor(private val provider: SourceProvider) : IdeaSourceProvider {
  private var myManifestFile: VirtualFile? = null
  private var myManifestIoFile: File? = null

  override val name: String get() = provider.name

  override val manifestFileUrl: String get() = VfsUtil.fileToUrl(provider.manifestFile)

  override val manifestFile: VirtualFile?
    get() {
      val manifestFile = provider.manifestFile
      if (myManifestFile == null || !myManifestFile!!.isValid || !filesEqual(manifestFile, myManifestIoFile)) {
        myManifestIoFile = manifestFile
        myManifestFile = findFileByIoFile(manifestFile, false)
      }

      return myManifestFile
    }

  override val javaDirectoryUrls: Collection<String> get() = convertToUrlSet(provider.javaDirectories)
  override val javaDirectories: Collection<VirtualFile> get() = convertFileSet(provider.javaDirectories)

  override val resourcesDirectoryUrls: Collection<String> get() = convertToUrlSet(provider.resourcesDirectories)
  override val resourcesDirectories: Collection<VirtualFile> get() = convertFileSet(provider.resourcesDirectories)

  override val aidlDirectoryUrls: Collection<String> get() = convertToUrlSet(provider.aidlDirectories)
  override val aidlDirectories: Collection<VirtualFile> get() = convertFileSet(provider.aidlDirectories)

  override val renderscriptDirectoryUrls: Collection<String> get() = convertToUrlSet(provider.renderscriptDirectories)
  override val renderscriptDirectories: Collection<VirtualFile> get() = convertFileSet(provider.renderscriptDirectories)

  override val jniDirectoryUrls: Collection<String> get() = convertToUrlSet(provider.cDirectories + provider.cppDirectories).toSet()
  override val jniDirectories: Collection<VirtualFile>
    // Even though the model has separate methods to get the C and Cpp directories,
    // they both return the same set of folders. So we combine them here.
    get() = convertFileSet(provider.cDirectories + provider.cppDirectories).toSet()

  override val jniLibsDirectoryUrls: Collection<String> get() = convertToUrlSet(provider.jniLibsDirectories)
  override val jniLibsDirectories: Collection<VirtualFile> get() = convertFileSet(provider.jniLibsDirectories)

  // TODO: Perform some caching; this method gets called a lot!
  override val resDirectoryUrls: Collection<String> get() = convertToUrlSet(provider.resDirectories)
  override val resDirectories: Collection<VirtualFile> get() = convertFileSet(provider.resDirectories)

  override val assetsDirectoryUrls: Collection<String> get() = convertToUrlSet(provider.assetsDirectories)
  override val assetsDirectories: Collection<VirtualFile> get() = convertFileSet(provider.assetsDirectories)

  override val shadersDirectoryUrls: Collection<String> get() = convertToUrlSet(provider.shadersDirectories)
  override val shadersDirectories: Collection<VirtualFile> get() = convertFileSet(provider.shadersDirectories)

  /** Convert a set of IO files into a set of equivalent virtual files  */
  private fun convertFileSet(fileSet: Collection<File>): Collection<VirtualFile> {
    val fileSystem = LocalFileSystem.getInstance()
    return fileSet.mapNotNullTo(Lists.newArrayListWithCapacity(fileSet.size)) { fileSystem.findFileByIoFile(it) }
  }

  /** Convert a set of IO files into a set of equivalent virtual files  */
  private fun convertToUrlSet(fileSet: Collection<File>): Collection<String> =
    fileSet.mapTo(Lists.newArrayListWithCapacity(fileSet.size)) { VfsUtil.fileToUrl(it) }

  /**
   * Compares another source provider delegate with this for equality. Returns true if the specified object is also a
   * [Delegate], has the same name, and the same manifest file path.
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false

    val that = other as Delegate?
    if (provider.name != that!!.name) return false
    // Only check the manifest file, as each SourceProvider will be guaranteed to have a different manifest file.
    if (provider.manifestFile.path != that.provider.manifestFile.path) return false

    return true
  }

  /**
   * Returns the hash code for this source provider. The hash code simply provides the hash of the manifest file's location,
   * but this follows the required contract that if two source providers are equal, their hash codes will be the same.
   */
  override fun hashCode(): Int {
    return provider.manifestFile.path.hashCode()
  }
}

/** [IdeaSourceProvider] for legacy Android projects without [SourceProvider].  */
private class LegacyDelegate constructor(private val facet: AndroidFacet) : IdeaSourceProvider {

  override val name: String = ""

  override val manifestFileUrl: String get() = manifestFile?.url ?: let {
    val contentRoots = ModuleRootManager.getInstance(facet.module).contentRoots
    return if (contentRoots.isNotEmpty()) {
      contentRoots[0].url + "/" + ANDROID_MANIFEST_XML
    } else {
      throw IllegalStateException("Content root is required to determine manifestFileUrl")
    }
  }

  override val manifestFile: VirtualFile?
    // Not calling AndroidRootUtil.getMainContentRoot(myFacet) because that method can
    // recurse into this same method if it can't find a content root. (This scenario
    // applies when we're looking for manifests in for example a temporary file system,
    // as tested by ResourceTypeInspectionTest#testLibraryRevocablePermission)
    get() {
      val module = facet.module
      val file = getFileByRelativeModulePath(module, facet.properties.MANIFEST_FILE_RELATIVE_PATH, true)
      if (file != null) {
        return file
      }
      val contentRoots = ModuleRootManager.getInstance(module).contentRoots
      if (contentRoots.size == 1) {
        return contentRoots[0].findChild(ANDROID_MANIFEST_XML)
      }
      return null
    }

  override val javaDirectoryUrls: Collection<String> get() = ModuleRootManager.getInstance(facet.module).contentRootUrls.toSet()
  override val javaDirectories: Collection<VirtualFile> get() = ModuleRootManager.getInstance(facet.module).contentRoots.toSet()

  override val resourcesDirectoryUrls: Collection<String> get() = emptySet()
  override val resourcesDirectories: Collection<VirtualFile> get() = emptySet()

  override val aidlDirectoryUrls: Collection<String> get() = listOfNotNull(getAidlGenSourceRootPath(facet)?.convertToUrl())
  override val aidlDirectories: Collection<VirtualFile> get() = listOfNotNull(getAidlGenDir(facet))

  override val renderscriptDirectoryUrls: Collection<String> get() = listOfNotNull(getRenderscriptGenSourceRootPath(facet)?.convertToUrl())
  override val renderscriptDirectories: Collection<VirtualFile> get() = listOfNotNull(getRenderscriptGenDir(facet))

  override val jniDirectoryUrls: Collection<String> get() = emptySet()
  override val jniDirectories: Collection<VirtualFile> get() = emptySet()

  override val jniLibsDirectoryUrls: Collection<String> get() = emptySet()
  override val jniLibsDirectories: Collection<VirtualFile> get() = emptySet()

  override val resDirectoryUrls: Collection<String> get() = resDirectories.map { it.url }
  override val resDirectories: Collection<VirtualFile>
    get() {
      val resRelPath = facet.properties.RES_FOLDER_RELATIVE_PATH
      return listOfNotNull(getFileByRelativeModulePath(facet.run { module }, resRelPath, true))
    }

  override val assetsDirectoryUrls: Collection<String> get() = assetsDirectories.map { it.url }
  override val assetsDirectories: Collection<VirtualFile>
    get() {
      val dir = getAssetsDir(facet)
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

private val IdeaSourceProvider.allSourceFolders: Collection<VirtualFile>
  get() =
    flatten(arrayOf(javaDirectories, resDirectories, aidlDirectories, renderscriptDirectories, assetsDirectories, jniDirectories,
                    jniLibsDirectories))

private fun String.convertToUrl() = VfsUtil.pathToUrl(this)
