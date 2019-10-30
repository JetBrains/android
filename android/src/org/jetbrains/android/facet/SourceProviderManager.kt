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
import com.android.tools.idea.projectsystem.IdeaSourceProvider
import com.android.tools.idea.projectsystem.IdeaSourceProviderImpl
import com.android.utils.reflection.qualifiedName
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
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.io.File

interface SourceProviderManager {
  companion object {
    @JvmStatic
    fun getInstance(facet: AndroidFacet) = facet.sourceProviderManager

    /**
     * Replaces the instances of SourceProviderManager for the given [facet] with a test stub based on a single source set [sourceSet].
     *
     * NOTE: The test instance is automatically discarded on any relevant change to the [facet].
     */
    @JvmStatic
    fun replaceForTest(facet: AndroidFacet, disposable: Disposable, sourceSet: IdeaSourceProvider) {
      facet.putUserData(KEY, object: SourceProviders {
        override val currentSourceProviders: List<IdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val currentTestSourceProviders: List<IdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val allSourceProviders: List<IdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        @Suppress("OverridingDeprecatedMember")
        override val mainAndFlavorSourceProviders: List<IdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val mainIdeaSourceProvider: IdeaSourceProvider
          get() = sourceSet
        override val mainManifestFile: VirtualFile?
          get() = sourceSet.manifestFile
      })
      Disposer.register(disposable, Disposable { facet.putUserData(KEY, null) })
    }

    /**
     * Replaces the instances of SourceProviderManager for the given [facet] with a test stub based on a [manifestFile] only.
     *
     * NOTE: The test instance is automatically discarded on any relevant change to the [facet].
     */
    @JvmStatic
    fun replaceForTest(facet: AndroidFacet, disposable: Disposable, manifestFile: VirtualFile?) {
      facet.putUserData(KEY, object: SourceProviders {
        override val currentSourceProviders: List<IdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val currentTestSourceProviders: List<IdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val allSourceProviders: List<IdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        @Suppress("OverridingDeprecatedMember")
        override val mainAndFlavorSourceProviders: List<IdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val mainIdeaSourceProvider: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val mainManifestFile: VirtualFile?
          get() = manifestFile
      })
      Disposer.register(disposable, Disposable { facet.putUserData(KEY, null) })
    }
  }
}

val AndroidFacet.sourceProviderManager: SourceProviders get() = getUserData(KEY) ?: createSourceProviderFor(this)

/**
 * A base class to implement SourceProviderManager which provides default implementations for manifest related helper methods.
 */
private abstract class SourceProviderManagerBase(val facet: AndroidFacet) : SourceProviders {

  override val mainManifestFile: VirtualFile? get() {
    // When opening a project, many parts of the IDE will try to read information from the manifest. If we close the project before
    // all of this finishes, we may end up creating disposable children of an already disposed facet. This is a rather hard problem in
    // general, but pretending there was no manifest terminates many code paths early.
    return if (facet.isDisposed) null else return mainIdeaSourceProvider.manifestFile
  }
}

private class SourceProviderManagerImpl(facet: AndroidFacet, model: AndroidModel) : SourceProviderManagerBase(facet) {

  override val mainIdeaSourceProvider: IdeaSourceProvider
  override val currentSourceProviders: List<IdeaSourceProvider>
  override val currentTestSourceProviders: List<IdeaSourceProvider>
  override val allSourceProviders: List<IdeaSourceProvider>

  @Suppress("OverridingDeprecatedMember")
  override val mainAndFlavorSourceProviders: List<IdeaSourceProvider>

  init {
    val all =
      @Suppress("DEPRECATION")
      (
        model.allSourceProviders.asSequence() +
        model.activeSourceProviders.asSequence() +
        model.testSourceProviders.asSequence() +
        model.defaultSourceProvider +
        (model as? AndroidModuleModel)?.flavorSourceProviders?.asSequence().orEmpty()
      )
        .toSet()
        .associateWith {
          IdeaSourceProviderImpl(
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

    fun SourceProvider.toIdeaSourceProvider() = all.getValue(this)

    mainIdeaSourceProvider = @Suppress("DEPRECATION") model.defaultSourceProvider.toIdeaSourceProvider()
    currentSourceProviders = @Suppress("DEPRECATION") model.activeSourceProviders.map { it.toIdeaSourceProvider() }
    currentTestSourceProviders = @Suppress("DEPRECATION") model.testSourceProviders.map { it.toIdeaSourceProvider() }
    allSourceProviders = @Suppress("DEPRECATION") model.allSourceProviders.map { it.toIdeaSourceProvider() }

    @Suppress("DEPRECATION")
    mainAndFlavorSourceProviders =
      (model as? AndroidModuleModel)
        ?.let { listOf(mainIdeaSourceProvider) + @Suppress("DEPRECATION") it.flavorSourceProviders.map { it.toIdeaSourceProvider() } }
      ?: emptyList()
  }
}

private class LegacySourceProviderManagerImpl(facet: AndroidFacet) : SourceProviderManagerBase(facet) {

  @Volatile
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

private val KEY: Key<SourceProviders> = Key.create(::KEY.qualifiedName)

private fun createSourceProviderFor(facet: AndroidFacet): SourceProviders {
  val model = if (AndroidModel.isRequired(facet)) AndroidModel.get(facet) else null
  return if (model != null) SourceProviderManagerImpl(facet, model) else LegacySourceProviderManagerImpl(facet)
}

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
/** Convert a set of IO files into a set of IDEA file urls referring to equivalent virtual files  */
private fun convertToUrlSet(fileSet: Collection<File>): Collection<String> = fileSet.map { VfsUtil.fileToUrl(it) }
