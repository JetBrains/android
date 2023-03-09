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
package com.android.tools.idea.ui.resourcemanager.plugin

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.svg.renderSvgWithSize
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.IOException
import java.util.concurrent.CompletableFuture

/**
 * [DesignAssetRenderer] to display SVGs
 */
class SVGAssetRenderer : DesignAssetRenderer {
  override fun isFileSupported(file: VirtualFile): Boolean = "svg".equals(file.extension, true)

  override fun getImage(file: VirtualFile,
                        module: Module?,
                        dimension: Dimension,
                        context: Any?): CompletableFuture<BufferedImage?> {
    return CompletableFuture.supplyAsync {
      try {
        file.inputStream.use { inputStream ->
          renderSvgWithSize(inputStream = inputStream, width = dimension.width.toFloat(), height = dimension.height.toFloat())
        }
      }
      catch (e: IOException) {
        logFileNotSupported(file, e)
        null
      }
    }
  }

  private fun logFileNotSupported(file: VirtualFile, ex: Exception) {
    Logger.getInstance(SVGAssetRenderer::class.java).warn(
      "${file.path} content is not supported by the SVG Loader\n ${ex.localizedMessage}"
    )
  }
}