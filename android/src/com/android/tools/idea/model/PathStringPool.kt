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
@file:JvmName("PathStrings")
package com.android.tools.idea.model

import com.android.annotations.concurrency.GuardedBy
import com.android.projectmodel.PathString
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Utility class which can toFile [PathString] instances to read-only [File] instances. Any paths that can't
 * be converted directly to [File] objects (for example, if they exist in an in-memory filesystem or inside a .jar)
 * are converted by copying them to a temp folder.
 *
 * This utility class is intended for invoking legacy APIs that accept [File] objects and aren't
 * capable of dealing with other kinds of [PathString] directly. It is preferable to the conversion methods
 * directly on [PathString] since this will return valid file contents is situations where the conversion
 * methods would have returned null.
 */
class PathStringPool : Closeable {
  @GuardedBy("files")
  private val files = HashMap<PathString, File>()

  /**
   * Returns a [VirtualFile] instance containing the same content as the file at the given [PathString].
   */
  fun toVirtualFile(path: PathString): VirtualFile? {
    path.toVirtualFile()?.let { return it }
    return LocalFileSystem.getInstance().findFileByIoFile(toFile(path))
  }

  /**
   * Returns a [Path] containing the same content as the file at the given [PathString].
   */
  fun toPath(path: PathString): Path {
    path.toPath()?.let { return it }
    return toFile(path).toPath()
  }

  /**
   * Returns a [File] containing the same content as the file at the given [PathString].
   */
  fun toFile(path: PathString): File {
    path.toFile()?.let { return it }
    synchronized(files) {
      files[path]?.let { return it }
    }

    val fileName = path.fileName.portablePath.let { if (it.length >= 3) it else "PathStringPool" + it }
    val tempFile = File.createTempFile(fileName, ".tmp")
    path.newInputStream().use { stream ->
      Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    synchronized(files) {
      files.put(path, tempFile)
    }

    return tempFile
  }

  override fun close() {
    val toDelete = ArrayList<File>()
    synchronized(files) {
      toDelete.addAll(files.values)
      files.clear()
    }
    toDelete.forEach {
      it.delete()
    }
  }
}

/**
 * Returns a [VirtualFile] for this [PathString] or null if the path can't be converted directly.
 */
fun PathString.toVirtualFile(): VirtualFile? {
  return VirtualFileManager.getInstance().getFileSystem(this.filesystemUri.scheme)?.findFileByPath(portablePath)
}

/**
 * Returns a new input stream for reading this file. Throws an [IOException] if unable to open the file
 * or the file does not exist.
 */
fun PathString.newInputStream(): InputStream {
  toVirtualFile()?.let {
    return it.inputStream
  }

  toPath()?.let {
    return Files.newInputStream(it, StandardOpenOption.READ)
  }

  throw FileNotFoundException(toString())
}

/**
 * Returns a [PathString] that describes the given [VirtualFile].
 */
fun VirtualFile.toPathString(): PathString {
  if (fileSystem.protocol == LocalFileSystem.PROTOCOL) {
    return PathString(path)
  }

  val fileSystemUri = URI(fileSystem.protocol, "/", "")
  return PathString(fileSystemUri, path)
}

/**
 * Searches for the file specified by given [PathString].
 */
fun findFileByPath(pathToFind: PathString, refreshNeeded: Boolean): VirtualFile? {
  val virtualFile = pathToFind.toVirtualFile() ?: return null

  return if (refreshNeeded) {
    virtualFile.fileSystem.refreshAndFindFileByPath(virtualFile.path)
  }
  else virtualFile
}
