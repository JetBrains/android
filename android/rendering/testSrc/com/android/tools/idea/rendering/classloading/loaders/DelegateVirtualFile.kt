/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.rendering.classloading.loaders

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import java.io.InputStream
import java.io.OutputStream

internal open class DelegateVirtualFile(private val delegate: VirtualFile): VirtualFile() {
  override fun getName(): String = delegate.name
  override fun getFileSystem(): VirtualFileSystem = delegate.fileSystem
  override fun getPath(): String = delegate.path
  override fun isWritable(): Boolean = delegate.isWritable
  override fun isDirectory(): Boolean = delegate.isDirectory
  override fun isValid(): Boolean = delegate.isValid
  override fun getParent(): VirtualFile = delegate.parent
  override fun getChildren(): Array<VirtualFile> = delegate.children
  override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream =
    delegate.getOutputStream(requestor, newModificationStamp, newTimeStamp)
  override fun contentsToByteArray(): ByteArray = delegate.contentsToByteArray()
  override fun getTimeStamp(): Long = delegate.timeStamp
  override fun getLength(): Long = delegate.length
  override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) =
    delegate.refresh(asynchronous, recursive, postRunnable)
  override fun getInputStream(): InputStream = delegate.inputStream
}