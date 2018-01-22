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

import com.google.common.util.concurrent.JdkFutureAdapters
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import org.apache.batik.dom.svg.SAXSVGDocumentFactory
import org.apache.batik.transcoder.TranscoderException
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.ImageTranscoder
import org.apache.batik.util.XMLResourceDescriptor
import org.xml.sax.SAXParseException
import java.awt.Dimension
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Callable

/**
 * [DesignAssetRenderer] to display SVGs
 */
class SVGAssetRenderer : DesignAssetRenderer {
  override fun isFileSupported(file: VirtualFile): Boolean = "svg".equals(file.extension, true)

  override fun getImage(file: VirtualFile, module: Module?, dimension: Dimension): ListenableFuture<out Image?> {
    return JdkFutureAdapters.listenInPoolThread(
      ApplicationManager.getApplication().executeOnPooledThread(
        Callable {
          try {
            SVGLoader(file.inputStream, dimension.height, dimension.width).createImage()
          } catch (saxParserException: SAXParseException) {
            logFileNotSupported(file, saxParserException)
            null
          } catch (saxIOException: IOException) {
            logFileNotSupported(file, saxIOException)
            null
          }
        })
    )
  }

  private fun logFileNotSupported(file: VirtualFile, ex: Exception) {
    Logger.getInstance(SVGAssetRenderer::class.java).warn(
      "${file.path} content is not supported by the SVG Loader\n ${ex.localizedMessage}"
    )
  }

  private class SVGLoader(
    inputStream: InputStream,
    private val width: Int,
    private val height: Int
  ) {

    private var img: BufferedImage? = null
    private val transcoderInput = TranscoderInput(
      SAXSVGDocumentFactory(
        XMLResourceDescriptor.getXMLParserClassName()
      ).createDocument(null, inputStream)
    )

    private inner class MyTranscoder : ImageTranscoder() {
      override fun createImage(w: Int, h: Int): BufferedImage {
        return UIUtil.createImage(w, h, BufferedImage.TYPE_INT_ARGB)
      }

      @Throws(TranscoderException::class)
      override fun writeImage(img: BufferedImage, output: TranscoderOutput?) {
        this@SVGLoader.img = img
      }
    }

    @Throws(TranscoderException::class)
    internal fun createImage(): BufferedImage? {
      val transcoder = MyTranscoder()
      transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, width.toFloat())
      transcoder.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, height.toFloat())
      transcoder.transcode(transcoderInput, null)
      return img
    }
  }
}