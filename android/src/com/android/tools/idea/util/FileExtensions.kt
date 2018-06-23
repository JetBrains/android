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
@file:JvmName("FileExtensions")
package com.android.tools.idea.util

import com.android.ide.common.util.PathString
import com.intellij.openapi.vfs.*
import com.intellij.util.io.inputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

fun VirtualFile.toIoFile(): File = VfsUtil.virtualToIoFile(this)

fun File.toVirtualFile(refresh: Boolean = false): VirtualFile? = VfsUtil.findFileByIoFile(this, refresh)

@JvmOverloads
fun File.toLibraryRootVirtualFile(refresh: Boolean = false): VirtualFile? {
  val url = VfsUtil.getUrlForLibraryRoot(this)
  val virtualFileManager = VirtualFileManager.getInstance()
  return if (refresh) virtualFileManager.refreshAndFindFileByUrl(url) else virtualFileManager.findFileByUrl(url)
}

/**
 * Returns a new input stream for reading this file. Throws an [IOException] if unable to open the file
 * or the file does not exist.
 */
fun PathString.inputStream(): InputStream {
  return toVirtualFile()?.inputStream ?: toPath()?.inputStream() ?: throw FileNotFoundException(toString())
}

/**
 * Returns the [VirtualFile] representing the file resource given its path, or null
 * if there is no VirtualFile for the resource.
 */
@JvmOverloads
fun PathString.toVirtualFile(refresh: Boolean = false): VirtualFile? {
  val filesystem = VirtualFileManager.getInstance().getFileSystem(filesystemUri.scheme) ?: return null
  return if (refresh) filesystem.refreshAndFindFileByPath(portablePath) else filesystem.findFileByPath(portablePath)
}

/**
 * Returns a [PathString] that describes the given [VirtualFile].
 */
fun VirtualFile.toPathString(): PathString {
  return PathString(fileSystem.protocol, path)
}
