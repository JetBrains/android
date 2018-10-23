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
import com.android.tools.idea.res.ProjectResourceRepositoryRootListener
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
    fun resourceFoldersChanged(
      facet: AndroidFacet,
      folders: List<VirtualFile>,
      added: Collection<VirtualFile>,
      removed: Collection<VirtualFile>
    )
  }

  private val lock = ReentrantLock()
  @GuardedBy("lock") private var resDirCache: List<VirtualFile>? = null
  @GuardedBy("lock") private var variantListenerAdded: Boolean = false
  @GuardedBy("lock") private val listeners = mutableListOf<ResourceFolderListener>()
  @Volatile private var generation: Long = 0

  fun addListener(listener: ResourceFolderListener) = lock.withLock { listeners.add(listener) }
  fun removeListener(listener: ResourceFolderListener) = lock.withLock { listeners.remove(listener) }
  override fun getModificationCount() = generation
  override fun onServiceDisposal(facet: AndroidFacet) = facet.putUserData(KEY, null)


  /**
   * Returns all resource directories, in the overlay order
   */
  val folders: List<VirtualFile>
    get() = lock.withLock {
      resDirCache ?: computeFolders().also { resDirCache = it }
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
    val old = resDirCache
    if (old == null || isDisposed) {
      return
    }
    resDirCache = null
    val new = folders
    if (old != new) {
      notifyChanged(old, new)
    }
  }

  private fun computeFolders(): List<VirtualFile> {
    val facet = this.facet
    return if (!facet.requiresAndroidModel()) {
      facet.mainIdeaSourceProvider.resDirectories.toList()
    } else {
      // Listen to root change events. Be notified when project is initialized so we can update the
      // resource set, if necessary.
      ProjectResourceRepositoryRootListener.ensureSubscribed(facet.module.project)
      if (facet.configuration.model == null) readFromFacetState(facet) else readFromModel(facet)
    }
  }

  private fun readFromModel(facet: AndroidFacet): List<VirtualFile> {
    val resDirectories = IdeaSourceProvider.getCurrentSourceProviders(facet).flatMap { it.resDirectories }

    // Write string property such that subsequent restarts can look up the most recent list before the gradle model has been initialized
    // asynchronously.
    facet.configuration.state?.RES_FOLDERS_RELATIVE_PATH = resDirectories.joinToString(SEPARATOR) { it.url }

    // Also refresh the app resources whenever the variant changes.
    if (!variantListenerAdded) {
      variantListenerAdded = true
      BuildVariantUpdater.getInstance(facet.module.project).addSelectionChangeListener(this::invalidate)
    }

    return resDirectories
  }

  private fun readFromFacetState(facet: AndroidFacet): List<VirtualFile> {
    val state = facet.configuration.state ?: return emptyList()
    val path = state.RES_FOLDERS_RELATIVE_PATH
    return if (path != null) {
      val manager = VirtualFileManager.getInstance()
      Splitter.on(SEPARATOR).omitEmptyStrings().trimResults().split(path).mapNotNull(manager::findFileByUrl)
    }
    else {
      // First time; have not yet computed the res folders, just try the default: src/main/res/ from Gradle templates, res/ from exported
      // Eclipse projects.
      listOf(
        AndroidRootUtil.getFileByRelativeModulePath(facet.module, "/$FD_SOURCES/$FD_MAIN/$FD_RES", true)
        ?: AndroidRootUtil.getFileByRelativeModulePath(facet.module, "/$FD_RES", true)
        ?: return emptyList())
    }
  }

  private fun notifyChanged(before: List<VirtualFile>, after: List<VirtualFile>) {
    generation++
    val added = HashSet<VirtualFile>(after.size)
    added.addAll(after)
    added.removeAll(before)

    val removed = HashSet<VirtualFile>(before.size)
    removed.addAll(before)
    removed.removeAll(after)

    for (listener in listeners) {
      listener.resourceFoldersChanged(facet, after, added, removed)
    }
  }

  companion object {
    private val KEY = Key.create<ResourceFolderManager>(ResourceFolderManager::class.java.name)

    /**
     * Separator used when encoding the list of res folders in the facet's state. Deliberately using ';' instead of [File.pathSeparator]
     * since on Unix [File.pathSeparator] is ":" which is also used in URLs, meaning we could end up with something like
     * `file://foo:file://bar`
     */
    private const val SEPARATOR = ";"

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
