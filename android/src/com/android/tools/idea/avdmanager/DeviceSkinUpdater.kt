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
import kotlin.io.path.isDirectory

object DeviceSkinUpdater {

  /**
   * Copies device skin from Studio to the SDK if the SDK one is out of date. The [skin] parameter
   * could be the absolute path to the SDK skin, e.g. "${HOME}/Android/Sdk/skins/pixel_4", a path
   * relative to the SDK "skins" folder, e.g. "pixel_4", an empty or "_no_skin" path.
   *
   * Returns the SDK skin path. Returns the passed in [skin] path if it is absolute, empty, or equal
   * "_no_skin". Also returns [skin] if the Studio skins folder and the SDK location are both not
   * found. Returns the SDK skin path if the Studio skins path is not found. Returns the Studio
   * skin, e.g. "${HOME}/android-studio/plugins/android/resources/device-art-resources/pixel_4",
   * if the SDK is not found or there is an I/O error.
   * @see DeviceSkinUpdaterService
   */
  @JvmStatic
  @JvmOverloads
  @Slow
  fun updateSkin(skin: Path, image: SystemImageDescription? = null): Path {
    return updateSkin(skin, if (image == null) emptyList() else image.skins)
  }

  @Slow
  fun updateSkin(skin: Path, imageSkins: Collection<Path>): Path {
    if (skin.isAbsolute) {
      return skin
    }
    val skinName = skin.toString()
    if (skinName.isEmpty() || skinName == "_no_skin") {
      return skin
    }

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

    val sdkSkins = sdkLocation?.resolve("skins")
    if (studioSkins == null) {
      return sdkSkins?.resolve(skin) ?: skin
    }

    if (sdkSkins == null) {
      return studioSkins.resolve(getStudioSkinName(skin.fileName.toString()))
    }

    return updateSkinImpl(skin, studioSkins, sdkSkins)
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

  private fun updateSkinImpl(skin: Path, studioSkins: Path, skinFolder: Path): Path {
    assert(skin.toString().isNotEmpty() && skin.toString() != "_no_skin")
    val sdkDeviceSkin = skinFolder.resolve(skin)
    val studioDeviceSkin = getStudioDeviceSkin(skin.fileName.toString(), studioSkins) ?: return skin

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

  private fun getStudioDeviceSkin(skinName: String, studioSkins: Path): Path? {
    return studioSkins.resolve(getStudioSkinName(skinName)).takeIf { it.isDirectory() }
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
