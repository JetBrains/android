/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.synchronisation

import com.android.resources.ResourceFolderType
import com.android.tools.idea.res.ModuleResourceRepository
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.*
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidResourceUtil
import java.security.MessageDigest

/**
 * Manager to keep track of the differences between the facet's modules' resources and
 * provided [VirtualFile]
 */
class SynchronizationManager(facet: AndroidFacet, parentDisposable: Disposable)
  : VirtualFileListener,
    Disposable {

  private var resourcesRepository = ModuleResourceRepository.getOrCreateInstance(facet)
  private var hashToFile: MutableMap<String, VirtualFile> = mapAllModuleResources(resourcesRepository.resourceDirs)
  private val listeners: MutableList<SynchronizationListener> = mutableListOf()

  init {
    VirtualFileManager.getInstance().addVirtualFileListener(this)
    Disposer.register(parentDisposable, this)
  }

  /**
   * Map the hash of the resources to the virtual file
   */
  private fun mapAllModuleResources(resourcesDirectories: MutableSet<VirtualFile>)
      : MutableMap<String, VirtualFile> {
    return AndroidResourceUtil
        .getResourceSubdirs(ResourceFolderType.DRAWABLE, resourcesDirectories)
        .flatMap { it.allChildren() }
        .associate { it.sha1() to it }.toMutableMap()
  }

  /**
   * Check if the files in [assetSet] are synchronized with the resources of the [AndroidFacet]
   */
  fun getSynchronizationStatus(assetSet: DesignAssetSet): SynchronizationStatus {
    val syncedFilesCount = assetSet.designAssets.count { hashToFile.containsKey(it.file.sha1()) }
    return when (syncedFilesCount) {
      assetSet.designAssets.size -> SynchronizationStatus.SYNCED
      0 -> SynchronizationStatus.INEXISTENT
      else -> SynchronizationStatus.PARTIALLY_SYNCED
    }
  }

  /**
   * Check if the file is in any of the resources directories of the facet
   */
  private fun isModuleResourceFile(virtualFile: VirtualFile) =
      !virtualFile.isDirectory && resourcesRepository.resourceDirs.any { VfsUtilCore.isAncestor(it, virtualFile, false) }

  private fun notifyResourceCreated(file: VirtualFile) {
    listeners.forEach { it.resourceAdded(file) }
  }

  private fun notifyResourceRemoved(file: VirtualFile) {
    listeners.forEach { it.resourceRemoved(file) }
  }

  override fun fileCreated(event: VirtualFileEvent) {
    if (isModuleResourceFile(event.file)) {
      hashToFile.put(event.file.sha1(), event.file)
      notifyResourceCreated(event.file)
    }
  }

  override fun fileDeleted(event: VirtualFileEvent) {
    if (isModuleResourceFile(event.file)) {
      hashToFile.entries
          .firstOrNull { it.value == event.file }
          ?.key
          ?.let(hashToFile::remove)
      notifyResourceRemoved(event.file)
    }
  }

  fun addListener(listener: SynchronizationListener) {
    listeners.add(listener)
  }

  fun removeListener(listener: SynchronizationListener) {
    listeners.remove(listener)
  }

  override fun dispose() {
    VirtualFileManager.getInstance().addVirtualFileListener(this)
  }
}

/**
 * Enum that represent the synchronisation status of a [DesignAssetSet]
 */
enum class SynchronizationStatus {

  /**
   * None of the files of the [DesignAssetSet] is present in the facet's module
   */
  INEXISTENT,

  /**
   * All the files of the [DesignAssetSet] are present in the facet's module
   */
  SYNCED,

  /**
   * Some of the files of the [DesignAssetSet] are present in the facet's module
   */
  PARTIALLY_SYNCED
}

/**
 * Listener to notify when a resources has been added or removed from the module associated
 * with the SynchronizationManager
 */
interface SynchronizationListener {
  fun resourceAdded(file: VirtualFile)
  fun resourceRemoved(file: VirtualFile)
}

private fun VirtualFile.allChildren(): List<VirtualFile> =
    if (!isDirectory) listOf(this) else children.flatMap { it.allChildren() }

private fun VirtualFile.sha1(): String {
  return String(MessageDigest.getInstance("SHA1")
      .digest(this.contentsToByteArray()))
}