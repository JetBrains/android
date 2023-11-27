/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.avdmanager

import com.android.annotations.concurrency.Slow
import com.android.tools.adtui.device.DeviceArtDescriptor
import com.android.tools.idea.sdk.AndroidSdks
import com.android.utils.FileUtils
import com.android.utils.PathUtils
import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

object DeviceSkinUpdater {

  /**
   * Usually returns the SDK skins path for the device (${HOME}/Android/Sdk/skins/pixel_4). This method also copies device skins from
   * Studio to the SDK if the SDK ones are out of date.
   *
   * Returns the SDK skins path for the device. Returns device as is if it's empty, absolute, equal to _no_skin, or both the Studio skins
   * path and SDK are not found. Returns the SDK skins path for the device if the Studio skins path is not found. Returns the Studio skins
   * path for the device (${HOME}/android-studio/plugins/android/resources/device-art-resources/pixel_4) if the SDK is not found or
   * an IOException is thrown.
   * @see DeviceSkinUpdaterService
   */
  @JvmStatic
  @JvmOverloads
  @Slow
  fun updateSkin(skin: Path, image: SystemImageDescription? = null): Path {
    if (skin.isAbsolute) {
      return skin
    }
    val skinName = skin.toString()
    if (skinName.isEmpty() || skinName == "_no_skin") {
      return skin
    }

    val imageSkins: Collection<Path> = image?.let { listOf(*image.skins) } ?: emptyList()

    val studioSkins = DeviceArtDescriptor.getBundledDescriptorsFolder()?.toPath()
    val sdk = AndroidSdks.getInstance().tryToChooseSdkHandler()

    return updateSkin(skin, imageSkins, studioSkins, sdk.location)
  }

  @VisibleForTesting
  fun updateSkin(skin: Path, imageSkins: Collection<Path>, studioSkins: Path?, sdkLocation: Path?): Path {
    for (imageSkin in imageSkins) {
      if (imageSkin.endsWith(skin)) {
        return imageSkin
      }
    }

    if (studioSkins == null && sdkLocation == null) {
      return skin
    }

    if (studioSkins == null) {
      return sdkLocation!!.resolve(skin)
    }

    if (sdkLocation == null) {
      return studioSkins.resolve(getStudioSkinName(skin.fileName.toString()))
    }

    return updateSkinImpl(skin, studioSkins, sdkLocation)
  }

  /**
   * Checks if all files under the [sourceDir] directory have their piers under
   * the [targetDir] directory with timestamps not older than the corresponding source file.
   */
  @VisibleForTesting
  fun areAllFilesUpToDate(targetDir: Path, sourceDir: Path): Boolean {
    class UpToDateChecker : SimpleFileVisitor<Path>() {
      var targetOlder: Boolean = false

      @Throws(IOException::class)
      override fun visitFile(sourceFile: Path, attrs: BasicFileAttributes): FileVisitResult {
        if (!sourceFile.fileName.toString().startsWith(".")) {
          val targetFile = targetDir.resolve(sourceDir.relativize(sourceFile).toString())
          // Convert to milliseconds to compensate for different timestamp precision on different file systems.
          if (getLastModifiedTimeMillis(targetFile) < getLastModifiedTimeMillis(sourceFile)) {
            targetOlder = true
            return FileVisitResult.TERMINATE
          }
        }
        return FileVisitResult.CONTINUE
      }

      private fun getLastModifiedTimeMillis(file: Path): Long {
        return Files.getLastModifiedTime(file).toMillis()
      }
    }

    val checker = UpToDateChecker()
    try {
      Files.walkFileTree(sourceDir, checker)
    }
    catch (e: IOException) {
      return false
    }

    return !checker.targetOlder
  }

  private fun updateSkinImpl(skin: Path, studioSkins: Path, sdkLocation: Path): Path {
    assert(skin.toString().isNotEmpty() && skin.toString() != "_no_skin")
    // For historical reasons relative skin paths are resolved relative to SDK itself, not its "skins" directory.
    val sdkDeviceSkin = sdkLocation.resolve(skin)
    val studioDeviceSkin = getStudioDeviceSkin(skin.fileName.toString(), studioSkins)

    try {
      if (areAllFilesUpToDate(sdkDeviceSkin, studioDeviceSkin)) {
        return sdkDeviceSkin
      }

      PathUtils.deleteRecursivelyIfExists(sdkDeviceSkin)
      FileUtils.copyDirectory(studioDeviceSkin, sdkDeviceSkin, false)
      return sdkDeviceSkin
    }
    catch (e: IOException) {
      thisLogger().warn(e)
      return studioDeviceSkin
    }
  }

  private fun getStudioDeviceSkin(skinName: String, studioSkins: Path): Path {
    return studioSkins.resolve(getStudioSkinName(skinName))
  }

  private fun getStudioSkinName(skinName: String): String {
    return when (skinName) {
      "WearLargeRound" -> "wearos_large_round"
      "WearSmallRound" -> "wearos_small_round"
      "WearSquare" -> "wearos_square"
      "WearRect" -> "wearos_rect"
      else -> skinName
    }
  }
}
