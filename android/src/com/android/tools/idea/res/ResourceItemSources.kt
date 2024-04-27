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

import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceFolderType
import com.android.resources.base.BasicResourceItem
import com.android.resources.base.RepositoryConfiguration
import com.android.resources.base.ResourceSourceFile
import com.android.utils.Base128OutputStream
import com.google.common.collect.ArrayListMultimap
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import it.unimi.dsi.fastutil.objects.Object2IntMap
import java.io.IOException

/**
 * Represents a resource file from which [ResourceItem]s are created by [ResourceFolderRepository].
 *
 * This is a common interface implemented by [PsiResourceFile] and [VfsResourceFile].
 */
internal interface ResourceItemSource<T : ResourceItem> : Iterable<T> {
  val virtualFile: VirtualFile?
  val configuration: RepositoryConfiguration
  val folderType: ResourceFolderType?

  val repository: ResourceFolderRepository
    get() = configuration.repository as ResourceFolderRepository

  val folderConfiguration: FolderConfiguration
    get() = configuration.folderConfiguration

  fun addItem(item: T)
}

/** The [ResourceItemSource] of [PsiResourceItem]s. */
internal class PsiResourceFile(
  private var _psiFile: PsiFile,
  items: Iterable<PsiResourceItem>,
  private var _resourceFolderType: ResourceFolderType?,
  override var configuration: RepositoryConfiguration
) : ResourceItemSource<PsiResourceItem> {

  private val _items = ArrayListMultimap.create<String, PsiResourceItem>()

  init {
    items.forEach(this::addItem)
  }

  override val folderType
    get() = _resourceFolderType

  override val virtualFile: VirtualFile?
    get() = _psiFile.virtualFile

  override fun iterator(): Iterator<PsiResourceItem> = _items.values().iterator()

  fun isSourceOf(item: ResourceItem): Boolean = (item as? PsiResourceItem)?.sourceFile == this

  override fun addItem(item: PsiResourceItem) {
    // Setting the source first is important, since an item's key gets the folder configuration from
    // the source (i.e. this).
    item.sourceFile = this
    _items.put(item.key, item)
  }

  fun removeItem(item: PsiResourceItem) {
    _items.remove(item.key, item)
    item.sourceFile = null
  }

  val name = _psiFile.name
  val psiFile
    get() = _psiFile

  fun setPsiFile(psiFile: PsiFile, configuration: RepositoryConfiguration) {
    this._psiFile = psiFile
    this._resourceFolderType = getFolderType(psiFile)
    this.configuration = configuration
  }
}

/** The [ResourceItemSource] of [BasicResourceItem]s. */
internal class VfsResourceFile(
  override val virtualFile: VirtualFile?,
  override val configuration: RepositoryConfiguration
) : ResourceSourceFile, ResourceItemSource<BasicResourceItem> {

  private val items = ArrayList<BasicResourceItem>()

  override val folderType
    get() = getFolderType(virtualFile)

  override val repository: ResourceFolderRepository
    get() = configuration.repository as ResourceFolderRepository

  override fun iterator(): Iterator<BasicResourceItem> = items.iterator()

  override fun addItem(item: BasicResourceItem) {
    items.add(item)
  }

  override val relativePath: String?
    get() = virtualFile?.let { VfsUtilCore.getRelativePath(it, repository.resourceDir) }

  fun isValid(): Boolean = virtualFile != null

  /** Serializes the object to the given stream without the contained resource items. */
  @Throws(IOException::class)
  override fun serialize(stream: Base128OutputStream, configIndexes: Object2IntMap<String>) {
    stream.writeString(relativePath)
    stream.writeInt(configIndexes.getInt(configuration.folderConfiguration.qualifierString))
    stream.write(FileTimeStampLengthHasher.hash(virtualFile))
  }
}
