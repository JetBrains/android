package com.android.tools.idea.appinspection.inspector.api.service

import com.intellij.util.io.exists
import java.nio.file.Path

/**
 * A service that provides common file operations.
 */
// TODO(b/170769654): Move this class to a more widely accessible location?
abstract class FileService {
  /**
   * Location of where cached files should live - that is, these files are expected to stick around
   * for "a while" but could be cleaned up at some point in the future (so their continued
   * existence shouldn't be assumed).
   */
  protected abstract val cacheRoot: Path

  /**
   * Location of where temp files should live - that is, these files will stick around until the
   * user exits the application.
   */
  protected abstract val tmpRoot: Path

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
