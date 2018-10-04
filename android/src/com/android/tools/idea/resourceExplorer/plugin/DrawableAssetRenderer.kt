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
import com.android.tools.idea.npw.assetstudio.DrawableRenderer
import com.android.tools.idea.util.TimedDisposable
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Dimension
import java.awt.Image
import java.text.ParseException
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

private val LOG = Logger.getInstance(DrawableAssetRenderer::class.java)

private val SUPPORTED_DRAWABLE_TAG = arrayOf(
  SdkConstants.TAG_VECTOR, SdkConstants.TAG_SHAPE, SdkConstants.TAG_BITMAP,
  SdkConstants.TAG_RIPPLE, SdkConstants.TAG_SELECTOR, SdkConstants.TAG_ANIMATED_SELECTOR, SdkConstants.TAG_ANIMATED_VECTOR,
  SdkConstants.TAG_TRANSITION, SdkConstants.TAG_INSET, SdkConstants.TAG_LAYER_LIST)

private const val disposeTime: Long = 5
private val disposeTimeUnit: TimeUnit = TimeUnit.MINUTES

/**
 * [DesignAssetRenderer] to display Vector Drawable.
 */
class DrawableAssetRenderer : DesignAssetRenderer {

  private var drawableRenderer: TimedDisposable<DrawableRenderer>? = null

  private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

  private fun getRenderer(module: Module): DrawableRenderer {
    val renderer = drawableRenderer?.get()
    return if (renderer == null) {
      val facet = AndroidFacet.getInstance(module) ?: throw NullPointerException("Facet couldn't be found for use in DrawableRenderer.")
      val newRenderer = DrawableRenderer(facet)
      drawableRenderer = TimedDisposable(newRenderer, facet, disposeTime, disposeTimeUnit)
      newRenderer
    }
    else {
      renderer
    }
  }

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

      val renderer = getRenderer(module)

      val xmlContent = String(file.contentsToByteArray())
      return renderer.renderDrawable(xmlContent, dimension)
    }
    catch (ex: Exception) {
      return failedFuture(ex)
    }
  }

  private fun failedFuture(exception: Throwable): ListenableFuture<out Image?> {
    LOG.warn(exception)
    return Futures.immediateFailedFuture(exception)
  }
}
