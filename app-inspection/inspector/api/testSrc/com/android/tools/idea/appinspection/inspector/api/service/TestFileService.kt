package com.android.tools.idea.appinspection.inspector.api.service

import com.android.tools.idea.io.DiskFileService
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

/**
 * A [FileService] backed by cache and tmp directories guaranteed unique for this service instance.
 * Additionally, these files will be cleaned up when the test runner exits.
 */
class TestFileService(cacheRoot: String, tmpRoot: String) : DiskFileService() {
  constructor() : this(FileUtil.getTempDirectory() + "/cache", FileUtil.getTempDirectory() + "/tmp")

  private val uniqueDir = UUID.randomUUID().toString()

  override val cacheRoot: Path =
    Paths.get(cacheRoot).resolve(uniqueDir).also { createAndInitDir(it) }
  override val tmpRoot: Path = Paths.get(tmpRoot).resolve(uniqueDir).also { createAndInitDir(it) }

  private fun createAndInitDir(path: Path) {
    val f = path.toFile()
    f.mkdirs()
    f.deleteOnExit()
  }
}
