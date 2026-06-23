/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.avd

import com.android.sdklib.internal.avd.AvdManager.Companion.ENVIRONMENTS_DIR
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.util.StudioPathManager.isRunningFromSources
import com.android.tools.idea.util.StudioPathManager.resolvePathFromSourcesRoot
import com.android.utils.FileUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

@Suppress("LightServiceMigrationCode")
class EnvironmentsUpdater : Disposable {

  private val environments: Deferred<List<EnvironmentImage>>
  private val sourceDir: Path
  private val destinationDir: Path

  @Volatile
  var environmentsList: List<EnvironmentImage>? = null
    private set

  init {
    sourceDir =
      when {
        isRunningFromSources() -> resolvePathFromSourcesRoot("tools/adt/idea/artwork/resources/device-art-resources/ai_glasses_device")
        else -> Path.of(PathManager.getHomePath()).resolve("plugins/android/resources/device-art-resources/ai_glasses_device")
      }

    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    destinationDir = sdkHandler.location?.resolve(ENVIRONMENTS_DIR) ?: throw RuntimeException("Unable to get SDK location")

    environments =
      createCoroutineScope().async(Dispatchers.IO) {
        try {
          updateDirectory(sourceDir, destinationDir)
        } catch (e: Exception) {
          thisLogger().warn("Error updating environment images in $destinationDir", e)
        }
        try {
          val list = EnvironmentImageScanner.scanEnvironments(destinationDir)
          environmentsList = list
          list
        } catch (e: Exception) {
          thisLogger().warn("Error loading environment images from $destinationDir", e)
          emptyList()
        }
      }
  }

  suspend fun getEnvironments(): List<EnvironmentImage> {
    return environments.await()
  }

  override fun dispose() {}

  companion object {
    fun getInstance(): EnvironmentsUpdater = service<EnvironmentsUpdater>()
  }
}

/**
 * Copies files from [sourceDir] and its subdirectories to [destinationDir]. Only files that don't exist in [destinationDir] or are older
 * than the corresponding files in [sourceDir] are copied.
 */
fun updateDirectory(sourceDir: Path, destinationDir: Path) {
  require(!destinationDir.startsWith(sourceDir)) { "Copying into itself is not allowed" }
  if (!Files.exists(sourceDir)) {
    return
  }

  Files.walk(sourceDir).use { stream ->
    stream.filter { Files.isRegularFile(it) }.forEach { source -> updateFile(sourceDir, destinationDir, sourceDir.relativize(source)) }
  }
}

/**
 * Copies a file from [sourceDir] to [destinationDir] if the destination doesn't exist or is older than the source. The file is defined by
 * its path relative to [sourceDir]. Returns absolute path of the destination file.
 */
private fun updateFile(sourceDir: Path, destinationDir: Path, relativePath: Path): Path {
  val source = sourceDir.resolve(relativePath)
  val destination = destinationDir.resolve(relativePath.toString())
  val sourceTimestamp = Files.getLastModifiedTime(source)
  if (!Files.exists(destination) || Files.getLastModifiedTime(destination) < sourceTimestamp) {
    Files.createDirectories(destination.parent!!)
    FileUtils.copyFile(source, destination)
    if (System.getProperty("os.name").lowercase().contains("windows")) { // FileUtils.copyFile doesn't preserve timestamp on Windows.
      Files.setLastModifiedTime(destination, sourceTimestamp)
    }
  }
  return destination
}
