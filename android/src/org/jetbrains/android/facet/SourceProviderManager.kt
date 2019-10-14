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
package org.jetbrains.android.facet

import com.android.SdkConstants
import com.android.builder.model.SourceProvider
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.model.AndroidModel
import com.android.utils.reflection.qualifiedName
import com.google.common.collect.Lists
import com.intellij.ProjectTopics
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerAdapter
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.DebugUtil
import java.io.File

// TODO(solodkyy): Remove default implementation when
interface SourceProviderManager {
  companion object {
    @JvmStatic
    fun getInstance(facet: AndroidFacet) = facet.sourceProviderManager

    /**
     * Replaces the instances of SourceProviderManager for the given [facet].
     *
     * NOTE: The test instance is automatically discarded on any relevant change to the [facet].
     */
    @JvmStatic
    fun replaceForTest(facet: AndroidFacet,
                       disposable: Disposable,
                       mock: SourceProviderManager) {
      facet.putUserData(KEY, mock)
      Disposer.register(disposable, Disposable { facet.putUserData(KEY, null) })
    }
  }

  val mainIdeaSourceProvider: IdeaSourceProvider

  val mainManifestFile: VirtualFile?

  /**
   * Returns a list of source providers, in the overlay order (meaning that later providers
   * override earlier providers when they redefine resources) for the currently selected variant.
   *
   * The overlay source order is defined by the underlying build system.
   */
  @JvmDefault
  val currentSourceProviders: List<IdeaSourceProvider>
    get() = throw UnsupportedOperationException()

  /**
   * Returns a list of source providers for all test artifacts (e.g. both `test/` and `androidTest/` source sets), in increasing
   * precedence order.
   *
   * @see currentSourceProviders
   */
  @JvmDefault
  val currentTestSourceProviders: List<IdeaSourceProvider>
    get() = throw UnsupportedOperationException()

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
  @JvmDefault
  val allSourceProviders: List<IdeaSourceProvider>
    get() = throw UnsupportedOperationException()

  /**
   * Returns a list of source providers which includes the main source provider and
   * product flavor specific source providers.
   *
   * DEPRECATED: This is method is added here to support android-kotlin-extensions which
   * for compatibility reasons require this particular subset of source providers.
   */
  @Deprecated("Do not use. This is unlikely to be what anybody needs.")
  @JvmDefault
  val mainAndFlavorSourceProviders: List<IdeaSourceProvider>
    get() = throw UnsupportedOperationException()
}

val AndroidFacet.sourceProviderManager: SourceProviderManager get() = getUserData(KEY) ?: createSourceProviderFor(this)

/**
 * A base class to implement SourceProviderManager which provides default implementations for manifest related helper methods.
 */
private abstract class SourceProviderManagerBase(val facet: AndroidFacet) : SourceProviderManager {

  override val mainManifestFile: VirtualFile? get() {
    // When opening a project, many parts of the IDE will try to read information from the manifest. If we close the project before
    // all of this finishes, we may end up creating disposable children of an already disposed facet. This is a rather hard problem in
    // general, but pretending there was no manifest terminates many code paths early.
    return if (facet.isDisposed) null else return mainIdeaSourceProvider.manifestFile
  }
}

private class SourceProviderManagerImpl(facet: AndroidFacet, private val model: AndroidModel) : SourceProviderManagerBase(facet) {
  private var mainIdeaSourceSet: IdeaSourceProvider? = null
  private var mainIdeaSourceSetCreatedFor: SourceProvider? = null

  /**
   * Returns the main source provider for the project. For projects that are not backed by a Gradle model, this method returns a
   * [SourceProvider] wrapper which provides information about the old project.
   */
  private val mainSourceProvider: SourceProvider get() = model.defaultSourceProvider

  override val mainIdeaSourceProvider: IdeaSourceProvider
    get() {
      val mainSourceSet = mainSourceProvider
      if (mainIdeaSourceSet == null || mainIdeaSourceSetCreatedFor != mainSourceSet) {
        mainIdeaSourceSet = Delegate(mainSourceSet)
        mainIdeaSourceSetCreatedFor = mainSourceSet
      }

      return mainIdeaSourceSet!!
    }

  override val currentSourceProviders: List<IdeaSourceProvider>
    get() = @Suppress("DEPRECATION") model.activeSourceProviders.toIdeaProviders()

  override val currentTestSourceProviders: List<IdeaSourceProvider>
    get() = @Suppress("DEPRECATION") model.testSourceProviders.toIdeaProviders()

