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
import com.android.ide.common.util.inputStream
import com.intellij.openapi.vfs.*
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
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
 * or the file does not exist. The result will be a buffered stream.
 */
fun PathString.bufferedStream(): InputStream
  = inputStream().buffered()

/**
 * Converts this stream to a buffered stream, if there is a possibility that it isn't already buffered.
 * No additional buffering is added around streams that are known to already be buffered.
 */
fun InputStream.buffered(): InputStream
  = when (this) {
    is BufferedInputStream -> this
    is ByteArrayInputStream -> this
    else -> BufferedInputStream(this)
  }

/**
 * Returns the [VirtualFile] representing the file resource given its path, or null
 * if there is no VirtualFile for the resource.
 */
@JvmOverloads
fun PathString.toVirtualFile(refresh: Boolean = false): VirtualFile? {
  // Ensure that IntelliJ's virtual filesystems are mounted (ensures that PathString-to-VirtualFile lookups work from unit tests
  // or if performed very early during startup).
  VirtualFileSystemOpener.mount()
  return toVirtualFile(this, refresh)
}

/**
 * Returns a [PathString] that describes the given [VirtualFile].
 */
fun VirtualFile.toPathString(): PathString {
  // Ensure that IntelliJ's virtual filesystems are mounted (ensures that PathString-to-VirtualFile lookups work from unit tests
  // or if performed very early during startup).
  VirtualFileSystemOpener.mount()
  return PathString(fileSystem.protocol, path)
}
