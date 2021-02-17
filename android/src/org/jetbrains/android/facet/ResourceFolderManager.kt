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

import com.android.SdkConstants.FD_MAIN
import com.android.SdkConstants.FD_RES
import com.android.SdkConstants.FD_SOURCES
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.variant.view.BuildVariantUpdater
import com.android.tools.idea.gradle.variant.view.BuildVariantView
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.res.AndroidProjectRootListener
import com.android.tools.idea.util.androidFacet
import com.google.common.base.Splitter
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.messages.Topic

/**
 * The resource folder manager is responsible for returning the current set of resource folders used in the project. It provides hooks for
 * getting notified when the set of folders changes (e.g. due to variant selection changes, or the folder set changing due to the user
 * editing the gradle files or after a delayed project initialization), and it also provides some state caching between IDE sessions such
 * that before the gradle initialization is done, it returns the folder set as it was before the IDE exited.
 */
class ResourceFolderManager(val module: Module) : ModificationTracker, Disposable {
  companion object {
    private val FOLDERS_KEY = Key.create<Folders>(ResourceFolderManager::class.qualifiedName!!)

    private val emptyFolders = Folders(emptyList(), emptyList())

    @JvmStatic
    fun getInstance(facet: AndroidFacet): ResourceFolderManager {
      return facet.module.getService(ResourceFolderManager::class.java)!!
    }

    @JvmField
    @Topic.ProjectLevel
    internal val TOPIC = Topic(ResourceFolderListener::class.java)
  }

  /** Listeners for resource folder changes  */
  interface ResourceFolderListener {
    /** The resource folders in this project has changed  */
    fun mainResourceFoldersChanged(
      facet: AndroidFacet,
      folders: List<VirtualFile>
    )

    /** The resource folders in this project has changed  */
    fun testResourceFoldersChanged(
      facet: AndroidFacet,
      folders: List<VirtualFile>
    )
  }

  private data class Folders(val main: List<VirtualFile>, val test: List<VirtualFile>)

  private val listener = BuildVariantView.BuildVariantSelectionChangeListener { checkForChanges() }
  @Volatile private var generation: Long = 0

  init {
    AndroidProjectRootListener.ensureSubscribed(module.project)
    BuildVariantUpdater.getInstance(module.project).addSelectionChangeListener(listener)
  }

  override fun getModificationCount() = generation

  override fun dispose() {
    BuildVariantUpdater.getInstance(module.project).removeSelectionChangeListener(listener)
  }

  /**
   * Returns main (production) resource directories, in increasing precedence order.
   *
   * @see com.android.tools.idea.projectsystem.SourceProviders.currentSourceProviders
   */
  val folders get() = mainAndTestFolders.main

  /**
   * Returns test resource directories, in the overlay order.
   *
   * @see com.android.tools.idea.projectsystem.SourceProviders.currentAndroidTestSourceProviders
   */
  val testFolders get() = mainAndTestFolders.test

  private val mainAndTestFolders: Folders
    get() {
      val facet = module.androidFacet ?: return emptyFolders
      return facet.getUserData(FOLDERS_KEY) ?: facet.putUserDataIfAbsent(FOLDERS_KEY, computeFolders(facet))
    }

  /**
   * This returns the primary resource directory; the default location to place newly created resources etc.
   *
   * This method is marked deprecated since we should be gradually adding in UI to allow users to choose specific resource folders among
   * the available flavors (see [AndroidModuleModel.getFlavorSourceProviders] etc).
   *
   * @return the primary resource dir, if any.
   */
  @Suppress("DeprecatedCallableAddReplaceWith") // The method body is not the recommended replacement, see above.
  @Deprecated("Instead of calling this, ask the user which resource folder should be used.")
  val primaryFolder get() = folders.firstOrNull()

  /** Notifies the resource folder manager that the resource folder set may have changed.  */
  fun checkForChanges() {
    if (module.isDisposed) return
    val facet = AndroidFacet.getInstance(module) ?: return
    val before = facet.getUserData(FOLDERS_KEY) ?: return
    facet.putUserData(FOLDERS_KEY, null)
    val after = mainAndTestFolders
    notifyIfChanged(before, after, Folders::main) { resourceFolderListener, folders ->
      resourceFolderListener.mainResourceFoldersChanged(facet, folders)
    }
    notifyIfChanged(before, after, Folders::test) { resourceFolderListener, folders ->
      resourceFolderListener.testResourceFoldersChanged(facet, folders)
    }
  }

  private inline fun notifyIfChanged(before: Folders,
                                     after: Folders,
                                     filesToCheck: (Folders) -> List<VirtualFile>,
                                     callback: (ResourceFolderListener, List<VirtualFile>) -> Unit) {
    val filesBefore = filesToCheck(before)
    val filesAfter = filesToCheck(after)
    if (filesBefore != filesAfter) {
      generation++
      callback(module.project.messageBus.syncPublisher(TOPIC), filesToCheck(after))
    }
  }


  private fun computeFolders(facet: AndroidFacet): Folders {
    return if (!AndroidModel.isRequired(facet)) {
      Folders(main = SourceProviderManager.getInstance(facet).mainIdeaSourceProvider.resDirectories.toList(), test = emptyList())
    }
    else {
      // Listen to root change events. Be notified when project is initialized so we can update the
      // resource set, if necessary.
      readFromFacetState(facet)
    }
  }

  private fun readFromFacetState(facet: AndroidFacet): Folders {
    val state = facet.configuration.state
    val mainFolders = state.RES_FOLDERS_RELATIVE_PATH
    return if (mainFolders != null) {
      // We have state saved in the facet.
      val manager = VirtualFileManager.getInstance()
      Folders(
        main = mainFolders.toVirtualFiles(manager),
        test = state.TEST_RES_FOLDERS_RELATIVE_PATH?.toVirtualFiles(manager).orEmpty()
      )
    }
    else {
      // First time; have not yet computed the res folders, just try the default: src/main/res/ from Gradle templates, res/ from exported
      // Eclipse projects.
      Folders(
        main = listOf(
          AndroidRootUtil.getFileByRelativeModulePath(facet.module, "/$FD_SOURCES/$FD_MAIN/$FD_RES", true)
          ?: AndroidRootUtil.getFileByRelativeModulePath(facet.module, "/$FD_RES", true)
          ?: return emptyFolders
        ),
        test = emptyList()
      )
    }
  }

  private fun String.toVirtualFiles(manager: VirtualFileManager): List<VirtualFile> {
    return Splitter.on(AndroidFacetProperties.PATH_LIST_SEPARATOR_IN_FACET_CONFIGURATION)
      .omitEmptyStrings()
      .trimResults()
      .split(this)
      .mapNotNull(manager::findFileByUrl)
  }
}
