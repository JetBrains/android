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

import com.android.SdkConstants
import com.android.tools.idea.layoutlib.RenderingException
import com.android.tools.idea.npw.assetstudio.DrawableRenderer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Dimension
import java.awt.Image
import java.text.ParseException
import javax.xml.parsers.DocumentBuilderFactory

private val LOG = Logger.getInstance(DrawableAssetRenderer::class.java)

private val SUPPORTED_DRAWABLE_TAG = arrayOf(
  SdkConstants.TAG_VECTOR, SdkConstants.TAG_SHAPE, SdkConstants.TAG_BITMAP,
  SdkConstants.TAG_RIPPLE, SdkConstants.TAG_SELECTOR, SdkConstants.TAG_ANIMATED_SELECTOR)

/**
 * [DesignAssetRenderer] to display Vector Drawable.
 */
class DrawableAssetRenderer : DesignAssetRenderer {

  private var drawableRenderer: DrawableRenderer? = null
  private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

  override fun isFileSupported(file: VirtualFile): Boolean {
    if (file.fileType != XmlFileType.INSTANCE || file.length == 0L) {
      return false
    }

    return try {
      val document = documentBuilder.parse(file.inputStream)
      document.documentElement.nodeName in SUPPORTED_DRAWABLE_TAG
    }
    catch (ex: Exception) {
      LOG.warn(ex)
      return false
    }
  }

  override fun getImage(
    file: VirtualFile,
    module: Module?,
    dimension: Dimension
  ): ListenableFuture<out Image?> {
    try {
      if (module == null) {
        throw NullPointerException("Module cannot be null to render a Drawable.")
      }

      if (!isFileSupported(file)) {
        throw ParseException("${file.path} couldn't be parsed as a drawable.", 0)
      }

      ensureDrawableRendererInitialized(module)

      val xmlContent = String(file.contentsToByteArray())
      return drawableRenderer?.renderDrawable(xmlContent, dimension)
             ?: throw RenderingException("${file.path} couldn't be rendered.")
    }
    catch (ex: Exception) {
      return failedFuture(ex)
    }
  }

  private fun ensureDrawableRendererInitialized(module: Module) {
    if (drawableRenderer?.let { Disposer.isDisposed(it) } != false) {
      val facet = AndroidFacet.getInstance(module) ?: throw NullPointerException("Facet couldn't be found for use in DrawableRenderer.")
      val renderer = DrawableRenderer(facet)
      Disposer.register(facet, renderer)
      drawableRenderer = renderer
    }
  }

  private fun failedFuture(exception: Throwable): ListenableFuture<out Image?> {
    LOG.warn(exception)
    return Futures.immediateFailedFuture(exception)
  }
}
