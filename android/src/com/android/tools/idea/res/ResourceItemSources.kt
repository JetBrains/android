/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.ide.common.resources.ResourceFile
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceMerger
import com.android.ide.common.resources.ResourceMergerItem
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceFolderType
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

/**
 * Represents a resource file from which [ResourceItem]s are crated by [ResourceFolderRepository].
 *
 * This is a common abstraction for [PsiResourceFile] (used by [PsiResourceItem]) and [ResourceFile] (used by [ResourceMergerItem]), needed
 * by [ResourceFolderRepository] which needs to deal with both types of [ResourceItem]s as it transitions from DOM parsing to PSI parsing
 * of resource files.
 */
sealed class ResourceItemSource<T : ResourceItem> : Iterable<T> {
  abstract val folderConfiguration: FolderConfiguration
  abstract val folderType: ResourceFolderType?
  abstract val virtualFile: VirtualFile?
  abstract fun addItem(item: T)
  abstract fun removeItem(item: T)
  abstract fun isSourceOf(item: ResourceItem): Boolean
}

/** The [ResourceItemSource] of [PsiResourceItem]s. */
class PsiResourceFile constructor(
  private var _psiFile: PsiFile,
  items: Iterable<PsiResourceItem>,
  private var _resourceFolderType: ResourceFolderType?,
  private var _folderConfiguration: FolderConfiguration
) : ResourceItemSource<PsiResourceItem>() {

  private val _items: ListMultimap<String, PsiResourceItem> = ArrayListMultimap.create<String, PsiResourceItem>()

  init {
    items.forEach(this::addItem)
  }

  override val folderType get() = _resourceFolderType
  override val folderConfiguration get() = _folderConfiguration
  override val virtualFile: VirtualFile? get() = _psiFile.virtualFile
  override fun iterator(): Iterator<PsiResourceItem> = _items.values().iterator()
  override fun isSourceOf(item: ResourceItem): Boolean = (item as? PsiResourceItem)?.source == this

  override fun addItem(item: PsiResourceItem) {
    // Setting the source first is important, since an item's key gets the folder configuration from the source (i.e. this).
    item.source = this
    _items.put(item.key, item)
  }

  override fun removeItem(item: PsiResourceItem) {
    item.source = null
    _items.remove(item.key, item)
  }

  var dataBindingInfo: LayoutDataBindingInfo? = null
  val name = _psiFile.name
  val psiFile get() = _psiFile

  fun setPsiFile(psiFile: PsiFile, folderConfiguration: FolderConfiguration) {
    this._psiFile = psiFile
    this._folderConfiguration = folderConfiguration
    this._resourceFolderType = getFolderType(psiFile)
  }
}

/**
 * The [ResourceItemSource] of [ResourceMergerItem]s.
 *
 * Implements the IDE-specific interface of [ResourceItemSource] by wrapping a [ResourceFile] from the non-IDE [ResourceMerger] subsystem.
 */
internal class ResourceFileAdapter(
  val resourceFile: ResourceFile
) : ResourceItemSource<ResourceMergerItem>() {
  override val folderConfiguration get() = resourceFile.folderConfiguration
  override val folderType get() = getFolderType(resourceFile)
  override val virtualFile get() = VfsUtil.findFileByIoFile(resourceFile.file, false)
  override fun iterator(): Iterator<ResourceMergerItem> = resourceFile.items.iterator()
  override fun addItem(item: ResourceMergerItem) = resourceFile.addItem(item)
  override fun removeItem(item: ResourceMergerItem) = resourceFile.removeItem(item)
  override fun isSourceOf(item: ResourceItem): Boolean = (item as? ResourceMergerItem)?.source == resourceFile
}