  override val allSourceProviders: List<IdeaSourceProvider>
    get() = @Suppress("DEPRECATION") model.allSourceProviders.toIdeaProviders()

  @Suppress("OverridingDeprecatedMember")
  override val mainAndFlavorSourceProviders: List<IdeaSourceProvider>
    get() {
      val androidModel = model as? AndroidModuleModel ?: return emptyList()
      val result = mutableListOf<IdeaSourceProvider>()
      result.add(mainIdeaSourceProvider)
      result.addAll(androidModel.flavorSourceProviders.toIdeaProviders())
      return result
    }
}

private class LegacySourceProviderManagerImpl(facet: AndroidFacet) : SourceProviderManagerBase(facet) {

  private var mainIdeaSourceSet: IdeaSourceProvider? = null

  override val mainIdeaSourceProvider: IdeaSourceProvider
    get() {
      if (mainIdeaSourceSet == null) {
        mainIdeaSourceSet = LegacyDelegate(facet)
      }
      return mainIdeaSourceSet!!
    }

  override val currentSourceProviders: List<IdeaSourceProvider>
    get() = listOf(mainIdeaSourceProvider)

  override val currentTestSourceProviders: List<IdeaSourceProvider> = emptyList()

  override val allSourceProviders: List<IdeaSourceProvider>
    get() = listOf(mainIdeaSourceProvider)

  @Suppress("OverridingDeprecatedMember")
  override val mainAndFlavorSourceProviders: List<IdeaSourceProvider>
    get() = listOf(mainIdeaSourceProvider)
}

/** [IdeaSourceProvider] wrapping a [SourceProvider].  */
internal class Delegate constructor(private val provider: SourceProvider) : IdeaSourceProvider {
  private var myManifestFile: VirtualFile? = null
  private var myManifestIoFile: File? = null

  override val name: String get() = provider.name

  override val manifestFileUrl: String get() = VfsUtil.fileToUrl(provider.manifestFile)

  override val manifestFile: VirtualFile?
    get() {
      val manifestFile = provider.manifestFile
      if (myManifestFile == null || !myManifestFile!!.isValid || !FileUtil.filesEqual(manifestFile,
                                                                                      myManifestIoFile)) {
        myManifestIoFile = manifestFile
        myManifestFile = VfsUtil.findFileByIoFile(manifestFile, false)
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
      return listOfNotNull(AndroidRootUtil.getFileByRelativeModulePath(facet.run { module }, resRelPath, true))
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

private val KEY: Key<SourceProviderManager> = Key.create(::KEY.qualifiedName)

private fun createSourceProviderFor(facet: AndroidFacet): SourceProviderManager {
  val model = if (AndroidModel.isRequired(facet)) AndroidModel.get(facet) else null
  return if (model != null) SourceProviderManagerImpl(facet, model) else LegacySourceProviderManagerImpl(facet)
}

private fun List<SourceProvider>.toIdeaProviders(): List<IdeaSourceProvider> = map(::Delegate)

private fun String.convertToUrl() = VfsUtil.pathToUrl(this)

private fun onChanged(facet: AndroidFacet) {
  facet.putUserData(KEY, createSourceProviderFor(facet))
}

private class SourceProviderManagerComponent(val project: Project) : ProjectComponent {
  private val connection = project.messageBus.connect()

  init {
    var subscribedToRootsChangedEvents = false

    @Synchronized
    fun ensureSubscribed() {
      if (!subscribedToRootsChangedEvents) {
        connection.subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
          override fun rootsChanged(event: ModuleRootEvent) {
            ModuleManager.getInstance(project)
              .modules.asIterable()
              .mapNotNull { it -> FacetManager.getInstance(it).getFacetByType(AndroidFacet.ID) }.forEach { facet ->
                onChanged(facet)
              }
          }
        })
        subscribedToRootsChangedEvents = true
      }
    }

    connection.subscribe(FacetManager.FACETS_TOPIC, object : FacetManagerAdapter() {
      override fun facetConfigurationChanged(facet: Facet<*>) {
        if (facet is AndroidFacet) {
          ensureSubscribed()
          onChanged(facet)
        }
      }

      override fun facetAdded(facet: Facet<*>) {
        if (facet is AndroidFacet) {
          ensureSubscribed()
          onChanged(facet)
        }
      }
    })
  }

  override fun projectClosed() {
    connection.disconnect()
  }
}