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
package com.android.tools.idea.util

import com.android.ide.common.util.FileSystemRegistry
import com.android.ide.common.util.PathOpener
import com.android.ide.common.util.PathString
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileSystem
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Implementation of [PathOpener] that understands IntelliJ's [VirtualFileSystem] paths.
 * [VirtualFile] paths.
 */
object VirtualFileSystemOpener : PathOpener {
  private val mutex = Object()
  private var registered = false

  /**
   * Mounts the IntelliJ virtual filesystems. This is an inexpensive no-op if they are already mounted.
   */
  public fun mount() {
    synchronized (mutex) {
      if (!registered) {
        registered = true
        FileSystemRegistry.mount(this)
      }
    }
  }

  /**
   * Unmounts the IntelliJ virtual filesystems.
   */
  public fun unmount() {
    synchronized (mutex) {
      if (registered) {
        registered = false
        FileSystemRegistry.unmount(this)
      }
    }
  }

  /**
   * Look up and return the [VirtualFileSystem] for the given path. Uses a 1-element cache of the previous
   * lookups.
   */
  private fun fileSystemFor(path: PathString): VirtualFileSystem?
    = VirtualFileManager.getInstance().getFileSystem(path.filesystemUri.scheme)

  override fun recognizes(path: PathString): Boolean
    = fileSystemFor(path) != null

  override fun isRegularFile(path: PathString): Boolean
    = toVirtualFile(path)?.let { it.exists() && !it.isDirectory } ?: false

  override fun open(path: PathString): InputStream
    = toVirtualFile(path)?.inputStream ?: throw FileNotFoundException(path.toString())

  override fun isDirectory(path: PathString): Boolean
    = toVirtualFile(path)?.let { it.exists() && it.isDirectory } ?: false
}

internal fun toVirtualFile(path: PathString, refresh: Boolean = false): VirtualFile? {
  val filesystem = VirtualFileManager.getInstance().getFileSystem(path.filesystemUri.scheme) ?: return null
  return if (refresh) filesystem.refreshAndFindFileByPath(path.portablePath) else filesystem.findFileByPath(path.portablePath)
}
