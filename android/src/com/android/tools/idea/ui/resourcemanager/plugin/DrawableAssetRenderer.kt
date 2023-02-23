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
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.rendering.DrawableRenderer
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.text.ParseException
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.android.facet.AndroidFacet
import org.xml.sax.SAXParseException

private val LOG = Logger.getInstance(DrawableAssetRenderer::class.java)

private val SUPPORTED_DRAWABLE_TAG =
  arrayOf(
    SdkConstants.TAG_ADAPTIVE_ICON,
    SdkConstants.TAG_ANIMATED_SELECTOR,
    SdkConstants.TAG_ANIMATED_VECTOR,
    SdkConstants.TAG_BITMAP,
    SdkConstants.TAG_INSET,
    SdkConstants.TAG_LAYER_LIST,
    SdkConstants.TAG_NINE_PATCH,
    SdkConstants.TAG_RIPPLE,
    SdkConstants.TAG_ROTATE,
    SdkConstants.TAG_SELECTOR,
    SdkConstants.TAG_SHAPE,
    SdkConstants.TAG_TRANSITION,
    SdkConstants.TAG_VECTOR
  )

/** [DesignAssetRenderer] to display Vector Drawable. */
class DrawableAssetRenderer : DesignAssetRenderer {

  private val documentBuilder = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder()

  private fun createRenderer(
    module: Module,
    contextFile: VirtualFile?
  ): CompletableFuture<DrawableRenderer> {
    val facet =
      AndroidFacet.getInstance(module)
        ?: return CompletableFuture<DrawableRenderer>().also {
          it.completeExceptionally(NullPointerException("Facet for module $module couldn't be found for use in DrawableRenderer."))
        }

    return CompletableFuture.supplyAsync(
      Supplier {
        return@Supplier if (contextFile == null) {
          DrawableRenderer(facet)
        } else {
          val configuration =
            ConfigurationManager.getOrCreateInstance(facet.module).getConfiguration(contextFile)
          DrawableRenderer(facet, configuration)
        }
      },
      AppExecutorUtil.getAppExecutorService()
    )
  }

  override fun isFileSupported(file: VirtualFile): Boolean {
    if (
      !FileTypeRegistry.getInstance().isFileOfType(file, XmlFileType.INSTANCE) || file.length == 0L
    ) {
      return false
    }

    return try {
      val document = documentBuilder.parse(file.inputStream)
      document.documentElement.nodeName in SUPPORTED_DRAWABLE_TAG
    } catch (ex: SAXParseException) {
      LOG.debug("${ex::class.simpleName} in ${file.path}", ex)
      return false
    } catch (ex: Exception) {
      LOG.warn("${ex::class.simpleName} in ${file.path}", ex)
      return false
    }
  }

  override fun getImage(
    file: VirtualFile,
    module: Module?,
    dimension: Dimension,
    context: Any?
  ): CompletableFuture<out BufferedImage?> {
    try {
      if (module == null) {
        return CompletableFuture<BufferedImage?>().also {
          it.completeExceptionally(
            NullPointerException("Module cannot be null to render a Drawable.")
          )
        }
      }

      if (!isFileSupported(file)) {
        return CompletableFuture<BufferedImage?>().also {
          it.completeExceptionally(
            ParseException("${file.path} couldn't be parsed as a drawable.", 0)
          )
        }
      }

      val renderer = createRenderer(module, context as? VirtualFile)

      val xmlContent = String(file.contentsToByteArray())

      return renderer
        .thenCompose { drawableRenderer -> drawableRenderer.renderDrawable(xmlContent, dimension) }
        .whenComplete { _, _ ->
          // Dispose of the renderer after the rendering is completed
          Disposer.dispose(renderer.get())
        }
    } catch (ex: Exception) {
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
