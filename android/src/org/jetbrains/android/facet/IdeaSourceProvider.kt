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

import com.android.builder.model.SourceProvider
import com.intellij.openapi.vfs.VfsUtilCore.isAncestor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil.flatten
import java.io.File

/**
 * Like [SourceProvider], but for IntelliJ, which means it provides
 * [VirtualFile] and IDEA's url references rather than [File] references.
 *
 * Note: VirtualFile versions may return fewer items or a null manifest where the url versions return a non-empty url(s) if the file
 * referred to does not exist in the VFS.
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

    /**
     * Returns a list of source providers, in the overlay order (meaning that later providers
     * override earlier providers when they redefine resources) for the currently selected variant.
     *
     * Note that the list will never be empty; there is always at least one source provider.
     *
     * The overlay source order is defined by the underlying build system.
     */
    @JvmStatic
    fun getCurrentSourceProviders(facet: AndroidFacet): List<IdeaSourceProvider> =
      SourceProviderManager.getInstance(facet).currentSourceProviders

    /**
     * Returns a list of source providers which includes the main source provider and
     * product flavor specific source providers.
     *
     * DEPRECATED: This is method is added here to support android-kotlin-extensions which
     * for compatibility reasons require this particular subset of source providers.
     */
    @Deprecated("Do not use. This is unlikely to be what anybody needs.")
    @JvmStatic
    fun getMainAndFlavorSourceProviders(facet: AndroidFacet): List<IdeaSourceProvider> =
      @Suppress("DEPRECATION") SourceProviderManager.getInstance(facet).mainAndFlavorSourceProviders


    /**
     * Returns a list of source providers for all test artifacts (e.g. both `test/` and `androidTest/` source sets), in increasing
     * precedence order.
     *
     * @see getCurrentSourceProviders
     */
    @JvmStatic
    fun getCurrentTestSourceProviders(facet: AndroidFacet): List<IdeaSourceProvider> =
      SourceProviderManager.getInstance(facet).currentTestSourceProviders

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
    fun getAllIdeaSourceProviders(facet: AndroidFacet): List<IdeaSourceProvider> =
      SourceProviderManager.getInstance(facet).allSourceProviders

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
          SourceProviderManager.getInstance(facet).allSourceProviders
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
      return SourceProviderManager.getInstance(facet).currentSourceProviders.any { candidate == it.manifestFile }
    }

    /** Returns the manifest files in the given module  */
    @JvmStatic
    fun getManifestFiles(facet: AndroidFacet): List<VirtualFile> {
      return getCurrentSourceProviders(facet).mapNotNull { it.manifestFile }
    }
  }
}

private val IdeaSourceProvider.allSourceFolders: Collection<VirtualFile>
  get() =
    flatten(arrayOf(javaDirectories, resDirectories, aidlDirectories, renderscriptDirectories, assetsDirectories, jniDirectories,
                    jniLibsDirectories))

