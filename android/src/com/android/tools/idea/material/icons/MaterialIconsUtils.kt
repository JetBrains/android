/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.material.icons

import com.android.tools.idea.sdk.AndroidSdks
import java.io.File
import java.util.Locale

/**
 * Set of common functions and values used when reading/writing Material Icons files.
 */
internal object MaterialIconsUtils {
  /**
   * The path where the bundled material icons are stored.
   */
  const val MATERIAL_ICONS_PATH = "images/material/icons/"

  /**
   * Name of the metadata filed used.
   */
  const val METADATA_FILE_NAME = "icons_metadata.txt"

  /**
   * Transform the verbose Material Icon family name into a format used for File directories.
   *
   * Eg: 'Material Icons Outlined' -> 'materialiconsoutlined'
   */
  fun String.toDirFormat(): String = this.toLowerCase(Locale.US).replace(" ", "")

  /**
   * The Android/Sdk path where Material Icons are expected to be stored.
   *
   * Eg: '.../Android/Sdk/icons/material'
   */
  fun getIconsSdkTargetPath(): File? {
    val sdkHome = AndroidSdks.getInstance().tryToChooseSdkHandler().location ?: return null
    val iconsFolder = File(sdkHome, "icons")
    return File(iconsFolder, "material").apply { mkdirs() }
  }
}