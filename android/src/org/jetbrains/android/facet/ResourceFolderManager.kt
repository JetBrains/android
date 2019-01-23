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
import com.android.tools.idea.res.AndroidProjectRootListener
import com.google.common.base.Splitter
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.containers.hash.HashSet
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import javax.annotation.concurrent.GuardedBy
import kotlin.concurrent.withLock

/**
 * The resource folder manager is responsible for returning the current set of resource folders used in the project. It provides hooks for
 * getting notified when the set of folders changes (e.g. due to variant selection changes, or the folder set changing due to the user
 * editing the gradle files or after a delayed project initialization), and it also provides some state caching between IDE sessions such
 * that before the gradle initialization is done, it returns the folder set as it was before the IDE exited.
 */
class ResourceFolderManager private constructor(facet: AndroidFacet) : AndroidFacetScopedService(facet), ModificationTracker {

  /** Listeners for resource folder changes  */
  interface ResourceFolderListener {
    /** The resource folders in this project has changed  */
    fun mainResourceFoldersChanged(
      facet: AndroidFacet,
      folders: List<VirtualFile>,
      added: Collection<VirtualFile>,
      removed: Collection<VirtualFile>
    )

    /** The resource folders in this project has changed  */
    fun testResourceFoldersChanged(
      facet: AndroidFacet,
      folders: List<VirtualFile>,
      added: Collection<VirtualFile>,
      removed: Collection<VirtualFile>
    )
  }

  private data class Folders(val main: List<VirtualFile>, val test: List<VirtualFile>)

  private val lock = ReentrantLock()
  @GuardedBy("this") private var foldersCache: Folders? = null
  @GuardedBy("lock") private var variantListenerAdded: Boolean = false
  @GuardedBy("lock") private val listeners = mutableListOf<ResourceFolderListener>()
  @Volatile private var generation: Long = 0

  fun addListener(listener: ResourceFolderListener) = lock.withLock { listeners.add(listener) }
  fun removeListener(listener: ResourceFolderListener) = lock.withLock { listeners.remove(listener) }
  override fun getModificationCount() = generation
  override fun onServiceDisposal(facet: AndroidFacet) = facet.putUserData(KEY, null)


  /**
   * Returns main (production) resource directories, in increasing precedence order.
   *
   * @see IdeaSourceProvider.getCurrentSourceProviders
   */
  val folders get() = lock.withLock { mainAndTestFolders.main }

  /**
   * Returns test resource directories, in the overlay order.
   *
   * @see IdeaSourceProvider.getCurrentTestSourceProviders
   */
  val testFolders get() = mainAndTestFolders.test

  private val mainAndTestFolders: Folders
    @Synchronized get() {
      return foldersCache ?: computeFolders().also { foldersCache = it }
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
  fun invalidate() = lock.withLock {
    val before = foldersCache
    if (before == null || isDisposed) {
      return
    }
    foldersCache = null
    val after = mainAndTestFolders
    notifyIfChanged(before, after, Folders::main, ResourceFolderListener::mainResourceFoldersChanged)
    notifyIfChanged(before, after, Folders::test, ResourceFolderListener::testResourceFoldersChanged)
  }

  private inline fun notifyIfChanged(
    before: Folders,
    after: Folders,
    filesToCheck: Folders.() -> List<VirtualFile>,
    callback: ResourceFolderListener.(AndroidFacet, List<VirtualFile>, Collection<VirtualFile>, Collection<VirtualFile>) -> Unit
  ) {
    val filesBefore = before.filesToCheck()
    val filesAfter = after.filesToCheck()
    if (filesBefore != filesAfter) {
      generation++
      val added = HashSet<VirtualFile>(after.main.size)
      added.addAll(after.main)
      added.removeAll(before.main)

      val removed = HashSet<VirtualFile>(before.main.size)
      removed.addAll(before.main)
      removed.removeAll(after.main)

      for (listener in listeners) {
        listener.callback(facet, after.filesToCheck(), added, removed)
      }
    }
  }

  private fun computeFolders(): Folders {
    val facet = this.facet
    return if (!facet.requiresAndroidModel()) {
      Folders(main = facet.mainIdeaSourceProvider.resDirectories.toList(), test = emptyList())
    } else {
      // Listen to root change events. Be notified when project is initialized so we can update the
      // resource set, if necessary.
      AndroidProjectRootListener.ensureSubscribed(facet.module.project)
      if (facet.configuration.model == null) readFromFacetState(facet) else readFromModel(facet)
    }
  }

  private fun readFromModel(facet: AndroidFacet): Folders {
    val mainResDirectories = IdeaSourceProvider.getCurrentSourceProviders(facet).flatMap { it.resDirectories }
    val testResDirectories = IdeaSourceProvider.getCurrentTestSourceProviders(facet).flatMap { it.resDirectories }

    // Write string property such that subsequent restarts can look up the most recent list before the gradle model has been initialized
    // asynchronously.
    facet.configuration.state?.apply {
      RES_FOLDERS_RELATIVE_PATH = mainResDirectories.joinToString(SEPARATOR) { it.url }
      TEST_RES_FOLDERS_RELATIVE_PATH = testResDirectories.joinToString(SEPARATOR) { it.url }
    }

    // Also refresh the app resources whenever the variant changes.
    if (!variantListenerAdded) {
      variantListenerAdded = true
      BuildVariantUpdater.getInstance(facet.module.project).addSelectionChangeListener(this::invalidate)
    }

    return Folders(mainResDirectories, testResDirectories)
  }

  private fun readFromFacetState(facet: AndroidFacet): Folders {
    val state = facet.configuration.state ?: return emptyFolders
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
    return Splitter.on(SEPARATOR)
      .omitEmptyStrings()
      .trimResults()
      .split(this)
      .mapNotNull(manager::findFileByUrl)
  }

  companion object {
    private val KEY = Key.create<ResourceFolderManager>(ResourceFolderManager::class.java.name)

    /**
     * Separator used when encoding the list of res folders in the facet's state. Deliberately using ';' instead of [File.pathSeparator]
     * since on Unix [File.pathSeparator] is ":" which is also used in URLs, meaning we could end up with something like
     * `file://foo:file://bar`
     */
    private const val SEPARATOR = ";"

    private val emptyFolders = Folders(emptyList(), emptyList())

    @JvmStatic
    fun getInstance(facet: AndroidFacet): ResourceFolderManager {
      synchronized(KEY) {
        var resourceFolderManager = facet.getUserData(KEY)
        if (resourceFolderManager == null) {
          resourceFolderManager = ResourceFolderManager(facet)
          facet.putUserData(KEY, resourceFolderManager)
        }
        return resourceFolderManager
      }
    }
  }
}
