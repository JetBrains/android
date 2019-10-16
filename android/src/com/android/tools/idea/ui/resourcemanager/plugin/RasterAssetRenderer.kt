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
package com.android.tools.idea.ui.resourcemanager.plugin

import com.android.tools.adtui.ImageUtils
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO

/**
 * [DesignAssetRenderer] to display raster format images.
 */
class RasterAssetRenderer : DesignAssetRenderer {
  override fun isFileSupported(file: VirtualFile) = ImageIO.getReaderFormatNames().contains(file.extension)

  override fun getImage(file: VirtualFile, module: Module?, dimension: Dimension): CompletableFuture<out BufferedImage?> =
    CompletableFuture.supplyAsync {
      try {
        ImageUtils.readImageAtScale(file.inputStream, dimension)
      }
      catch (e: NullPointerException) {
        // b/115303829
        null
      }
    }
}