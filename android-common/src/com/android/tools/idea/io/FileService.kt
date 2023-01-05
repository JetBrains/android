/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.io

import com.intellij.openapi.application.PathManager
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

/**
 * A service that provides common file operations for temporary files or directories.
 */
abstract class FileService {
  /**
   * Location of where cached files should live - that is, these files are expected to stick around
   * for "a while" but could be cleaned up at some point in the future (so their continued
   * existence shouldn't be assumed).
   */
  abstract val cacheRoot: Path

  /**
   * Location of where temp files should live - that is, these files will stick around until the
   * user exits the application.
   */
  abstract val tmpRoot: Path

  /**
   * Given a [path] that represents a directory, make sure it is created.
   */
  protected abstract fun createDir(path: Path, deleteOnExit: Boolean)

  /**
   * Get a directory (ensuring it is created if it does not already exist) underneath the cache root.
   */
  fun getOrCreateCacheDir(name: String): Path = cacheRoot.resolve(name).also { createDir(it, false) }

  /**
   * Get a directory (ensuring it is created if it does not already exist) underneath the tmp root.
   */
  fun getOrCreateTempDir(name: String): Path = tmpRoot.resolve(name).also { createDir(it, true) }
}

/**
 * An implementation of [FileService] which is backed by the file system and uses IDE defaults.
 *
 * A subclass is still expected to provide overrides for the concrete root directories.
 */
abstract class DiskFileService : FileService() {
  final override fun createDir(path: Path, deleteOnExit: Boolean) {
    if (!path.exists()) {
      val file = path.toFile()
      file.mkdirs()
      if (deleteOnExit) {
        file.deleteOnExit()
      }
    }
  }
}

/**
 * A service that provides common file operations with locations standardized across the IDE.
 *
 * @param subdir An (optional) additional subdirectory to create all cache / temp files under to
 *   reduce the chance of filename collisions between different areas.
 */
class IdeFileService(subdir: String = "") : DiskFileService() {
  override val cacheRoot: Path = Paths.get(PathManager.getSystemPath()).resolve(subdir)
  override val tmpRoot: Path = Paths.get(PathManager.getTempPath()).resolve(subdir)
}