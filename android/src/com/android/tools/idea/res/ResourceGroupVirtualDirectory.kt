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
package com.android.tools.idea.res

import com.android.resources.ResourceFolderType
import com.google.common.base.Objects
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem
import com.intellij.psi.PsiFile
import com.intellij.util.ArrayUtil
import java.util.Arrays

/**
 * Implementation of a dummy [VirtualFile] that represents a set of different versions
 * of a same resource.
 *
 * This is used to be able to run refactoring action on all the resources at once, for example, when user
 * click rename on an Android resource group node in the project tree view or in the resource explorer.
 */
class ResourceGroupVirtualDirectory(
  val resourceName: String,
  val resourceFiles: Array<VirtualFile>
) : VirtualFile() {

  constructor(resourceName: String, resourceFiles: List<PsiFile>) :
    this(resourceName, resourceFiles.map(PsiFile::getVirtualFile).toTypedArray())

  init {
    require(resourceFiles.isNotEmpty())
  }

  override fun getName() = resourceName

  override fun getFileSystem(): DummyFileSystem = ResourceGroupDummyFS

  override fun getPath(): String {
    val parentFolder = resourceFiles[0].parent.name
    val folderName = ResourceFolderType.getFolderType(parentFolder)?.getName() ?: parentFolder
    return "$folderName/$resourceName"
  }

  override fun isWritable() = true

  override fun isDirectory() = true

  override fun isValid() = true

  override fun getParent(): VirtualFile = resourceFiles[0].parent

  override fun getChildren() = resourceFiles

  override fun getOutputStream(requestor: Any, newModificationStamp: Long, newTimeStamp: Long): Nothing {
    throw UnsupportedOperationException("Can't call getOutputStream on ${ResourceGroupVirtualDirectory::class.simpleName}")
  }

  override fun contentsToByteArray(): ByteArray = ArrayUtil.EMPTY_BYTE_ARRAY

  override fun getTimeStamp(): Long = 0

  override fun getLength(): Long = 0

  override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {}

  override fun getInputStream(): Nothing {
    throw UnsupportedOperationException("Can't call getInputStream on ${ResourceGroupVirtualDirectory::class.simpleName}")
  }

  override fun hashCode(): Int {
    return Arrays.hashCode(resourceFiles)
  }

  override fun equals(other: Any?): Boolean {
    if (other !is ResourceGroupVirtualDirectory) return false
    return Arrays.equals(resourceFiles, other.resourceFiles)
  }
}

object ResourceGroupDummyFS : DummyFileSystem() {
  override fun isReadOnly() = false

  override fun renameFile(requestor: Any?, vFile: VirtualFile, newName: String) {
    if (vFile is ResourceGroupVirtualDirectory) {
      vFile.children.forEach {
        it.rename(requestor, newNameWithSameExtension(newName, it))
      }
    }
  }

  override fun copyFile(requestor: Any?, vFile: VirtualFile, newParent: VirtualFile, copyName: String): VirtualFile {
    if (vFile is ResourceGroupVirtualDirectory) {
      val newFiles = vFile.children.map {
        val newSubDir = newParent.findChild(it.parent.name) ?: newParent.createChildDirectory(requestor, it.parent.name)
        it.copy(requestor, newSubDir, newNameWithSameExtension(copyName, it))
      }.toTypedArray()
      return ResourceGroupVirtualDirectory(copyName, newFiles)
    }
    return vFile.copy(requestor, newParent, copyName)
  }

  override fun createChildDirectory(requestor: Any?, vDir: VirtualFile, dirName: String): VirtualFile {
    throw UnsupportedOperationException()
  }

  override fun deleteFile(requestor: Any?, vFile: VirtualFile) {
    if (vFile is ResourceGroupVirtualDirectory) {
      vFile.children.forEach { it.delete(requestor) }
    }
  }
}

private fun newNameWithSameExtension(newName: String, vFile: VirtualFile) =
  FileUtil.getNameWithoutExtension(newName) + vFile.extension.orEmpty()

