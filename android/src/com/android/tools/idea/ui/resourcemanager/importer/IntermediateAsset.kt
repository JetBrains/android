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
package com.android.tools.idea.ui.resourcemanager.importer

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import java.io.File
import java.io.InputStream

/**
 * Representation of a [VirtualFile] where the data lives in an existing [source] file
 * but its path appears at a location that might not yet exist.
 *
 * This is used to represent an intermediate state before copying a file from a [source] location
 * to a [target] location and can be used to show the target location of the file to the user while still
 * being able to access the data of the [source] file without the need to actually copy the file.
 */
class IntermediateAssetFile(
  val source: VirtualFile,
  val target: File
) : VirtualFile() {
  override fun getName(): String = target.name

  override fun getFileSystem(): VirtualFileSystem = LocalFileSystem.getInstance()

  /**
   * Returns the parent if it already exist at the target location, null otherwise.
   */
  override fun getParent(): VirtualFile? {
    if (target.parentFile.exists()) {
      return VfsUtil.findFileByIoFile(target.parentFile, true)
    }
    else {
      return null
    }
  }

  /**
   * The [InputStream] of the sourceFile
   */
  override fun getInputStream(): InputStream = source.inputStream

  /**
   * The content of the [source] file
   */
  override fun contentsToByteArray(): ByteArray = source.contentsToByteArray()

  /**
   * The path of the [target] file.
   */
  override fun getPath(): String = target.path

  override fun isWritable(): Boolean = false
  override fun isDirectory(): Boolean = false
  override fun isValid(): Boolean = true
  override fun getChildren(): Array<VirtualFile> = emptyArray()
  override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) = throw NotImplementedError()
  override fun getTimeStamp(): Long = 0
  override fun getLength(): Long = source.length
  override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {}
}

/**
 * Utility class meant to be use temporarily to map a source file to the desired name and folder after import.
 * It is used to do the transition from [DesignAssetSet] to resource file in the res/ directory because
 * [com.android.tools.idea.ui.resourcemanager.model.DesignAssetSet] are grouped by name while Android resources are grouped by qualifiers.
 */
data class IntermediateAsset(
  /**
   * The file that will be copied. If the source file has been converted, this represents
   * the converted file (which can live in memory only, in which case the content of the file will be
   * copied into a new file).
   */
  val sourceFile: VirtualFile,

  /**
   * The absolute path of the base resource directory where the file will be copied.
   */
  var targetResDirPath: String,

  /**
   * The relative path from [targetResDirPath] where [sourceFile] will be copied
   */
  var targetFolderName: String,

  /**
   * The name of the resource (without the extension)
   */
  val name: String
) {
  /**
   * The relative path of the file once it will be copied. This is the concatenation
   * of [targetFolderName] and [targetFileName]
   */
  val targetRelativePath get() = targetFolderName + File.separatorChar + targetFileName

  /**
   * The name of the file once copied in the project. This is the concatenation of the resource
   * [name] and the extension of the [sourceFile].
   */
  val targetFileName get() = "$name.${sourceFile.extension}"

  /**
   * A representation of the [IntermediateAsset] as a [IntermediateAssetFile].
   */
  val intermediateFile: IntermediateAssetFile = IntermediateAssetFile(
    sourceFile, File(targetRelativePath))
}