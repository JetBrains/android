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

import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.res.AndroidProjectRootListener
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic

/**
 * The resource folder manager is responsible for returning the current set of resource folders used in the project. It provides hooks for
 * getting notified when the set of folders changes (e.g. due to variant selection changes, or the folder set changing due to the user
 * editing the gradle files or after a delayed project initialization), and it also provides some state caching between IDE sessions such
 * that before the gradle initialization is done, it returns the folder set as it was before the IDE exited.
 */
class ResourceFolderManager(val module: Module) : ModificationTracker {

  companion object {
    private val FOLDERS_KEY = Key.create<List<VirtualFile>>(requireNotNull(ResourceFolderManager::class.qualifiedName))

    private val emptyFolders: List<VirtualFile> = emptyList()

    @JvmStatic
    fun getInstance(facet: AndroidFacet): ResourceFolderManager {
      return facet.module.getService(ResourceFolderManager::class.java)
    }

    @JvmField
    val TOPIC = Topic.create(ResourceFolderManager::class.qualifiedName!!, ResourceFolderListener::class.java,
                             Topic.BroadcastDirection.NONE)
  }

  /** Listeners for resource folder changes  */
  fun interface ResourceFolderListener {
    /** The resource folders in this project has changed  */
    fun foldersChanged(
      facet: AndroidFacet,
      folders: List<VirtualFile>
    )
  }

  @Volatile private var generation: Long = 0

  init {
    AndroidProjectRootListener.ensureSubscribed(module.project)
  }

  override fun getModificationCount() = generation

  /**
   * Returns main (production) resource directories, in increasing precedence order.
   *
   * @see com.android.tools.idea.projectsystem.SourceProviders.currentSourceProviders
   */
  val folders: List<VirtualFile>
    get() {
      val facet = module.androidFacet ?: return emptyFolders
      return facet.getUserData(FOLDERS_KEY) ?: facet.putUserDataIfAbsent(FOLDERS_KEY, computeFolders(facet))
    }

  /**
   * This returns the primary resource directory; the default location to place newly created resources etc.
   *
   * This method is marked deprecated since we should be gradually adding in UI to allow users to choose specific resource folders among
   * the available build variants (see [com.android.tools.idea.projectsystem.SourceProviders.currentSourceProviders] etc).
   *
   * @return the primary resource dir, if any.
   */
  @Suppress("DeprecatedCallableAddReplaceWith") // The method body is not the recommended replacement, see above.
  @Deprecated("Instead of calling this, ask the user which resource folder should be used.")
  val primaryFolder get() = folders.firstOrNull()

  /** Notifies the resource folder manager that the resource folder set may have changed.  */
  fun checkForChanges() {
    if (module.isDisposed) return
    val facet = module.androidFacet ?: return
    val before = facet.getUserData(FOLDERS_KEY) ?: return
    facet.putUserData(FOLDERS_KEY, null)
    val after = folders

    if (before != after) {
      generation++
      module.project.messageBus.syncPublisher(TOPIC).foldersChanged(facet, after)
    }
  }

  private fun computeFolders(facet: AndroidFacet): List<VirtualFile> {
    return if (!AndroidModel.isRequired(facet)) {
      SourceProviderManager.getInstance(facet).mainIdeaSourceProvider.resDirectories.toList()
    }
    else {
      readFromFacetState(facet)
    }
  }

  private fun readFromFacetState(facet: AndroidFacet): List<VirtualFile> {
    val sourceProviderManager = SourceProviderManager.getInstance(facet)
    return ResourceFolderManagerToken.computeFoldersFromSourceProviders(sourceProviderManager, module)
  }
}
