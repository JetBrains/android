/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.material.icons.utils

import com.android.tools.idea.material.icons.metadata.MaterialIconsMetadata
import com.android.tools.idea.sdk.AndroidSdks
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.util.Locale

/**
 * Set of common functions and values used when reading/writing Material Icons files.
 */
object MaterialIconsUtils {
  private val LOG = Logger.getInstance(javaClass)

  /**
   * The path where the bundled material icons are stored.
   */
  const val MATERIAL_ICONS_PATH = "images/material/icons/"

  /**
   * Name of the metadata filed used.
   */
  const val METADATA_FILE_NAME = "icons_metadata.txt"

  /**
   * Path from classpath to the bundled style in the Material Icons directory.
   *
   * Does not end on trailing slash.
   */
  fun getBundledStyleDirectoryPath(styleName: String): String = MATERIAL_ICONS_PATH + styleName.toDirFormat()

  /**
   * Path from classpath to the bundled icon in the Material Icons directory.
   */
  fun getBundledIconPath(styleName: String, iconName: String, iconFileName: String) =
    getBundledStyleDirectoryPath(styleName) + "/" + iconName + "/" + iconFileName

  /**
   * Transform the verbose Material Icon family name into a format used for File directories.
   *
   * Eg: 'Material Icons Outlined' -> 'materialiconsoutlined'
   */
  fun String.toDirFormat(): String = this.lowercase(Locale.US).replace(" ", "")

  /**
   * The Android/Sdk path where Material Icons are expected to be stored.
   *
   * Eg: '.../Android/Sdk/icons/material'
   */
  fun getIconsSdkTargetPath(): File? {
    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    val sdkHome = sdkHandler.location ?: return null
    val materialDir = sdkHome.resolve("icons/material")
    return Files.createDirectories(materialDir).toFile()
  }

  /**
   * Returns `true` if there's a file with the name [METADATA_FILE_NAME] in the .../Android/Sdk directory.
   *
   * Does not check if it's a valid file for [MaterialIconsMetadata].
   */
  fun hasMetadataFileInSdkPath(): Boolean {
    val iconsSdkPath = getIconsSdkTargetPath()
    return iconsSdkPath != null && iconsSdkPath.resolve(METADATA_FILE_NAME).exists()
  }

  /**
   * @see [MaterialIconsMetadata.writeAsJson]
   * @return The [MaterialIconsMetadata] parsed from the URL provided.
   */
  fun getMetadata(url: URL): MaterialIconsMetadata {
    return MaterialIconsMetadata.parse(url, LOG)
  }

  /**
   * Returns the expected file name for material icons given the [iconName] and the [styleName].
   *
   * E.g. For 'android' of 'Material Icons Rounded' returns 'rounded_android_24'
   *
   * This should be used whenever we want to read or write material icons, since we rely on this consistency.
   */
  fun getIconFileNameWithoutExtension(iconName: String, styleName: String): String {
    val family = styleName.toDirFormat().substringAfter("materialicons")
    val familyPrefix = when (family) {
      "" -> "baseline"
      "outlined" -> "outline"
      else -> family
    }
    return "${familyPrefix}_${iconName}_24"
  }
}