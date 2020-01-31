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
package com.android.tools.idea.ui.resourcemanager.model

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.Density
import com.android.tools.idea.ui.resourcemanager.model.DesignAssetMetadata.DENSITY
import com.android.tools.idea.ui.resourcemanager.model.DesignAssetMetadata.DIMENSIONS_DP
import com.android.tools.idea.ui.resourcemanager.model.DesignAssetMetadata.DIMENSIONS_PX
import com.android.tools.idea.ui.resourcemanager.model.DesignAssetMetadata.FILE_NAME
import com.android.tools.idea.ui.resourcemanager.model.DesignAssetMetadata.FILE_SIZE
import com.android.tools.idea.ui.resourcemanager.model.DesignAssetMetadata.FILE_TYPE
import com.android.tools.idea.util.toPathString
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import javax.imageio.ImageIO

enum class DesignAssetMetadata(val metadataName: String) {
  FILE_NAME("File name"),

  /**
   * The type of the
   */
  FILE_TYPE("File type"),

  /**
   * A human-readable representation of the file size.
   */
  FILE_SIZE("File size"),

  /**
   * The dimension in pixel of the file (only if it is an image).
   */
  DIMENSIONS_PX("Dimensions (px)"),

  /**
   * The dimensions of the image in dip. This value can only be found if the
   * [DENSITY] can be found.
   */
  DIMENSIONS_DP("Dimensions (dp)"),

  /**
   * The density of the file. This will be inferred from the parent folder of the [VirtualFile].
   */
  DENSITY("Density")
}

/**
 * Returns a map of [DesignAssetMetadata] to their human readable value represented as [String].
 *
 * This can be used to easily get metadata about a [VirtualFile] to display them to the user.
 *
 * The map will contain the desired [dataKeys] if they can be fetched from the [VirtualFile].
 */
fun VirtualFile.getMetadata(vararg dataKeys: DesignAssetMetadata = DesignAssetMetadata.values()): Map<DesignAssetMetadata, String> {

  val keys = dataKeys.toMutableSet()
  val map = mutableMapOf<DesignAssetMetadata, String>()
  if (keys.remove(FILE_NAME)) {
    map[FILE_NAME] = name
  }

  if (keys.remove(FILE_TYPE)) {
    val extension = extension
    map[FILE_TYPE] = if (extension.equals("xml", true)) "Vector drawable" else extension?.toUpperCase(Locale.US) ?: "Unknown"
  }

  val parentFileName = toPathString().parentFileName

  val density = if (parentFileName != null) {
    FolderConfiguration.getConfigForFolder(parentFileName)?.densityQualifier?.value
  }
  else {
    Density.MEDIUM
  }
  if (keys.remove(DENSITY)) {
    if (density != null) {
      map[DENSITY] = density.longDisplayValue
    }
  }

  if (keys.remove(FILE_SIZE)) {
    map[FILE_SIZE] = StringUtil.formatFileSize(length)
  }

  val reader = ImageIO.getImageReadersBySuffix(extension)
    .asSequence()
    .firstOrNull()
  if (reader != null) {
    reader.input = ImageIO.createImageInputStream(this.inputStream)
    val width = reader.getWidth(0)
    val height = reader.getHeight(0)
    if (keys.remove(DIMENSIONS_PX)) {
      map[DIMENSIONS_PX] = "${width}x$height"
    }

    if (keys.remove(DIMENSIONS_DP) && density != null && density.isValidValueForDevice) {
      val dpiValue = density.dpiValue.toDouble()
      val dpWidth = Math.round(width / (dpiValue / Density.DEFAULT_DENSITY))
      val dpHeight = Math.round(height / (dpiValue / Density.DEFAULT_DENSITY))
      map[DIMENSIONS_DP] = "${dpWidth}x${dpHeight}"
    }

  }
  return map
}