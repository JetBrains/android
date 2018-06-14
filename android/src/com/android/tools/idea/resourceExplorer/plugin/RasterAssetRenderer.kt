/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.plugin

import com.android.tools.adtui.ImageUtils
import com.google.common.util.concurrent.JdkFutureAdapters
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Dimension
import java.awt.Image
import java.awt.image.BufferedImage
import java.net.URL
import java.util.concurrent.Callable
import javax.imageio.ImageIO

/**
 * [DesignAssetRenderer] to display raster format images.
 */
class RasterAssetRenderer : DesignAssetRenderer {
  override fun isFileSupported(file: VirtualFile) = ImageIO.getReaderFormatNames().contains(file.extension)

  override fun getImage(file: VirtualFile, module: Module?, dimension: Dimension): ListenableFuture<out Image?> {
    return JdkFutureAdapters.listenInPoolThread(
      ApplicationManager.getApplication().executeOnPooledThread(
        Callable<Image?> {
          val image = ImageIO.read(file.inputStream)
          val width = image.getWidth(null).toDouble()
          val height = image.getHeight(null).toDouble()
          val scale = if (width < height) dimension.width / width else dimension.height / height
          val scaledImage = ImageUtils.scale(image, scale, scale, null)
          if ((width * scale + 0.5).toInt() != dimension.width
              || (height * scale + 0.5).toInt() != dimension.height) {

            val sx1 = (((width * scale - dimension.width) / 2.0) + 0.5).toInt()
            val sy1 = (((height * scale - dimension.height) / 2.0) + 0.5).toInt()
            val sx2 = sx1 + dimension.width
            val sy2 = sy1 + dimension.height
            val croppedImage = BufferedImage(dimension.width, dimension.height, BufferedImage.TYPE_INT_ARGB)
            val g = croppedImage.createGraphics()
            g.drawImage(scaledImage,
                        0, 0, dimension.width, dimension.height,
                        sx1, sy1, sx2, sy2,
                        null)
            g.dispose()
            croppedImage
          }
          else scaledImage
        }))
  }
}