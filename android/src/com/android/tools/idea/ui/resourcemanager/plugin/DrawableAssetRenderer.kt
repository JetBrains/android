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

import com.android.SdkConstants
import com.android.tools.idea.npw.assetstudio.DrawableRenderer
import com.android.tools.idea.util.TimedDisposable
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.xml.sax.SAXParseException
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.lang.ref.WeakReference
import java.text.ParseException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

private val LOG = Logger.getInstance(DrawableAssetRenderer::class.java)

private val SUPPORTED_DRAWABLE_TAG = arrayOf(
  SdkConstants.TAG_ADAPTIVE_ICON,
  SdkConstants.TAG_ANIMATED_SELECTOR,
  SdkConstants.TAG_ANIMATED_VECTOR,
  SdkConstants.TAG_BITMAP,
  SdkConstants.TAG_INSET,
  SdkConstants.TAG_LAYER_LIST,
  SdkConstants.TAG_RIPPLE,
  SdkConstants.TAG_SELECTOR,
  SdkConstants.TAG_SHAPE,
  SdkConstants.TAG_TRANSITION,
  SdkConstants.TAG_VECTOR
)

private const val disposeTime: Long = 5
private val disposeTimeUnit: TimeUnit = TimeUnit.MINUTES

/**
 * [DesignAssetRenderer] to display Vector Drawable.
 */
class DrawableAssetRenderer : DesignAssetRenderer {

  private var drawableRenderer: TimedDisposable<DrawableRenderer>? = null

  // Only use a single weak reference because only the resources of one module are rendered at a
  // time. We can update to an hash map when this changes.
  private var currentFacet: WeakReference<AndroidFacet>? = null

  private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

  private fun getRenderer(module: Module): DrawableRenderer {
    val facet = AndroidFacet.getInstance(module)
                ?: throw NullPointerException("Facet couldn't be found for use in DrawableRenderer.")

    // We cannot reuse the same renderer with another facet
    // otherwise some drawable might not render.
    if (facet != currentFacet?.get()) {
      currentFacet = null
      drawableRenderer?.dispose()
      drawableRenderer = null
    }

    val renderer = drawableRenderer?.get()
    return if (renderer == null) {
      val newRenderer = DrawableRenderer(facet)
      drawableRenderer = TimedDisposable(newRenderer, facet, disposeTime, disposeTimeUnit)
      currentFacet = WeakReference(facet)
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
    catch (ex: SAXParseException) {
      LOG.debug("${ex::class.simpleName} in ${file.path}", ex)
      return false
    }
    catch (ex: Exception) {
      LOG.warn("${ex::class.simpleName} in ${file.path}", ex)
      return false
    }
  }

  override fun getImage(
    file: VirtualFile,
    module: Module?,
    dimension: Dimension
  ): CompletableFuture<out BufferedImage?> {
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

  private fun failedFuture(exception: Throwable): CompletableFuture<out BufferedImage?> {
    LOG.warn(exception)
    val failedFuture = CompletableFuture<BufferedImage?>()
    failedFuture.completeExceptionally(exception)
    return failedFuture
  }
}
